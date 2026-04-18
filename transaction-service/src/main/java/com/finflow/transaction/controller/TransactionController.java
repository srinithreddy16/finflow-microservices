package com.finflow.transaction.controller;

import com.finflow.common.dto.PagedResponse;
import com.finflow.common.util.IdGenerator;
import com.finflow.transaction.command.CompleteTransactionCommand;
import com.finflow.transaction.command.CreateTransactionCommand;
import com.finflow.transaction.command.ReverseTransactionCommand;
import com.finflow.transaction.command.TransactionCommandHandler;
import com.finflow.transaction.dto.CompleteTransactionRequest;
import com.finflow.transaction.dto.ReverseTransactionRequest;
import com.finflow.transaction.dto.TransactionRequestDto;
import com.finflow.transaction.dto.TransactionResponseDto;
import com.finflow.transaction.dto.TransactionSummaryDto;
import com.finflow.transaction.mapper.TransactionMapper;
import com.finflow.transaction.model.TransactionStatus;
import com.finflow.transaction.query.GetTransactionQuery;
import com.finflow.transaction.query.GetTransactionSummaryQuery;
import com.finflow.transaction.query.GetTransactionsByAccountQuery;
import com.finflow.transaction.query.TransactionQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for transaction commands (writes) and queries (reads) against the CQRS projection.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction management endpoints")
public class TransactionController {

    private final TransactionCommandHandler transactionCommandHandler;
    private final TransactionQueryHandler transactionQueryHandler;

    @SuppressWarnings("unused")
    private final TransactionMapper transactionMapper;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<TransactionResponseDto> createTransaction(
            @RequestBody @Valid TransactionRequestDto request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String transactionId = IdGenerator.generate();
        if (correlationId == null) {
            correlationId = IdGenerator.correlationId();
        }
        if (userId == null) {
            userId = "anonymous";
        }
        CreateTransactionCommand command =
                new CreateTransactionCommand(
                        transactionId,
                        request.accountId(),
                        request.amount(),
                        request.currency(),
                        request.description(),
                        userId,
                        correlationId);

        transactionCommandHandler.handle(command);

        TransactionResponseDto responseDto =
                transactionQueryHandler.handle(new GetTransactionQuery(transactionId, userId));

        log.info(
                "Transaction creation requested: {} for account: {}",
                transactionId,
                request.accountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public TransactionResponseDto getTransaction(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return transactionQueryHandler.handle(new GetTransactionQuery(id, resolveUserId(userId)));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get all transactions for an account")
    public PagedResponse<TransactionResponseDto> getTransactionsByAccount(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        GetTransactionsByAccountQuery query =
                new GetTransactionsByAccountQuery(
                        accountId,
                        status,
                        page,
                        size,
                        sortBy,
                        sortDirection,
                        resolveUserId(userId));
        return transactionQueryHandler.handle(query);
    }

    @GetMapping("/account/{accountId}/summary")
    @Operation(summary = "Get transaction summary for an account")
    public TransactionSummaryDto getTransactionSummary(
            @PathVariable String accountId,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return transactionQueryHandler.handle(
                new GetTransactionSummaryQuery(accountId, resolveUserId(userId)));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Mark a transaction as completed (internal use)")
    public ResponseEntity<Void> completeTransaction(
            @PathVariable String id,
            @RequestBody CompleteTransactionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        TransactionResponseDto existing =
                transactionQueryHandler.handle(new GetTransactionQuery(id, resolveUserId(userId)));
        String accountId = existing.accountId();

        transactionCommandHandler.handle(
                new CompleteTransactionCommand(id, request.paymentId(), request.correlationId()));
        transactionQueryHandler.evictCacheForAccount(accountId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/reverse")
    @Operation(summary = "Reverse a completed transaction")
    public ResponseEntity<Void> reverseTransaction(
            @PathVariable String id,
            @RequestBody @Valid ReverseTransactionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        transactionCommandHandler.handle(
                new ReverseTransactionCommand(
                        id,
                        request.reversalReason(),
                        resolveUserId(userId),
                        request.correlationId()));
        return ResponseEntity.ok().build();
    }

    private static String resolveUserId(String userId) {
        return userId != null ? userId : "anonymous";
    }
}
