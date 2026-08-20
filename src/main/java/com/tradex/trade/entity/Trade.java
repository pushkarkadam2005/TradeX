package com.tradex.trade.entity;

import com.tradex.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade extends BaseEntity {

    @Column(name = "execution_id", nullable = false, unique = true, length = 36)
    private String executionId;

    @Column(name = "buy_order_id", nullable = false)
    private UUID buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private UUID sellOrderId;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    public Trade() {
    }

    public Trade(String executionId, UUID buyOrderId, UUID sellOrderId, UUID stockId,
                 UUID buyerId, UUID sellerId, String symbol, BigDecimal price, long quantity, Instant executedAt) {
        this.executionId = executionId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.stockId = stockId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt != null ? executedAt : Instant.now();
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public UUID getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(UUID buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public UUID getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(UUID sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public UUID getStockId() {
        return stockId;
    }

    public void setStockId(UUID stockId) {
        this.stockId = stockId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(UUID buyerId) {
        this.buyerId = buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public void setSellerId(UUID sellerId) {
        this.sellerId = sellerId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}
