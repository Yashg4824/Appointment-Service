package com.dealership.appointmentreminder.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dealership.appointmentreminder.config.ReminderProperties;
import com.dealership.appointmentreminder.dto.Notification;
import com.dealership.appointmentreminder.entity.Appointment;
import com.dealership.appointmentreminder.entity.AppointmentStatus;
import com.dealership.appointmentreminder.entity.Reminder;
import com.dealership.appointmentreminder.repository.AppointmentRepository;
import com.dealership.appointmentreminder.repository.ReminderRepository;
import com.dealership.appointmentreminder.service.NotificationSender;

/**
 * Scheduled reminder worker. Claiming, delivery, retries, and stale-work recovery live here so
 * the complete reminder lifecycle is visible in one component.
 */
@Component
public class ReminderWorker {

    private static final Logger log = LoggerFactory.getLogger(ReminderWorker.class);

    private final ReminderRepository reminderRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationSender notificationSender;
    private final ReminderProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReminderWorker(ReminderRepository reminderRepository,
                          AppointmentRepository appointmentRepository,
                          NotificationSender notificationSender,
                          ReminderProperties properties,
                          Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.reminderRepository = reminderRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationSender = notificationSender;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 * * * * *")
    public void runCycle() {
        try {
            reclaimStaleProcessing();
            for (Reminder reminder : claimDueReminders()) {
                processReminderSafely(reminder);
            }
        } catch (Exception e) {
            log.error("Reminder worker cycle failed; will retry on the next poll", e);
        }
    }

    public int reclaimStaleProcessing() {
        Integer reclaimed = transactionTemplate.execute(status ->
                reminderRepository.reclaimStaleProcessing(clock.instant()));
        int count = reclaimed == null ? 0 : reclaimed;
        if (count > 0) {
            log.warn("Reclaimed {} reminder(s) abandoned by a worker", count);
        }
        return count;
    }

    public List<Reminder> claimDueReminders() {
        List<Reminder> claimed = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            List<Reminder> rows = reminderRepository.findDueForUpdateSkipLocked(
                    now, now.minus(properties.getRetryDelay()), properties.getBatchSize());
            Instant processingUntil = now.plus(properties.getProcessingTimeout());
            rows.forEach(reminder -> reminder.markProcessing(processingUntil, now));
            return rows;
        });
        return claimed == null ? java.util.Collections.emptyList() : claimed;
    }

    public void process(Reminder reminder) {
        Instant now = clock.instant();
        Long reminderId = reminder.getId();
        if (reminder.getAttemptCount() >= properties.getMaxAttempts()) {
            recordOutcome(reminderId, reminderRepository.markFailed(reminderId, now), "FAILED");
            return;
        }

        Optional<Appointment> appointment = appointmentRepository.findById(reminder.getAppointmentId());
        if (!appointment.isPresent() || appointment.get().getStatus() != AppointmentStatus.SCHEDULED) {
            recordOutcome(reminderId, reminderRepository.markCancelled(reminderId, now), "CANCELLED");
            return;
        }

        Appointment appt = appointment.get();
        try {
            notificationSender.send(new Notification(reminderId, reminder.getIdempotencyKey(),
                    reminder.getReminderType(),
                    appt.getCustomerContact(), appt.getCustomerName(), appt.getScheduledAt()));
            recordOutcome(reminderId, reminderRepository.markSent(reminderId, now), "SENT");
        } catch (RuntimeException e) {
            int attemptsAfterThis = reminder.getAttemptCount() + 1;
            int updated = attemptsAfterThis >= properties.getMaxAttempts()
                    ? reminderRepository.markFailed(reminderId, now)
                    : reminderRepository.markForRetry(reminderId, now);
            recordOutcome(reminderId, updated,
                    attemptsAfterThis >= properties.getMaxAttempts() ? "FAILED" : "PENDING (retry)");
        }
    }

    private void processReminderSafely(Reminder reminder) {
        try {
            process(reminder);
        } catch (Exception e) {
            log.error("Unhandled error processing reminder {}; leaving it for timeout recovery",
                    reminder.getId(), e);
        }
    }

    private void recordOutcome(Long reminderId, int rowsUpdated, String intendedStatus) {
        if (rowsUpdated == 0) {
            log.warn("Reminder {} was no longer PROCESSING when recording {}", reminderId, intendedStatus);
        }
    }
}
