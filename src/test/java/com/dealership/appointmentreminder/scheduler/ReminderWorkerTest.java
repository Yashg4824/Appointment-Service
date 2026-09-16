package com.dealership.appointmentreminder.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import com.dealership.appointmentreminder.config.ReminderProperties;
import com.dealership.appointmentreminder.entity.Reminder;
import com.dealership.appointmentreminder.repository.AppointmentRepository;
import com.dealership.appointmentreminder.repository.ReminderRepository;
import com.dealership.appointmentreminder.service.NotificationSender;

class ReminderWorkerTest {

    private final ReminderWorker worker = spy(new ReminderWorker(
            mock(ReminderRepository.class), mock(AppointmentRepository.class),
            mock(NotificationSender.class), new ReminderProperties(), Clock.systemUTC(),
            mock(PlatformTransactionManager.class)));

    @Test
    void shouldReclaimBeforeClaimingAndProcessing() {
        Reminder first = mock(Reminder.class);
        Reminder second = mock(Reminder.class);
        doReturn(Arrays.asList(first, second)).when(worker).claimDueReminders();

        worker.runCycle();

        InOrder order = inOrder(worker);
        order.verify(worker).reclaimStaleProcessing();
        order.verify(worker).claimDueReminders();
        order.verify(worker).process(first);
        order.verify(worker).process(second);
    }

    @Test
    void shouldContinueAfterOneReminderFails() {
        Reminder failing = mock(Reminder.class);
        Reminder healthy = mock(Reminder.class);
        doReturn(Arrays.asList(failing, healthy)).when(worker).claimDueReminders();
        doThrow(new RuntimeException("boom")).when(worker).process(failing);

        assertThatCode(worker::runCycle).doesNotThrowAnyException();
        verify(worker).process(healthy);
    }

    @Test
    void shouldContainDatabaseFailures() {
        doThrow(new DataAccessResourceFailureException("database is down"))
                .when(worker).claimDueReminders();

        assertThatCode(worker::runCycle).doesNotThrowAnyException();
    }
}
