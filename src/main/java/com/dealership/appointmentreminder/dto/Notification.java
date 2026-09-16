package com.dealership.appointmentreminder.dto;

import java.time.Instant;
import java.util.Objects;

import com.dealership.appointmentreminder.entity.ReminderType;
import com.dealership.appointmentreminder.service.NotificationSender;

/**
 * An immutable value object describing one notification to be delivered.
 *
 * <p><b>Why this class exists:</b> it is the contract between {@code ReminderWorker} and any
 * {@link NotificationSender}. Passing a value object rather than a {@code Reminder} entity keeps
 * senders away from persistence concerns - a sender cannot accidentally mutate or lazily load
 * database state - and means a future SMS or email sender needs no knowledge of JPA.
 *
 * <p><b>Why it carries {@code idempotencyKey}:</b> this stable appointment/type key is supplied
 * to a provider (or the local durable sender record) to suppress a duplicate caused by a retry
 * after a crash.
 *
 * <p><b>Why it carries the absolute {@code appointmentScheduledAt}:</b> the message states the
 * actual appointment time rather than a relative phrase like "in 24 hours". That one choice is
 * what makes a reminder delivered slightly late still correct, and is therefore what makes a
 * polling worker safe (architecture document, assumption 3).
 */
public final class Notification {

    private final Long reminderId;
    private final String idempotencyKey;
    private final ReminderType reminderType;
    private final String recipient;
    private final String customerName;
    private final Instant appointmentScheduledAt;

    public Notification(Long reminderId,
                        ReminderType reminderType,
                        String recipient,
                        String customerName,
                        Instant appointmentScheduledAt) {
        this(reminderId, reminderId + "-" + reminderType.name(), reminderType, recipient,
                customerName, appointmentScheduledAt);
    }

    public Notification(Long reminderId,
                        String idempotencyKey,
                        ReminderType reminderType,
                        String recipient,
                        String customerName,
                        Instant appointmentScheduledAt) {
        this.reminderId = Objects.requireNonNull(reminderId, "reminderId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.reminderType = Objects.requireNonNull(reminderType, "reminderType");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.customerName = Objects.requireNonNull(customerName, "customerName");
        this.appointmentScheduledAt = Objects.requireNonNull(appointmentScheduledAt, "appointmentScheduledAt");
    }

    /** The stable idempotency key for this notification. */
    public Long getReminderId() {
        return reminderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public ReminderType getReminderType() {
        return reminderType;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Instant getAppointmentScheduledAt() {
        return appointmentScheduledAt;
    }
}
