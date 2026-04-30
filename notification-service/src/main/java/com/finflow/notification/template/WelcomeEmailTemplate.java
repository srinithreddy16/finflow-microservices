package com.finflow.notification.template;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WelcomeEmailTemplate {

    public NotificationRequest build(
            String email,
            String firstName,
            String lastName,
            String accountId,
            String sagaId,
            String correlationId) {
        String safeFirstName = firstName != null ? firstName : "Customer";
        String fullName =
                ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""))
                        .trim();
        if (fullName.isEmpty()) {
            fullName = safeFirstName;
        }

        String subject = "Welcome to FinFlow, " + safeFirstName + "!";
        String messageBody =
                "Hello "
                        + fullName
                        + ",\n\n"
                        + "Welcome to FinFlow. Your account is now active.\n"
                        + "Account ID: "
                        + accountId
                        + "\n"
                        + "Saga ID: "
                        + sagaId
                        + "\n\n"
                        + "We are excited to have you with us.\n"
                        + "FinFlow Team";

        return new NotificationRequest(
                accountId,
                email,
                null,
                NotificationChannel.EMAIL,
                subject,
                messageBody,
                "WELCOME",
                sagaId,
                correlationId);
    }
}
