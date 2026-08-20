package com.tradex.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
    @NotNull(message = "Target userId is required")
    UUID userId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Deposit amount must be strictly positive")
    BigDecimal amount,

    String description
) {}
