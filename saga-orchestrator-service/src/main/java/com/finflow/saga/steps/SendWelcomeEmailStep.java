package com.finflow.saga.steps;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SendWelcomeEmailStep implements SagaStep {

    private static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    private static final String WELCOME_ROUTING_KEY = "saga.notify.welcome";
    private static final String CANCELLATION_ROUTING_KEY = "saga.notify.cancellation";

    public record WelcomeEmailPayload(
            String accountId, String email, String firstName, String lastName, String sagaId) {}

    @Override
    public int getStepNumber() {
        return 4;
    }

    @Override
    public String getStepName() {
        return "SEND_WELCOME_EMAIL";
    }

    @Override
    public String getCommandType() {
        return "SEND_WELCOME_EMAIL";
    }

    @Override
    public void execute(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        WelcomeEmailPayload payload =
                new WelcomeEmailPayload(
                        saga.getAccountId(),
                        saga.getEmail(),
                        saga.getFirstName(),
                        saga.getLastName(),
                        saga.getSagaId());

        SagaCommand command =
                new SagaCommand(
                        saga.getSagaId(), "SEND_WELCOME_EMAIL", saga.getCorrelationId(), payload);
        rabbitTemplate.convertAndSend(SAGA_COMMANDS_EXCHANGE, WELCOME_ROUTING_KEY, command);
        log.info("Step 4 executed: SEND_WELCOME_EMAIL for saga: {}", saga.getSagaId());
    }

    @Override
    public void compensate(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        log.info("Step 4 compensation: welcome email cannot be recalled");
        SagaCommand cancellationCommand =
                new SagaCommand(
                        saga.getSagaId(),
                        "SEND_CANCELLATION_EMAIL",
                        saga.getCorrelationId(),
                        new WelcomeEmailPayload(
                                saga.getAccountId(),
                                saga.getEmail(),
                                saga.getFirstName(),
                                saga.getLastName(),
                                saga.getSagaId()));
        rabbitTemplate.convertAndSend(
                SAGA_COMMANDS_EXCHANGE, CANCELLATION_ROUTING_KEY, cancellationCommand);
    }

    @Override
    public SagaState getPendingState() {
        return SagaState.STEP_4_PENDING;
    }

    @Override
    public SagaState getCompletedState() {
        return SagaState.COMPLETED;
    }
}
