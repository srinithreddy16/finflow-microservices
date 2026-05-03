package com.finflow.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.saga.compensation.CompensationEngine;
import com.finflow.saga.orchestrator.OnboardingSagaOrchestrator;
import com.finflow.saga.orchestrator.OnboardingSagaOrchestrator.OnboardingRequest;
import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaInstanceRepository;
import com.finflow.saga.state.SagaState;
import com.finflow.saga.steps.SagaStep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnboardingSagaOrchestratorTest {

    @Mock private SagaInstanceRepository sagaInstanceRepository;
    @Mock private CompensationEngine compensationEngine;
    @Mock private RabbitTemplate rabbitTemplate;

    @Mock private SagaStep step1;
    @Mock private SagaStep step2;
    @Mock private SagaStep step3;
    @Mock private SagaStep step4;

    private OnboardingSagaOrchestrator onboardingSagaOrchestrator;

    @BeforeEach
    void setUp() {
        onboardingSagaOrchestrator =
                new OnboardingSagaOrchestrator(
                        sagaInstanceRepository,
                        List.of(step2, step4, step1, step3),
                        compensationEngine,
                        rabbitTemplate,
                        new SimpleMeterRegistry());
        ReflectionTestUtils.invokeMethod(onboardingSagaOrchestrator, "registerActiveSagaGauge");

        when(step1.getStepNumber()).thenReturn(1);
        when(step1.getStepName()).thenReturn("CREATE_ACCOUNT");
        when(step1.getPendingState()).thenReturn(SagaState.STEP_1_PENDING);
        when(step1.getCompletedState()).thenReturn(SagaState.STEP_1_COMPLETED);

        when(step2.getStepNumber()).thenReturn(2);
        when(step2.getStepName()).thenReturn("VERIFY_KYC");
        when(step2.getPendingState()).thenReturn(SagaState.STEP_2_PENDING);
        when(step2.getCompletedState()).thenReturn(SagaState.STEP_2_COMPLETED);

        when(step3.getStepNumber()).thenReturn(3);
        when(step3.getStepName()).thenReturn("CREATE_KEYCLOAK_USER");
        when(step3.getPendingState()).thenReturn(SagaState.STEP_3_PENDING);
        when(step3.getCompletedState()).thenReturn(SagaState.STEP_3_COMPLETED);

        when(step4.getStepNumber()).thenReturn(4);
        when(step4.getStepName()).thenReturn("SEND_WELCOME_EMAIL");
        when(step4.getPendingState()).thenReturn(SagaState.STEP_4_PENDING);
        when(step4.getCompletedState()).thenReturn(SagaState.COMPLETED);
    }

    @Test
    void startSaga_CreatesInstanceAndExecutesStep1() {
        when(sagaInstanceRepository.findByEmailOrderByCreatedAtDesc("user@finflow.com"))
                .thenReturn(List.of());
        when(sagaInstanceRepository.save(any(SagaInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request =
                new OnboardingRequest(
                        "user@finflow.com", "Jane", "Doe", "555-0100", "tenant-1", "corr-1");

        SagaInstance saga = onboardingSagaOrchestrator.startSaga(request);

        verify(sagaInstanceRepository, atLeastOnce()).save(any(SagaInstance.class));
        verify(step1).execute(any(SagaInstance.class), eq(rabbitTemplate));
        assertThat(saga.getState()).isEqualTo(SagaState.STEP_1_PENDING);
    }

    @Test
    void startSaga_ThrowsException_WhenActiveSagaExists() {
        SagaInstance existing =
                SagaInstance.builder()
                        .sagaId("saga-existing")
                        .email("user@finflow.com")
                        .state(SagaState.STEP_1_PENDING)
                        .build();
        when(sagaInstanceRepository.findByEmailOrderByCreatedAtDesc("user@finflow.com"))
                .thenReturn(List.of(existing));

        OnboardingRequest request =
                new OnboardingRequest(
                        "user@finflow.com", "Jane", "Doe", "555-0100", "tenant-1", "corr-1");

        assertThatThrownBy(() -> onboardingSagaOrchestrator.startSaga(request))
                .isInstanceOf(FinFlowException.class)
                .satisfies(
                        ex ->
                                assertThat(((FinFlowException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS));
    }

    @Test
    void handleStepSuccess_AdvancesToNextStep_AfterStep1() {
        SagaInstance saga =
                SagaInstance.builder()
                        .sagaId("saga-001")
                        .email("user@finflow.com")
                        .state(SagaState.STEP_1_PENDING)
                        .currentStep(0)
                        .build();
        when(sagaInstanceRepository.findById("saga-001")).thenReturn(Optional.of(saga));
        when(sagaInstanceRepository.save(any(SagaInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingSagaOrchestrator.handleStepSuccess(
                "saga-001", "CREATE_ACCOUNT", "account-001", "corr-001");

        assertThat(saga.getAccountId()).isEqualTo("account-001");
        assertThat(saga.getCurrentStep()).isEqualTo(1);
        assertThat(saga.getState()).isEqualTo(SagaState.STEP_2_PENDING);
        verify(step2).execute(saga, rabbitTemplate);
    }

    @Test
    void handleStepSuccess_CompletesSaga_AfterStep4() {
        SagaInstance saga =
                SagaInstance.builder()
                        .sagaId("saga-001")
                        .email("user@finflow.com")
                        .state(SagaState.STEP_4_PENDING)
                        .currentStep(3)
                        .build();
        when(sagaInstanceRepository.findById("saga-001")).thenReturn(Optional.of(saga));
        when(sagaInstanceRepository.save(any(SagaInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        onboardingSagaOrchestrator.handleStepSuccess(
                "saga-001", "SEND_WELCOME_EMAIL", "ok", "corr-001");

        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(saga.getCompletedAt()).isNotNull();
        verify(step1, never()).execute(any(), any());
        verify(step2, never()).execute(any(), any());
        verify(step3, never()).execute(any(), any());
        verify(step4, never()).execute(any(), any());
    }

    @Test
    void handleStepFailure_StartsCompensation() {
        SagaInstance saga =
                SagaInstance.builder()
                        .sagaId("saga-001")
                        .state(SagaState.STEP_2_PENDING)
                        .currentStep(1)
                        .build();
        when(sagaInstanceRepository.findById("saga-001")).thenReturn(Optional.of(saga));

        onboardingSagaOrchestrator.handleStepFailure(
                "saga-001", "VERIFY_KYC", "KYC check failed", "corr-001");

        verify(compensationEngine)
                .startCompensation(saga, "KYC check failed", 2);
    }

    @Test
    void handleStepSuccess_IgnoresLateReply_ForTerminalSaga() {
        SagaInstance saga =
                SagaInstance.builder()
                        .sagaId("saga-001")
                        .state(SagaState.COMPLETED)
                        .currentStep(4)
                        .build();
        when(sagaInstanceRepository.findById("saga-001")).thenReturn(Optional.of(saga));

        onboardingSagaOrchestrator.handleStepSuccess(
                "saga-001", "SEND_WELCOME_EMAIL", "ok", "corr-001");

        verify(step1, never()).execute(any(), any());
        verify(step2, never()).execute(any(), any());
        verify(step3, never()).execute(any(), any());
        verify(step4, never()).execute(any(), any());
        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
    }
}
