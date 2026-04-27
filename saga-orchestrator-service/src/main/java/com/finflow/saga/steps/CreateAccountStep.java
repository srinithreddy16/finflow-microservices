package com.finflow.saga.steps;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateAccountStep implements SagaStep {

    private static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    private static final String COMMAND_ROUTING_KEY = "account.commands";
    private static final String COMPENSATION_KEY = "saga.account.compensation";

    public record AccountCreatePayload(
            String email, String firstName, String lastName, String phoneNumber, String tenantId) {}

    public record AccountCompensationPayload(
            String sagaId, String accountId, String reason, String correlationId) {}

    @Override
    public int getStepNumber() {
        return 1;
    }

    @Override
    public String getStepName() {
        return "CREATE_ACCOUNT";
    }

    @Override
    public String getCommandType() {
        return "CREATE_ACCOUNT";
    }

    @Override
    public void execute(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        AccountCreatePayload payload =
                new AccountCreatePayload(
                        saga.getEmail(),
                        saga.getFirstName(),
                        saga.getLastName(),
                        saga.getPhoneNumber(),
                        saga.getTenantId());

        SagaCommand command =
                new SagaCommand(
                        saga.getSagaId(), "CREATE_ACCOUNT", saga.getCorrelationId(), payload);

        rabbitTemplate.convertAndSend(SAGA_COMMANDS_EXCHANGE, COMMAND_ROUTING_KEY, command);
        log.info("Step 1 executed: CREATE_ACCOUNT command sent for saga: {}", saga.getSagaId());
    }

    @Override
    public void compensate(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        if (saga.getAccountId() == null) {
            log.warn("Step 1 compensation skipped -- no accountId (account was not created)");
            return;
        }

        AccountCompensationPayload payload =
                new AccountCompensationPayload(
                        saga.getSagaId(),
                        saga.getAccountId(),
                        saga.getCompensationReason(),
                        saga.getCorrelationId());

        rabbitTemplate.convertAndSend(
                SAGA_COMMANDS_EXCHANGE,
                COMPENSATION_KEY,
                new SagaCommand(
                        saga.getSagaId(),
                        "DELETE_ACCOUNT",
                        saga.getCorrelationId(),
                        payload));
        log.info("Step 1 compensation sent: DELETE_ACCOUNT for saga: {}", saga.getSagaId());
    }

    @Override
    public SagaState getPendingState() {
        return SagaState.STEP_1_PENDING;
    }

    @Override
    public SagaState getCompletedState() {
        return SagaState.STEP_1_COMPLETED;
    }
}
