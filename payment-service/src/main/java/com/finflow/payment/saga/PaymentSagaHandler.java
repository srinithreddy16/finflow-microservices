package com.finflow.payment.saga;

import com.finflow.payment.config.RabbitMQConfig;
import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.kafka.PaymentEventKafkaPublisher;
import com.finflow.payment.model.Payment;
import com.finflow.payment.service.PaymentService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Choreography Saga consumer for PaymentInitiated events.
 *
 * <p>This service is a PARTICIPANT in the saga — it reacts to events without being told by a
 * central coordinator.
 *
 * <p>Message acknowledgement strategy:
 *
 * <ul>
 *   <li>ACK: payment succeeded OR business failure (insufficient balance). Both are valid
 *       outcomes that do not need retry.
 *   <li>NACK (no requeue): technical/unexpected failures. Message goes to DLQ for manual inspection
 *       and potential retry.
 * </ul>
 *
 * <p>This prevents infinite retry loops on unrecoverable errors.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentSagaHandler {

    private static final String SYSTEM_RECEIVER_ACCOUNT_ID = "FINFLOW-SYSTEM-ACCOUNT";

    private final PaymentService paymentService;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentEventKafkaPublisher paymentEventKafkaPublisher;

    @RabbitListener(
            queues = RabbitMQConfig.PAYMENT_INITIATED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handlePaymentInitiated(
            @Payload PaymentInitiatedMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        log.info(
                "PaymentInitiated received for transaction: {}, amount: {} {}, account: {}",
                message.transactionId(),
                message.amount(),
                message.currency(),
                message.accountId());

        try {
            Payment payment =
                    paymentService.initiatePayment(
                            message.transactionId(),
                            message.accountId(),
                            SYSTEM_RECEIVER_ACCOUNT_ID,
                            message.amount(),
                            message.currency(),
                            message.correlationId(),
                            message.transactionId());

            paymentEventPublisher.publishCompleted(
                    message.transactionId(),
                    payment.getId(),
                    message.correlationId(),
                    payment.getAmount(),
                    payment.getCurrency());

            paymentEventKafkaPublisher.publishPaymentCompleted(payment, message);

            log.info(
                    "Payment completed for transaction: {}, paymentId: {}",
                    message.transactionId(),
                    payment.getId());

            ackSafely(channel, deliveryTag);
        } catch (InsufficientBalanceException e) {
            log.warn(
                    "Payment failed - insufficient balance for transaction: {}",
                    message.transactionId());
            paymentEventPublisher.publishFailed(
                    message.transactionId(),
                    "Insufficient balance",
                    "INSUFFICIENT_BALANCE",
                    message.correlationId());
            paymentEventKafkaPublisher.publishPaymentFailed(message, "Insufficient balance");
            ackSafely(channel, deliveryTag);
        } catch (Exception ex) {
            log.error(
                    "Unexpected error processing payment for transaction: {}",
                    message.transactionId(),
                    ex);
            nackSafely(channel, deliveryTag);
        }
    }

    private static void ackSafely(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("Failed to ACK message deliveryTag={}", deliveryTag, e);
        }
    }

    private static void nackSafely(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException e) {
            log.error("Failed to NACK message deliveryTag={}", deliveryTag, e);
        }
    }
}
