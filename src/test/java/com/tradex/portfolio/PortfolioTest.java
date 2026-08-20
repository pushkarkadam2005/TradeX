package com.tradex.portfolio;

import com.tradex.portfolio.entity.PortfolioPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTest {

    @Test
    @DisplayName("Weighted Average Buy Price Calculation — Correctly updates average buy price across multiple buy fills")
    void weightedAverageBuyPriceCalculation() {
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        PortfolioPosition position = new PortfolioPosition(userId, stockId, "AAPL");

        // First buy: 100 shares @ $150.00
        position.addSharesOnBuy(100, new BigDecimal("150.0000"));
        assertThat(position.getQuantity()).isEqualTo(100);
        assertThat(position.getAverageBuyPrice()).isEqualByComparingTo("150.0000");

        // Second buy: 100 shares @ $200.00 -> New average buy price should be $175.00
        position.addSharesOnBuy(100, new BigDecimal("200.0000"));
        assertThat(position.getQuantity()).isEqualTo(200);
        assertThat(position.getAverageBuyPrice()).isEqualByComparingTo("175.0000");
    }

    @Test
    @DisplayName("Share Reservation & Release — Moves available shares to locked and releases properly")
    void shareReservationAndRelease() {
        PortfolioPosition position = new PortfolioPosition(UUID.randomUUID(), UUID.randomUUID(), "AAPL");
        position.setQuantity(50);

        position.reserveShares(20);
        assertThat(position.getAvailableQuantity()).isEqualTo(30);
        assertThat(position.getLockedQuantity()).isEqualTo(20);

        position.releaseShares(10);
        assertThat(position.getAvailableQuantity()).isEqualTo(40);
        assertThat(position.getLockedQuantity()).isEqualTo(10);
    }
}
