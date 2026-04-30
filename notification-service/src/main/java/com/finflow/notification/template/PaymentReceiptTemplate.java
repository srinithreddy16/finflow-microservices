package com.finflow.notification.template;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentReceiptTemplate {

    public NotificationRequest build(
            String email,
            String accountId,
            String transactionId,
            BigDecimal amount,
            String currency,
            String correlationId) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        String safeCurrency = currency != null ? currency : "USD";
        String subject = "Payment Confirmation - " + safeAmount + " " + safeCurrency;
        String messageBody =
                "Your payment was processed successfully.\n\n"
                        + "Amount: "
                        + safeAmount
                        + " "
                        + safeCurrency
                        + "\n"
                        + "Transaction ID: "
                        + transactionId
                        + "\n"
                        + "Timestamp: "
                        + Instant.now()
                        + "\n\n"
                        + "Thank you for using FinFlow.";

        return new NotificationRequest(
                accountId,
                email,
                null,
                NotificationChannel.EMAIL,
                subject,
                messageBody,
                "PAYMENT_RECEIPT",
                transactionId,
                correlationId);
    }
}
