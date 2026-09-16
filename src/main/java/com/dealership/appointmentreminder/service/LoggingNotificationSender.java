package com.dealership.appointmentreminder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dealership.appointmentreminder.dto.Notification;

/**
 * The stub sender required by the assignment: it logs the payload instead of delivering it.
 *
 * <p><b>Why this class exists:</b> it makes the system runnable and demonstrable end to end
 * without an external provider, and it gives tests something real to observe.
 *
 * <p>The idempotency key is recorded in a small database table before logging. This makes the
 * local stub deduplicate across retries, instances, and restarts. A real provider must still
 * enforce the key at its own boundary: this stub has no external delivery side effect.
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);
    private final JdbcTemplate jdbcTemplate;

    public LoggingNotificationSender(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void send(Notification notification) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO notification_deliveries (idempotency_key, recorded_at) VALUES (?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (idempotency_key) DO NOTHING",
                notification.getIdempotencyKey());
        if (inserted == 0) {
            log.info("NOTIFICATION DUPLICATE SUPPRESSED | idempotencyKey={}",
                    notification.getIdempotencyKey());
            return;
        }
        log.info("NOTIFICATION SENT | reminderId={} | idempotencyKey={} | type={} | to={} | customer={} | appointmentAt={}",
                notification.getReminderId(),
                notification.getIdempotencyKey(),
                notification.getReminderType(),
                mask(notification.getRecipient()),
                notification.getCustomerName(),
                notification.getAppointmentScheduledAt());
    }

    /**
     * Masks the contact so customer email addresses and phone numbers do not sit in plain text
     * in log aggregation (architecture document, section 11, observability).
     */
    private String mask(String contact) {
        if (contact == null || contact.length() <= 4) {
            return "****";
        }
        int visible = Math.min(3, contact.length() - 4);
        return contact.substring(0, visible) + "****" + contact.substring(contact.length() - 2);
    }
}
