package com.finflow.account.dto;

import com.finflow.account.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusUpdateDto(@NotNull AccountStatus status, String reason) {}
