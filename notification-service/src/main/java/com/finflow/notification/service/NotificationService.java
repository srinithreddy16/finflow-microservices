package com.finflow.notification.service;

import com.finflow.notification.model.NotificationLog;
import com.finflow.notification.model.NotificationStatus;
import com.finflow.notification.repository.NotificationLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final NotificationLogRepository notificationLogRepository;

    @Transactional
    public void send(NotificationRequest request) {
        NotificationLog logEntry =
                NotificationLog.builder()
                        .recipientEmail(request.recipientEmail())
                        .recipientPhone(request.recipientPhone())
                        .accountId(request.accountId())
                        .channel(request.channel())
                        .subject(request.subject())
                        .messageBody(request.messageBody())
                        .status(NotificationStatus.PENDING)
                        .eventType(request.eventType())
                        .referenceId(request.referenceId())
                        .correlationId(request.correlationId())
                        .retryCount(0)
                        .build();

        notificationLogRepository.save(logEntry);

        try {
            boolean success =
                    switch (request.channel()) {
                        case EMAIL -> emailService.send(request);
                        case SMS -> smsService.send(request);
                        case PUSH -> pushService.send(request);
                    };

            if (success) {
                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setSentAt(Instant.now());
            } else {
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage("Channel returned false");
            }

            notificationLogRepository.save(logEntry);
            log.info(
                    "Notification {}: channel={}, eventType={}, account={}",
                    logEntry.getId(),
                    request.channel(),
                    request.eventType(),
                    request.accountId());
        } catch (Exception ex) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(ex.getMessage());
            notificationLogRepository.save(logEntry);
            log.error("Notification failed: {}", ex.getMessage(), ex);
        }
    }
}
