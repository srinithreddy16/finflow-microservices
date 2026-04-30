package com.finflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.model.NotificationLog;
import com.finflow.notification.model.NotificationStatus;
import com.finflow.notification.repository.NotificationLogRepository;
import com.finflow.notification.service.EmailService;
import com.finflow.notification.service.NotificationRequest;
import com.finflow.notification.service.NotificationService;
import com.finflow.notification.service.PushService;
import com.finflow.notification.service.SmsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private PushService pushService;
    @Mock private NotificationLogRepository notificationLogRepository;

    @InjectMocks private NotificationService notificationService;

    @Test
    void send_LogsSuccess_WhenEmailSent() {
        when(emailService.send(any(NotificationRequest.class))).thenReturn(true);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "Subject",
                        "Message",
                        "PAYMENT_RECEIPT",
                        "tx-1",
                        "corr-1");

        notificationService.send(request);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        List<NotificationLog> saves = captor.getAllValues();
        assertThat(saves.get(1).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void send_LogsFailure_WhenEmailFails() {
        when(emailService.send(any(NotificationRequest.class))).thenReturn(false);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "Subject",
                        "Message",
                        "PAYMENT_FAILED",
                        "tx-2",
                        "corr-2");

        notificationService.send(request);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        List<NotificationLog> saves = captor.getAllValues();
        assertThat(saves.get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void send_DoesNotThrow_WhenServiceThrows() {
        doThrow(new RuntimeException("smtp error"))
                .when(emailService)
                .send(any(NotificationRequest.class));
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "Subject",
                        "Message",
                        "WELCOME",
                        "saga-1",
                        "corr-3");

        assertThatCode(() -> notificationService.send(request)).doesNotThrowAnyException();

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        List<NotificationLog> saves = captor.getAllValues();
        assertThat(saves.get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void send_RoutesSms_WhenChannelIsSms() {
        when(smsService.send(any(NotificationRequest.class))).thenReturn(true);
        when(notificationLogRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        null,
                        "+15550100",
                        NotificationChannel.SMS,
                        "Subject",
                        "Message",
                        "FRAUD_ALERT",
                        "tx-3",
                        "corr-4");

        notificationService.send(request);

        verify(smsService).send(request);
        verify(emailService, never()).send(any(NotificationRequest.class));
    }
}
