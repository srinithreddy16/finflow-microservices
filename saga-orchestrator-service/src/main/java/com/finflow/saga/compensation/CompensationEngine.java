package com.finflow.saga.compensation;

import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaInstanceRepository;
import com.finflow.saga.state.SagaState;
import com.finflow.saga.steps.SagaStep;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Executes compensation steps in reverse order when a saga fails.
 *
 * <p>Example: Saga fails at step 3 (CREATE_KEYCLOAK_USER) Compensation order: step 2 -> step 1
 * (step 2 KYC has no compensation, step 1 deletes the account)
 *
 * <p>Compensation is best-effort -- if a compensation step itself fails, the saga is marked
 * COMPENSATION_FAILED and requires manual intervention.
 *
 * <p>IMPORTANT: Compensation commands are fire-and-forget. The orchestrator does NOT wait for
 * compensation replies. This simplifies the design at the cost of potential partial rollbacks in
 * extreme failure scenarios (acceptable trade-off).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompensationEngine {

    private final List<SagaStep> steps;
    private final RabbitTemplate rabbitTemplate;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final MeterRegistry meterRegistry;

    public void startCompensation(SagaInstance saga, String failureReason, int failedAtStep) {
        meterRegistry
                .counter("saga.compensation.total", "type", "ACCOUNT_ONBOARDING")
                .increment();
        log.warn(
                "Starting compensation for saga: {}, failed at step: {}, reason: {}",
                saga.getSagaId(),
                failedAtStep,
                failureReason);

        saga.setFailureReason(failureReason);
        saga.setFailureStep(failedAtStep);
        saga.setCompensationReason(
                "Compensating due to failure at step " + failedAtStep + ": " + failureReason);
        saga.setState(SagaState.COMPENSATING);
        sagaInstanceRepository.save(saga);

        List<SagaStep> stepsToCompensate =
                steps.stream()
                        .filter(step -> step.getStepNumber() < failedAtStep)
                        .sorted(Comparator.comparingInt(SagaStep::getStepNumber).reversed())
                        .toList();

        for (SagaStep step : stepsToCompensate) {
            try {
                saga.setState(saga.getState().nextCompensationState());
                sagaInstanceRepository.save(saga);

                step.compensate(saga, rabbitTemplate);
                log.info(
                        "Compensation step {} ({}) executed for saga: {}",
                        step.getStepNumber(),
                        step.getStepName(),
                        saga.getSagaId());
            } catch (Exception ex) {
                log.error(
                        "Compensation step {} FAILED for saga: {}",
                        step.getStepNumber(),
                        saga.getSagaId(),
                        ex);
                saga.setState(SagaState.COMPENSATION_FAILED);
                sagaInstanceRepository.save(saga);
                return;
            }
        }

        saga.setState(SagaState.FAILED);
        saga.setCompletedAt(Instant.now());
        sagaInstanceRepository.save(saga);
        log.warn("Saga {} compensation completed -- saga marked FAILED", saga.getSagaId());
    }

    public boolean isCompensationRequired(SagaInstance saga, int failedAtStep) {
        return failedAtStep > 1;
    }
}
