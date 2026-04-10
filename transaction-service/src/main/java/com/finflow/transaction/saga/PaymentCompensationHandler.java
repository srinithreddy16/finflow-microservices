package com.finflow.transaction.saga;

import static com.finflow.transaction.config.RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE;
import static com.finflow.transaction.config.RabbitMQConfig.PAYMENT_FAILED_QUEUE;

import com.finflow.transaction.command.ReverseTransactionCommand;
import com.finflow.transaction.command.TransactionCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for PaymentFailed events and reverses the transaction (choreography compensation).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompensationHandler {

    /** System actor when reversing due to payment failure (not an end-user JWT). */
    private static final String COMPENSATION_ACTOR = "payment-saga-compensation";

    public record PaymentFailedMessage(String transactionId, String reason, String correlationId) {}

    private final TransactionCommandHandler transactionCommandHandler;

    @RabbitListener(queues = PAYMENT_FAILED_QUEUE)
    public void handlePaymentFailed(PaymentFailedMessage message) {
        log.debug(
                "Received payment failure from exchange {} for queue {}",
                PAYMENT_EVENTS_EXCHANGE,
                PAYMENT_FAILED_QUEUE);
        log.warn(
                "Payment failed for transaction: {}, reason: {}",
                message.transactionId(),
                message.reason());

        ReverseTransactionCommand reverseCommand =
                new ReverseTransactionCommand(
                        message.transactionId(),
                        message.reason() != null ? message.reason() : "Payment failed",
                        COMPENSATION_ACTOR,
                        message.correlationId());

        try {
            transactionCommandHandler.handle(reverseCommand);
        } catch (Exception e) {
            log.error(
                    "Compensation reverse failed for transaction: {}",
                    message.transactionId(),
                    e);
        }
    }
}
