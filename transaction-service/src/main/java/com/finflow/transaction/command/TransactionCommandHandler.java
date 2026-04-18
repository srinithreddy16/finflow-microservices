package com.finflow.transaction.command;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.transaction.aggregate.TransactionAggregate;
import com.finflow.transaction.event.EventStore;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.event.TransactionEvent;
import com.finflow.transaction.exception.TransactionNotFoundException;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckResponse;
import com.finflow.transaction.grpc.FraudCheckGrpcClient;
import com.finflow.transaction.kafka.TransactionEventPublisher;
import com.finflow.transaction.query.TransactionQueryHandler;
import com.finflow.transaction.saga.PaymentSagaInitiator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionCommandHandler {

    private final EventStore eventStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final FraudCheckGrpcClient fraudCheckGrpcClient;
    private final TransactionEventPublisher transactionEventPublisher;
    private final PaymentSagaInitiator paymentSagaInitiator;
    @Lazy private final TransactionQueryHandler transactionQueryHandler;

    /*
    Checks if this transaction ID already exists in the EventStore.
    Imagine a customer clicks "Pay" twice by accident. Without this check,
    you'd process the payment twice and charge them double. This is called idempotency
     */
    @Transactional
    public TransactionCreatedEvent handle(CreateTransactionCommand command) {
        if (!eventStore.findByAggregateId(command.transactionId()).isEmpty()) {
            throw FinFlowException.badRequest(
                    ErrorCode.TRANSACTION_ALREADY_EXISTS,
                    "Transaction already exists: " + command.transactionId());
        }

        /* Fraud check via gRPC. Calls the fraud-detection-service synchronously via
            gRPC before doing anything else. Why: We need an instant answer — is this transaction safe? gRPC is used here
            because it's fast (binary protocol), typed (protobuf), and synchronous (we wait for the answer before continuing). This is the ONLY gRPC call in the entire system.
         */
        FraudCheckResponse fraudResponse =
                fraudCheckGrpcClient.checkTransaction(
                        command.transactionId(),
                        command.accountId(),
                        command.amount(),
                        command.currency(),
                        "UNKNOWN");

        if (fraudResponse.getFlagged()) {
            try {
                TransactionAggregate aggregate =
                        TransactionAggregate.create(
                                command.transactionId(),
                                command.accountId(),
                                command.amount(),
                                command.currency(),
                                command.description(),
                                command.initiatedBy(),
                                command.correlationId());
                aggregate.fail(
                        fraudResponse.getReason() != null && !fraudResponse.getReason().isEmpty()
                                ? fraudResponse.getReason()
                                : "Fraud detected",
                        "FRAUD_DETECTED",
                        command.correlationId());
                commitAndPublish(aggregate);
                transactionEventPublisher.publishFailed(aggregate);
                transactionQueryHandler.evictCacheForAccount(aggregate.getAccountId());
            } catch (IllegalStateException e) {
                throw FinFlowException.badRequest(ErrorCode.VALIDATION_FAILED, e.getMessage());
            }
            throw FinFlowException.badRequest(
                    ErrorCode.TRANSACTION_FLAGGED_AS_FRAUD,
                    "Transaction flagged by fraud detection: "
                            + (fraudResponse.getReason() != null ? fraudResponse.getReason() : ""));
        }

        try {
            TransactionAggregate aggregate =
                    TransactionAggregate.create(
                            command.transactionId(),
                            command.accountId(),
                            command.amount(),
                            command.currency(),
                            command.description(),
                            command.initiatedBy(),
                            command.correlationId());

            TransactionCreatedEvent created =
                    (TransactionCreatedEvent) aggregate.getUncommittedEvents().getFirst();

            commitAndPublish(aggregate);

            transactionEventPublisher.publishCreated(aggregate);
            paymentSagaInitiator.initiatePayment(aggregate);
            transactionQueryHandler.evictCacheForAccount(aggregate.getAccountId());

            log.info(
                    "Transaction created: {} for account: {}, amount: {} {}",
                    command.transactionId(),
                    command.accountId(),
                    command.amount(),
                    command.currency());

            return created;
        } catch (IllegalStateException e) {
            throw FinFlowException.badRequest(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
    }

    @Transactional
    public void handle(CompleteTransactionCommand command) {
        List<TransactionEvent> events = eventStore.findByAggregateId(command.transactionId());
        if (events.isEmpty()) {
            throw new TransactionNotFoundException(command.transactionId());
        }
        try {
            TransactionAggregate aggregate = TransactionAggregate.reconstitute(events);
            aggregate.complete(command.paymentId(), command.correlationId());
            commitAndPublish(aggregate);
            transactionEventPublisher.publishCompleted(aggregate);
            transactionQueryHandler.evictCacheForAccount(aggregate.getAccountId());
            log.info("Transaction completed: {}", command.transactionId());
        } catch (IllegalStateException e) {
            throw FinFlowException.badRequest(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
    }

    @Transactional
    public void handle(ReverseTransactionCommand command) {
        List<TransactionEvent> events = eventStore.findByAggregateId(command.transactionId());
        if (events.isEmpty()) {
            throw new TransactionNotFoundException(command.transactionId());
        }
        try {
            TransactionAggregate aggregate = TransactionAggregate.reconstitute(events);
            aggregate.reverse(
                    command.reversalReason(), command.reversedBy(), command.correlationId());
            commitAndPublish(aggregate);
            transactionEventPublisher.publishReversed(aggregate);
            transactionQueryHandler.evictCacheForAccount(aggregate.getAccountId());
            log.info(
                    "Transaction reversed: {} by: {}",
                    command.transactionId(),
                    command.reversedBy());
        } catch (IllegalStateException e) {
            throw FinFlowException.badRequest(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
    }

    private void commitAndPublish(TransactionAggregate aggregate) {
        List<TransactionEvent> events = new ArrayList<>(aggregate.getUncommittedEvents());
        for (TransactionEvent event : events) {
            eventStore.append(event);
            applicationEventPublisher.publishEvent(event);
        }
        aggregate.markEventsAsCommitted();
        log.debug(
                "Committed and published {} events for aggregate: {}",
                events.size(),
                aggregate.getTransactionId());
    }
}
