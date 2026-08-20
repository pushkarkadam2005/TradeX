package com.tradex.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "tradex.risk")
public class RiskProperties {

    private BigDecimal maxOrderValue = new BigDecimal("100000.0000");
    private long maxOrderQuantity = 10000L;
    private BigDecimal maxDailyTradingValue = new BigDecimal("500000.0000");

    public BigDecimal getMaxOrderValue() {
        return maxOrderValue;
    }

    public void setMaxOrderValue(BigDecimal maxOrderValue) {
        this.maxOrderValue = maxOrderValue;
    }

    public long getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public void setMaxOrderQuantity(long maxOrderQuantity) {
        this.maxOrderQuantity = maxOrderQuantity;
    }

    public BigDecimal getMaxDailyTradingValue() {
        return maxDailyTradingValue;
    }

    public void setMaxDailyTradingValue(BigDecimal maxDailyTradingValue) {
        this.maxDailyTradingValue = maxDailyTradingValue;
    }
}
