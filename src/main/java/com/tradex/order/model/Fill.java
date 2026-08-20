package com.tradex.order.model;

import com.tradex.common.dto.FillExecutionRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Fill(
    String tradeExecutionId,
    UUID buyOrderId,
    UUID sellOrderId,
    UUID stockId,
    UUID buyerId,
    UUID sellerId,
    String symbol,
    BigDecimal price,
    long quantity,
    Instant executedAt
) {
    public FillExecutionRequest toExecutionRequest() {
        return new FillExecutionRequest(
            tradeExecutionId,
            buyOrderId,
            sellOrderId,
            stockId,
            buyerId,
            sellerId,
            symbol,
            price,
            quantity,
            executedAt
        );
    }
}
