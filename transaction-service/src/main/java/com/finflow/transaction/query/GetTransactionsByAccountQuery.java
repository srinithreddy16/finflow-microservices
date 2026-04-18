package com.finflow.transaction.query;

import com.finflow.transaction.model.TransactionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Query for listing transactions for an account with optional status filter, paging, and sorting.
 *
 * <p>When {@code status} is null, all statuses are included. Default page size is 20; size must not
 * exceed 100.
 */
public record GetTransactionsByAccountQuery(
        @NotBlank String accountId,
        TransactionStatus status,
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        String sortBy,
        String sortDirection,
        String requestedBy) {

    /**
     * Builds a query with defaults: page 0, size 20, no status filter, sort by {@code createdAt}
     * descending.
     */
    public static GetTransactionsByAccountQuery of(String accountId, String requestedBy) {
        return new GetTransactionsByAccountQuery(
                accountId, null, 0, 20, "createdAt", "DESC", requestedBy);
    }
}
