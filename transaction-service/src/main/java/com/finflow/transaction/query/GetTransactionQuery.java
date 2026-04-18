package com.finflow.transaction.query;

import jakarta.validation.constraints.NotBlank;

/**
 * Query to retrieve a single transaction by ID.
 *
 * <p>The requestedBy field is used to enforce that users can only see their own transactions unless
 * they have {@code ROLE_BUSINESS_ADMIN}.
 */
public record GetTransactionQuery(@NotBlank String transactionId, String requestedBy) {}
