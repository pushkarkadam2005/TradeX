package com.tradex.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockPriceResponse(
    String symbol,
    BigDecimal currentPrice,
    String source,
    Instant timestamp
) {
    public StockPriceResponse(String symbol, BigDecimal currentPrice, String source) {
        this(symbol, currentPrice, source, Instant.now());
    }
}
