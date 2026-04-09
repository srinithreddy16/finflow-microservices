package com.finflow.account.dto;

import java.time.LocalDateTime;

public record AccountResponseDto(
        String id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String status,
        String tenantId,
        boolean kycVerified,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
