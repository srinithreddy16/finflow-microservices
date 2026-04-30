package com.finflow.graphql.client.dto;

import java.time.Instant;

public record AccountDto(
        String id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String status,
        String tenantId,
        boolean kycVerified,
        Instant createdAt,
        Instant updatedAt) {}
