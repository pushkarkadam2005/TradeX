package com.tradex.withdrawal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateWithdrawalRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be strictly positive")
    BigDecimal amount,

    @NotBlank(message = "Destination reference is required")
    String destinationReference,

    @NotBlank(message = "Idempotency key is required")
    String idempotencyKey
) {}
