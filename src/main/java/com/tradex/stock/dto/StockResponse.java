package com.tradex.stock.dto;

import com.tradex.stock.entity.Stock;

import java.math.BigDecimal;
import java.util.UUID;

public record StockResponse(
    UUID id,
    String symbol,
    String companyName,
    BigDecimal currentPrice,
    BigDecimal previousClose,
    String sector,
    String marketStatus,
    boolean tradable
) {
    public static StockResponse fromEntity(Stock stock) {
        return new StockResponse(
            stock.getId(),
            stock.getSymbol(),
            stock.getCompanyName(),
            stock.getCurrentPrice(),
            stock.getPreviousClose(),
            stock.getSector(),
            stock.getMarketStatus(),
            stock.isTradable()
        );
    }
}
