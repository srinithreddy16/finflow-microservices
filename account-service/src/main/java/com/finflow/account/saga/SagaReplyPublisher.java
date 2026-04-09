package com.finflow.account.saga;

import com.finflow.account.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SagaReplyPublisher {

    private final RabbitTemplate rabbitTemplate;

    public record SagaReply(
            String sagaId,
            String stepName,
            boolean success,
            String resultId,
            String errorMessage,
            String correlationId) {}

    public void publishSuccess(
            String sagaId, String stepName, String resultId, String correlationId) {
        SagaReply reply = new SagaReply(sagaId, stepName, true, resultId, null, correlationId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE, "saga.replies", reply);
        log.info("Saga reply published: step={}, sagaId={}, success=true", stepName, sagaId);
    }

    public void publishFailure(
            String sagaId, String stepName, String errorMessage, String correlationId) {
        SagaReply reply = new SagaReply(sagaId, stepName, false, null, errorMessage, correlationId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE, "saga.replies", reply);
        log.warn(
                "Saga reply published: step={}, sagaId={}, success=false, error={}",
                stepName,
                sagaId,
                errorMessage);
    }
}
