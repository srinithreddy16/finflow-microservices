package com.finflow.notification.service;

import com.finflow.notification.model.NotificationChannel;

public record NotificationRequest(
        String accountId,
        String recipientEmail,
        String recipientPhone,
        NotificationChannel channel,
        String subject,
        String messageBody,
        String eventType,
        String referenceId,
        String correlationId) {}
