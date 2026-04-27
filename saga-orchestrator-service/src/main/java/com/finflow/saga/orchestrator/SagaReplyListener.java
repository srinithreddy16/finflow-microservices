package com.finflow.saga.orchestrator;

import com.finflow.saga.config.RabbitMQConfig;
import com.finflow.saga.steps.SagaReply;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Consumes saga replies from the saga.replies.queue RabbitMQ queue.
 *
 * <p>Every saga participant (account-service, notification-service) publishes a SagaReply after
 * processing a SagaCommand. This listener feeds those replies into the orchestrator state machine.
 *
 * <p>Message acknowledgement:
 *
 * <p>- ACK: reply was processed (whether success or failure) - NACK: unexpected error processing
 * the reply (goes to DLQ)
 *
 * <p>The SagaReply structure: { sagaId, stepName, success, resultId, errorMessage, correlationId
 * }
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaReplyListener {

    private final OnboardingSagaOrchestrator sagaOrchestrator;

    @RabbitListener(
            queues = RabbitMQConfig.SAGA_REPLIES_QUEUE,
            containerFactory = "sagaListenerContainerFactory")
    public void handleSagaReply(
            SagaReply reply, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws Exception {
        log.info(
                "Saga reply received: sagaId={}, step={}, success={}",
                reply != null ? reply.sagaId() : null,
                reply != null ? reply.stepName() : null,
                reply != null && reply.success());

        if (reply == null || reply.sagaId() == null || reply.sagaId().isBlank()) {
            log.warn("Invalid saga reply received -- ignoring");
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (reply.success()) {
            try {
                sagaOrchestrator.handleStepSuccess(
                        reply.sagaId(), reply.stepName(), reply.resultId(), reply.correlationId());
                channel.basicAck(deliveryTag, false);
                log.debug("Saga reply ACKed: sagaId={}, step={}", reply.sagaId(), reply.stepName());
            } catch (Exception ex) {
                log.error("Error handling saga success reply: {}", ex.getMessage(), ex);
                channel.basicNack(deliveryTag, false, false);
            }
            return;
        }

        try {
            sagaOrchestrator.handleStepFailure(
                    reply.sagaId(),
                    reply.stepName(),
                    reply.errorMessage(),
                    reply.correlationId());
            channel.basicAck(deliveryTag, false);
            log.warn(
                    "Saga step failure handled: sagaId={}, step={}, error={}",
                    reply.sagaId(),
                    reply.stepName(),
                    reply.errorMessage());
        } catch (Exception ex) {
            log.error("Error handling saga failure reply: {}", ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
