package com.finflow.transaction.query;

import jakarta.validation.constraints.NotBlank;

/**
 * Query for summary statistics for an account: total count, total amount, and counts by status.
 */
public record GetTransactionSummaryQuery(@NotBlank String accountId, String requestedBy) {}
