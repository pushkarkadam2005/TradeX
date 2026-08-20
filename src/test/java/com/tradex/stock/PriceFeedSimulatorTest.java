package com.tradex.stock;

import com.tradex.stock.simulator.PriceFeedSimulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceFeedSimulatorTest {

    @Test
    @DisplayName("Simulator price tick stays strictly positive and within bounded range")
    void simulatorPriceBounds() {
        PriceFeedSimulator simulator = new PriceFeedSimulator(null);
        BigDecimal initialPrice = new BigDecimal("100.0000");

        for (int i = 0; i < 50; i++) {
            BigDecimal nextPrice = simulator.calculateNextPrice(initialPrice);
            assertThat(nextPrice).isGreaterThan(BigDecimal.ZERO);
            // Delta must not exceed 0.5% (max 0.50 for 100.00)
            BigDecimal delta = nextPrice.subtract(initialPrice).abs();
            assertThat(delta).isLessThanOrEqualTo(new BigDecimal("0.5500"));
        }
    }
}
