package com.finflow.saga.orchestrator;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.common.util.IdGenerator;
import com.finflow.saga.compensation.CompensationEngine;
import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaInstanceRepository;
import com.finflow.saga.state.SagaState;
import com.finflow.saga.steps.SagaStep;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central state machine for Account Onboarding orchestration.
 *
 * <p>State transition flow:
 *
 * <p>STARTED -> STEP_1_PENDING -> STEP_1_COMPLETED -> STEP_2_PENDING -> STEP_2_COMPLETED ->
 * STEP_3_PENDING -> STEP_3_COMPLETED -> STEP_4_PENDING -> COMPLETED
 *
 * <p>On any step failure, compensation is triggered in reverse order via {@link CompensationEngine}
 * and terminal state becomes FAILED or COMPENSATION_FAILED.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingSagaOrchestrator {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final List<SagaStep> steps;
    private final CompensationEngine compensationEngine;
    private final RabbitTemplate rabbitTemplate;

    public record OnboardingRequest(
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String tenantId,
            String correlationId) {}

    @Transactional
    public SagaInstance startSaga(OnboardingRequest request) {
        List<SagaInstance> existing =
                sagaInstanceRepository.findByEmailOrderByCreatedAtDesc(request.email());

        boolean hasActiveSaga = existing.stream().anyMatch(saga -> !saga.getState().isTerminal());
        if (hasActiveSaga) {
            throw FinFlowException.badRequest(
                    ErrorCode.ACCOUNT_ALREADY_EXISTS,
                    "Active onboarding already in progress for: " + request.email());
        }

        SagaInstance saga =
                SagaInstance.builder()
                        .sagaId(IdGenerator.generate())
                        .sagaType("ACCOUNT_ONBOARDING")
                        .state(SagaState.STARTED)
                        .currentStep(0)
                        .email(request.email())
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .phoneNumber(request.phoneNumber())
                        .tenantId(request.tenantId())
                        .correlationId(
                                request.correlationId() != null
                                        ? request.correlationId()
                                        : IdGenerator.correlationId())
                        .retryCount(0)
                        .maxRetries(3)
                        .build();

        saga = sagaInstanceRepository.save(saga);
        executeNextStep(saga);
        log.info("Onboarding saga started: {} for email: {}", saga.getSagaId(), saga.getEmail());
        return saga;
    }

    @Transactional
    public void handleStepSuccess(
            String sagaId, String stepName, String resultId, String correlationId) {
        SagaInstance saga =
                sagaInstanceRepository
                        .findById(sagaId)
                        .orElseThrow(
                                () ->
                                        FinFlowException.notFound(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Saga not found: " + sagaId));

        if (saga.getState().isTerminal()) {
            log.warn("Ignoring late success reply for terminal saga: {}", sagaId);
            return;
        }

        log.info("Step {} succeeded for saga: {}, resultId: {}", stepName, sagaId, resultId);

        switch (stepName) {
            case "CREATE_ACCOUNT" -> {
                saga.setAccountId(resultId);
                saga.setCurrentStep(1);
            }
            case "VERIFY_KYC" -> saga.setCurrentStep(2);
            case "CREATE_KEYCLOAK_USER" -> {
                saga.setKeycloakUserId(resultId);
                saga.setCurrentStep(3);
            }
            case "SEND_WELCOME_EMAIL" -> saga.setCurrentStep(4);
            default -> log.warn("Unknown step: {}", stepName);
        }

        Optional<SagaStep> currentStep =
                sortedSteps().stream().filter(s -> s.getStepName().equals(stepName)).findFirst();
        if (currentStep.isPresent()) {
            saga.setState(currentStep.get().getCompletedState());
        }

        saga = sagaInstanceRepository.save(saga);

        if (saga.getState() == SagaState.COMPLETED) {
            saga.setCompletedAt(Instant.now());
            sagaInstanceRepository.save(saga);
            log.info("Saga {} COMPLETED successfully for email: {}", saga.getSagaId(), saga.getEmail());
            return;
        }

        executeNextStep(saga);
    }

    @Transactional
    public void handleStepFailure(
            String sagaId, String stepName, String errorMessage, String correlationId) {
        SagaInstance saga =
                sagaInstanceRepository
                        .findById(sagaId)
                        .orElseThrow(
                                () ->
                                        FinFlowException.notFound(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Saga not found: " + sagaId));

        if (saga.getState().isTerminal()) {
            return;
        }

        log.warn("Step {} FAILED for saga: {}, error: {}", stepName, sagaId, errorMessage);

        int failedStep =
                sortedSteps().stream()
                        .filter(s -> s.getStepName().equals(stepName))
                        .mapToInt(SagaStep::getStepNumber)
                        .findFirst()
                        .orElse(saga.getCurrentStep() + 1);

        compensationEngine.startCompensation(saga, errorMessage, failedStep);
    }

    private void executeNextStep(SagaInstance saga) {
        int nextStepNumber = saga.getCurrentStep() + 1;

        Optional<SagaStep> nextStep =
                sortedSteps().stream().filter(s -> s.getStepNumber() == nextStepNumber).findFirst();

        if (nextStep.isEmpty()) {
            saga.setState(SagaState.COMPLETED);
            saga.setCompletedAt(Instant.now());
            sagaInstanceRepository.save(saga);
            log.info("All steps completed -- saga {} marked COMPLETED", saga.getSagaId());
            return;
        }

        SagaStep step = nextStep.get();
        saga.setState(step.getPendingState());
        sagaInstanceRepository.save(saga);

        try {
            step.execute(saga, rabbitTemplate);
            log.info(
                    "Step {} ({}) command sent for saga: {}",
                    step.getStepNumber(),
                    step.getStepName(),
                    saga.getSagaId());
        } catch (Exception ex) {
            log.error(
                    "Failed to send step {} command for saga: {}",
                    step.getStepNumber(),
                    saga.getSagaId(),
                    ex);
            compensationEngine.startCompensation(saga, ex.getMessage(), nextStepNumber);
        }
    }

    public SagaInstance getSagaStatus(String sagaId) {
        return sagaInstanceRepository
                .findById(sagaId)
                .orElseThrow(
                        () ->
                                FinFlowException.notFound(
                                        ErrorCode.RESOURCE_NOT_FOUND, "Saga not found: " + sagaId));
    }

    public List<SagaInstance> getActiveSagas() {
        return sagaInstanceRepository.findByStateNotInOrderByCreatedAtDesc(
                List.of(SagaState.COMPLETED, SagaState.FAILED, SagaState.COMPENSATION_FAILED));
    }

    public List<SagaInstance> getSagaHistoryByEmail(String email) {
        return sagaInstanceRepository.findByEmailOrderByCreatedAtDesc(email);
    }

    private List<SagaStep> sortedSteps() {
        return steps.stream().sorted(Comparator.comparingInt(SagaStep::getStepNumber)).toList();
    }
}
