package com.finflow.saga.steps;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KycCheckStep implements SagaStep {

    private static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    private static final String COMMAND_ROUTING_KEY = "account.commands";

    public record KycCheckPayload(String accountId, String email) {}

    @Override
    public int getStepNumber() {
        return 2;
    }

    @Override
    public String getStepName() {
        return "VERIFY_KYC";
    }

    @Override
    public String getCommandType() {
        return "VERIFY_KYC";
    }

    @Override
    public void execute(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        KycCheckPayload payload = new KycCheckPayload(saga.getAccountId(), saga.getEmail());
        SagaCommand command =
                new SagaCommand(saga.getSagaId(), "VERIFY_KYC", saga.getCorrelationId(), payload);
        rabbitTemplate.convertAndSend(SAGA_COMMANDS_EXCHANGE, COMMAND_ROUTING_KEY, command);
        log.info("Step 2 executed: VERIFY_KYC command sent for saga: {}", saga.getSagaId());
    }

    @Override
    public void compensate(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        log.info("Step 2 compensation: KYC check has no compensation needed");
    }

    @Override
    public SagaState getPendingState() {
        return SagaState.STEP_2_PENDING;
    }

    @Override
    public SagaState getCompletedState() {
        return SagaState.STEP_2_COMPLETED;
    }
}
