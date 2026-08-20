package com.tradex.order.dto;

import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
    @NotBlank(message = "Symbol is required")
    String symbol,

    @NotNull(message = "Order side (BUY/SELL) is required")
    OrderSide side,

    @NotNull(message = "Order type (MARKET/LIMIT) is required")
    OrderType orderType,

    @Positive(message = "Quantity must be strictly positive")
    long quantity,

    BigDecimal limitPrice,

    @NotBlank(message = "clientOrderId is required for idempotency")
    String clientOrderId
) {}
