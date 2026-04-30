package com.finflow.notification.template;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FraudAlertTemplate {

    public NotificationRequest build(
            String email,
            String accountId,
            String transactionId,
            int fraudScore,
            String correlationId) {
        String subject = "Security Alert - Suspicious Activity Detected";
        String messageBody =
                "We detected potentially suspicious activity on your account.\n\n"
                        + "Transaction ID: "
                        + transactionId
                        + "\n"
                        + "Fraud Score: "
                        + fraudScore
                        + "\n\n"
                        + "If this was not you, contact support immediately at support@finflow.com.";

        return new NotificationRequest(
                accountId,
                email,
                null,
                NotificationChannel.EMAIL,
                subject,
                messageBody,
                "FRAUD_ALERT",
                transactionId,
                correlationId);
    }
}
