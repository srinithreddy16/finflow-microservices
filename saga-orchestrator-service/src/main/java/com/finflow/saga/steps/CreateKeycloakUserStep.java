package com.finflow.saga.steps;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaState;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateKeycloakUserStep implements SagaStep {

    private static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    private static final String COMMAND_ROUTING_KEY = "account.commands";

    public record KeycloakUserPayload(
            String accountId, String email, String firstName, String lastName, String initialRole) {}

    @Override
    public int getStepNumber() {
        return 3;
    }

    @Override
    public String getStepName() {
        return "CREATE_KEYCLOAK_USER";
    }

    @Override
    public String getCommandType() {
        return "CREATE_KEYCLOAK_USER";
    }

    @Override
    public void execute(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        KeycloakUserPayload payload =
                new KeycloakUserPayload(
                        saga.getAccountId(),
                        saga.getEmail(),
                        saga.getFirstName(),
                        saga.getLastName(),
                        "ROLE_CUSTOMER");

        SagaCommand command =
                new SagaCommand(
                        saga.getSagaId(), "CREATE_KEYCLOAK_USER", saga.getCorrelationId(), payload);
        rabbitTemplate.convertAndSend(SAGA_COMMANDS_EXCHANGE, COMMAND_ROUTING_KEY, command);
        log.info("Step 3 executed: CREATE_KEYCLOAK_USER for saga: {}", saga.getSagaId());
    }

    @Override
    public void compensate(SagaInstance saga, RabbitTemplate rabbitTemplate) {
        if (saga.getKeycloakUserId() == null) {
            log.warn("Step 3 compensation skipped -- no keycloakUserId");
            return;
        }

        SagaCommand command =
                new SagaCommand(
                        saga.getSagaId(),
                        "DELETE_KEYCLOAK_USER",
                        saga.getCorrelationId(),
                        Map.of("keycloakUserId", saga.getKeycloakUserId(), "sagaId", saga.getSagaId()));
        rabbitTemplate.convertAndSend(SAGA_COMMANDS_EXCHANGE, COMMAND_ROUTING_KEY, command);
        log.info("Step 3 compensation sent: DELETE_KEYCLOAK_USER for saga: {}", saga.getSagaId());
    }

    @Override
    public SagaState getPendingState() {
        return SagaState.STEP_3_PENDING;
    }

    @Override
    public SagaState getCompletedState() {
        return SagaState.STEP_3_COMPLETED;
    }
}
