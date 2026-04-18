package com.finflow.transaction.projection;

import com.finflow.transaction.event.TransactionCompletedEvent;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.event.TransactionFailedEvent;
import com.finflow.transaction.event.TransactionReversedEvent;
import com.finflow.transaction.model.Transaction;
import com.finflow.transaction.model.TransactionStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events published after the command transaction commits and updates the
 * read-side projection ({@code transaction_projection}) so queries stay aligned with the EventStore.
 * <p>
 * This is the bridge between the CQRS write path (aggregates + EventStore) and the read model.
 * <p>
 * <strong>Eventually consistent reads:</strong> The projection may lag behind the EventStore by
 * milliseconds. Queries should tolerate this — if a projection row is not found immediately after
 * a command, it is being built.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionProjectionUpdater {

    private final ProjectionRepository projectionRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onTransactionCreated(TransactionCreatedEvent event) {
        Transaction projection =
                Transaction.builder()
                        .id(event.getAggregateId())
                        .accountId(event.getAccountId())
                        .amount(event.getAmount())
                        .currency(event.getCurrency())
                        .description(event.getDescription())
                        .initiatedBy(event.getInitiatedBy())
                        .status(TransactionStatus.PENDING)
                        .correlationId(event.getCorrelationId())
                        .createdAt(event.getOccurredOn())
                        .updatedAt(Instant.now())
                        .build();
        projectionRepository.save(projection);
        log.info("Projection created for transaction: {}", event.getAggregateId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        projectionRepository
                .findById(event.getAggregateId())
                .ifPresentOrElse(
                        tx -> {
                            tx.setStatus(TransactionStatus.COMPLETED);
                            tx.setPaymentId(event.getPaymentId());
                            tx.setCompletedAt(event.getCompletedAt());
                            tx.setUpdatedAt(Instant.now());
                            projectionRepository.save(tx);
                            log.info(
                                    "Projection updated to COMPLETED for transaction: {}",
                                    event.getAggregateId());
                        },
                        () ->
                                log.warn(
                                        "No projection row for transaction: {} (event may have arrived before projection was created)",
                                        event.getAggregateId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onTransactionFailed(TransactionFailedEvent event) {
        projectionRepository
                .findById(event.getAggregateId())
                .ifPresent(
                        tx -> {
                            tx.setStatus(TransactionStatus.FAILED);
                            tx.setFailureReason(event.getReason());
                            tx.setFailureCode(event.getFailureCode());
                            tx.setUpdatedAt(Instant.now());
                            projectionRepository.save(tx);
                        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onTransactionReversed(TransactionReversedEvent event) {
        projectionRepository
                .findById(event.getAggregateId())
                .ifPresent(
                        tx -> {
                            tx.setStatus(TransactionStatus.REVERSED);
                            tx.setReversalReason(event.getReversalReason());
                            tx.setReversedBy(event.getReversedBy());
                            tx.setReversedAt(event.getReversedAt());
                            tx.setUpdatedAt(Instant.now());
                            projectionRepository.save(tx);
                        });
    }
}
