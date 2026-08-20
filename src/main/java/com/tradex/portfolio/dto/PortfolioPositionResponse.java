package com.tradex.portfolio.dto;

import com.tradex.portfolio.entity.PortfolioPosition;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioPositionResponse(
    UUID id,
    UUID userId,
    UUID stockId,
    String symbol,
    long quantity,
    long lockedQuantity,
    long availableQuantity,
    BigDecimal averageBuyPrice
) {
    public static PortfolioPositionResponse fromEntity(PortfolioPosition position) {
        return new PortfolioPositionResponse(
            position.getId(),
            position.getUserId(),
            position.getStockId(),
            position.getSymbol(),
            position.getQuantity(),
            position.getLockedQuantity(),
            position.getAvailableQuantity(),
            position.getAverageBuyPrice()
        );
    }
}
