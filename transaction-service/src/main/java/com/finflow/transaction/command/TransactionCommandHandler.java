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
import com.finflow.transaction.saga.PaymentSagaInitiator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionCommandHandler {

    private final EventStore eventStore;
    private final FraudCheckGrpcClient fraudCheckGrpcClient;
    private final TransactionEventPublisher transactionEventPublisher;
    private final PaymentSagaInitiator paymentSagaInitiator;

    @Transactional
    public TransactionCreatedEvent handle(CreateTransactionCommand command) {
        if (!eventStore.findByAggregateId(command.transactionId()).isEmpty()) {
            throw FinFlowException.badRequest(
                    ErrorCode.TRANSACTION_ALREADY_EXISTS,
                    "Transaction already exists: " + command.transactionId());
        }

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
                List<TransactionEvent> events =
                        new ArrayList<>(aggregate.getUncommittedEvents());
                for (TransactionEvent event : events) {
                    eventStore.append(event);
                }
                aggregate.markEventsAsCommitted();
                transactionEventPublisher.publishFailed(aggregate);
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

            List<TransactionEvent> newEvents = new ArrayList<>(aggregate.getUncommittedEvents());
            TransactionCreatedEvent created =
                    (TransactionCreatedEvent) newEvents.getFirst();

            for (TransactionEvent event : newEvents) {
                eventStore.append(event);
            }
            aggregate.markEventsAsCommitted();

            transactionEventPublisher.publishCreated(aggregate);
            paymentSagaInitiator.initiatePayment(aggregate);

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
            List<TransactionEvent> newEvents =
                    new ArrayList<>(aggregate.getUncommittedEvents());
            for (TransactionEvent event : newEvents) {
                eventStore.append(event);
            }
            aggregate.markEventsAsCommitted();
            transactionEventPublisher.publishCompleted(aggregate);
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
            List<TransactionEvent> newEvents =
                    new ArrayList<>(aggregate.getUncommittedEvents());
            for (TransactionEvent event : newEvents) {
                eventStore.append(event);
            }
            aggregate.markEventsAsCommitted();
            transactionEventPublisher.publishReversed(aggregate);
            log.info(
                    "Transaction reversed: {} by: {}",
                    command.transactionId(),
                    command.reversedBy());
        } catch (IllegalStateException e) {
            throw FinFlowException.badRequest(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
    }
}
