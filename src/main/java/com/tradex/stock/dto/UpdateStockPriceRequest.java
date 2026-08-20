package com.tradex.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateStockPriceRequest(
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be strictly positive")
    BigDecimal currentPrice
) {}
