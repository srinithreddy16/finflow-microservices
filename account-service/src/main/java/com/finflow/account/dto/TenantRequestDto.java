package com.finflow.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TenantRequestDto(
        @NotBlank String name,
        @NotBlank String slug,
        @NotBlank @Email String ownerEmail) {}
