package com.finflow.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.saga.compensation.CompensationEngine;
import com.finflow.saga.state.SagaInstance;
import com.finflow.saga.state.SagaInstanceRepository;
import com.finflow.saga.state.SagaState;
import com.finflow.saga.steps.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompensationEngineTest {

    @Mock private SagaInstanceRepository sagaInstanceRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @Mock private SagaStep step1;
    @Mock private SagaStep step2;
    @Mock private SagaStep step3;
    @Mock private SagaStep step4;

    @InjectMocks private CompensationEngine compensationEngine;

    @BeforeEach
    void setUp() {
        compensationEngine =
                new CompensationEngine(
                        java.util.List.of(step4, step2, step1, step3),
                        rabbitTemplate,
                        sagaInstanceRepository);
        when(step1.getStepNumber()).thenReturn(1);
        when(step2.getStepNumber()).thenReturn(2);
        when(step3.getStepNumber()).thenReturn(3);
        when(step4.getStepNumber()).thenReturn(4);

        when(step1.getStepName()).thenReturn("CREATE_ACCOUNT");
        when(step2.getStepName()).thenReturn("VERIFY_KYC");
        when(step3.getStepName()).thenReturn("CREATE_KEYCLOAK_USER");
        when(step4.getStepName()).thenReturn("SEND_WELCOME_EMAIL");

        when(sagaInstanceRepository.save(any(SagaInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startCompensation_RunsStepsInReverse_WhenFailedAtStep3() {
        SagaInstance saga =
                SagaInstance.builder().sagaId("saga-001").currentStep(2).state(SagaState.STEP_2_PENDING).build();

        compensationEngine.startCompensation(saga, "KYC failed", 3);

        verify(step2).compensate(saga, rabbitTemplate);
        verify(step1).compensate(saga, rabbitTemplate);
        verify(step3, never()).compensate(any(), any());
        assertThat(saga.getState()).isEqualTo(SagaState.FAILED);
    }

    @Test
    void startCompensation_MarksCompensationFailed_WhenCompensationThrows() {
        SagaInstance saga =
                SagaInstance.builder().sagaId("saga-001").currentStep(1).state(SagaState.STEP_2_PENDING).build();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(step1)
                .compensate(any(SagaInstance.class), any(RabbitTemplate.class));

        compensationEngine.startCompensation(saga, "failed", 2);

        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATION_FAILED);
        verify(sagaInstanceRepository, org.mockito.Mockito.atLeastOnce()).save(saga);
    }

    @Test
    void startCompensation_SkipsCompensation_WhenFailedAtStep1() {
        SagaInstance saga =
                SagaInstance.builder().sagaId("saga-001").currentStep(0).state(SagaState.STEP_1_PENDING).build();

        compensationEngine.startCompensation(saga, "error", 1);

        verify(step1, never()).compensate(any(), any());
        verify(step2, never()).compensate(any(), any());
        verify(step3, never()).compensate(any(), any());
        verify(step4, never()).compensate(any(), any());
        assertThat(saga.getState()).isEqualTo(SagaState.FAILED);
    }
}
