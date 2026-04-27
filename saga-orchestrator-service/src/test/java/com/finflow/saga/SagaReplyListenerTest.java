package com.finflow.saga;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.saga.orchestrator.OnboardingSagaOrchestrator;
import com.finflow.saga.orchestrator.SagaReplyListener;
import com.finflow.saga.steps.SagaReply;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaReplyListenerTest {

    @Mock private OnboardingSagaOrchestrator sagaOrchestrator;
    @Mock private Channel channel;

    @InjectMocks private SagaReplyListener sagaReplyListener;

    @Test
    void handleSagaReply_CallsHandleSuccess_OnSuccessReply() throws Exception {
        SagaReply reply =
                new SagaReply(
                        "saga-001",
                        "CREATE_ACCOUNT",
                        true,
                        "account-001",
                        null,
                        "corr-001");

        sagaReplyListener.handleSagaReply(reply, channel, 1L);

        verify(sagaOrchestrator)
                .handleStepSuccess("saga-001", "CREATE_ACCOUNT", "account-001", "corr-001");
        verify(channel).basicAck(1L, false);
    }

    @Test
    void handleSagaReply_CallsHandleFailure_OnFailureReply() throws Exception {
        SagaReply reply =
                new SagaReply("saga-001", "VERIFY_KYC", false, null, "KYC failed", "corr-001");

        sagaReplyListener.handleSagaReply(reply, channel, 1L);

        verify(sagaOrchestrator)
                .handleStepFailure("saga-001", "VERIFY_KYC", "KYC failed", "corr-001");
        verify(channel).basicAck(1L, false);
    }

    @Test
    void handleSagaReply_Nacks_WhenOrchestratorThrows() throws Exception {
        SagaReply reply =
                new SagaReply(
                        "saga-001",
                        "CREATE_ACCOUNT",
                        true,
                        "account-001",
                        null,
                        "corr-001");
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(sagaOrchestrator)
                .handleStepSuccess("saga-001", "CREATE_ACCOUNT", "account-001", "corr-001");

        sagaReplyListener.handleSagaReply(reply, channel, 1L);

        verify(channel).basicNack(1L, false, false);
    }

    @Test
    void handleSagaReply_Acks_AndIgnores_WhenReplyIsNull() throws Exception {
        sagaReplyListener.handleSagaReply(null, channel, 1L);

        verify(channel).basicAck(1L, false);
        verifyNoInteractions(sagaOrchestrator);
    }
}
