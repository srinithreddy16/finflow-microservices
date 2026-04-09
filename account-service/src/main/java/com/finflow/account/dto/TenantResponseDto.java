package com.finflow.account.dto;

import java.time.LocalDateTime;

public record TenantResponseDto(
        String id,
        String name,
        String slug,
        String ownerEmail,
        String status,
        LocalDateTime createdAt) {}
