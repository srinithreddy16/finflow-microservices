package com.finflow.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountRequestDto(
        @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phoneNumber,
        String tenantId,
        String accountId) {}
