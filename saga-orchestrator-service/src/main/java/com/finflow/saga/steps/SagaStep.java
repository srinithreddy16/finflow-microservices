package com.finflow.saga.steps;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaState;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Strategy interface for individual saga steps. Each step knows:
 *
 * <p>- What command to send (execute) - How to undo itself (compensate) - Which states it
 * transitions through
 *
 * <p>Steps are executed in order (1->2->3->4). Compensation runs in reverse (4->3->2->1).
 *
 * <p>Each step sends a command to saga.commands exchange and the orchestrator waits for a reply on
 * saga.replies queue before calling the next step.
 */
public interface SagaStep {

    int getStepNumber();

    String getStepName();

    String getCommandType();

    void execute(SagaInstance saga, RabbitTemplate rabbitTemplate);

    void compensate(SagaInstance saga, RabbitTemplate rabbitTemplate);

    SagaState getPendingState();

    SagaState getCompletedState();
}
