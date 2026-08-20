package com.tradex.risk;

import com.tradex.risk.config.RiskProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPropertiesTest {

    @Test
    @DisplayName("RiskProperties Initialization — Configured defaults use BigDecimal for monetary limits and long for quantity")
    void riskPropertiesDefaults() {
        RiskProperties props = new RiskProperties();

        assertThat(props.getMaxOrderValue()).isEqualByComparingTo("100000.0000");
        assertThat(props.getMaxOrderQuantity()).isEqualTo(10000L);
        assertThat(props.getMaxDailyTradingValue()).isEqualByComparingTo("500000.0000");
    }
}
