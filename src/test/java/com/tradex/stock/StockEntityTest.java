package com.tradex.stock;

import com.tradex.stock.entity.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockEntityTest {

    @Test
    @DisplayName("Stock entity initializes fields, normalizes symbol uppercase, and supports optimistic locking version")
    void stockEntityMapping() {
        BigDecimal price = new BigDecimal("185.5000");
        BigDecimal prevClose = new BigDecimal("184.2500");

        Stock stock = new Stock("aapl", "Apple Inc.", price, prevClose, "Technology");
        stock.setVersion(0L);

        assertThat(stock.getSymbol()).isEqualTo("AAPL"); // Normalized to uppercase
        assertThat(stock.getCompanyName()).isEqualTo("Apple Inc.");
        assertThat(stock.getCurrentPrice()).isEqualByComparingTo(price);
        assertThat(stock.getPreviousClose()).isEqualByComparingTo(prevClose);
        assertThat(stock.getSector()).isEqualTo("Technology");
        assertThat(stock.getMarketStatus()).isEqualTo("OPEN");
        assertThat(stock.isTradable()).isTrue();
        assertThat(stock.getVersion()).isEqualTo(0L);
    }
}
