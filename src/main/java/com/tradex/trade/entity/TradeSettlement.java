package com.tradex.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_settlements")
public class TradeSettlement {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "execution_id", nullable = false, unique = true, length = 36)
    private String executionId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt = Instant.now();

    public TradeSettlement() {}

    public TradeSettlement(String executionId, UUID buyerId, UUID sellerId, UUID stockId,
                           String symbol, BigDecimal price, long quantity) {
        this.id = UUID.randomUUID();
        this.executionId = executionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.price = price != null ? price.setScale(4) : BigDecimal.ZERO.setScale(4);
        this.quantity = quantity;
        this.settledAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getStockId() {
        return stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
