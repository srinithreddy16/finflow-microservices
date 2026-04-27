package com.finflow.saga.steps;

/**
 * Message consumed by the saga orchestrator from the saga.replies queue after a participant
 * processes a saga command.
 */
public record SagaReply(
        String sagaId,
        String stepName,
        boolean success,
        String resultId,
        String errorMessage,
        String correlationId) {}
