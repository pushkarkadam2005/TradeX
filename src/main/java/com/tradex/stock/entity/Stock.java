package com.tradex.stock.entity;

import com.tradex.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "stocks")
public class Stock extends BaseEntity {

    @Column(name = "symbol", nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "previous_close", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "market_status", nullable = false, length = 20)
    private String marketStatus = "OPEN";

    @Column(name = "tradable", nullable = false)
    private boolean tradable = true;

    public Stock() {
    }

    public Stock(String symbol, String companyName, BigDecimal currentPrice, BigDecimal previousClose, String sector) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.companyName = companyName;
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.sector = sector;
        this.marketStatus = "OPEN";
        this.tradable = true;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getMarketStatus() {
        return marketStatus;
    }

    public void setMarketStatus(String marketStatus) {
        this.marketStatus = marketStatus;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }
}
