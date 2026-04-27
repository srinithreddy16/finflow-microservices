package com.finflow.saga.steps;

/**
 * Message published by the saga orchestrator to the saga.commands exchange for step execution or
 * compensation requests.
 */
public record SagaCommand(String sagaId, String commandType, String correlationId, Object payload) {}
