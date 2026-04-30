package com.finflow.notification.template;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentFailedTemplate {

    public NotificationRequest build(
            String email,
            String accountId,
            String transactionId,
            String reason,
            String correlationId) {
        String subject = "Payment Failed";
        String messageBody =
                "Your payment could not be completed.\n\n"
                        + "Transaction ID: "
                        + transactionId
                        + "\n"
                        + "Reason: "
                        + (reason != null ? reason : "Unknown error")
                        + "\n\n"
                        + "Please try again or contact support@finflow.com for help.";

        return new NotificationRequest(
                accountId,
                email,
                null,
                NotificationChannel.EMAIL,
                subject,
                messageBody,
                "PAYMENT_FAILED",
                transactionId,
                correlationId);
    }
}
