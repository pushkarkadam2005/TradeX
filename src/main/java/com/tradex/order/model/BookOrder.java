package com.tradex.order.model;

import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mutable in-memory order book state object.
 * Mutated only under per-symbol ReentrantLock in OrderBook.
 */
public class BookOrder {

    private final UUID orderId;
    private final UUID userId;
    private final UUID stockId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType orderType;
    private final BigDecimal price;
    private final long quantity;
    private long remainingQuantity;
    private final long sequence;
    private final Instant createdAt;

    public BookOrder(UUID orderId, UUID userId, UUID stockId, String symbol, OrderSide side,
                     OrderType orderType, BigDecimal price, long quantity, long remainingQuantity,
                     long sequence, Instant createdAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.sequence = sequence;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static BookOrder fromEntity(Order order) {
        return new BookOrder(
            order.getId(),
            order.getUserId(),
            order.getStockId(),
            order.getSymbol(),
            order.getSide(),
            order.getOrderType(),
            order.getLimitPrice(),
            order.getQuantity(),
            order.getRemainingQuantity(),
            order.getOrderSequence(),
            order.getCreatedAt()
        );
    }

    public void decrementRemaining(long amount) {
        if (amount < 0 || amount > this.remainingQuantity) {
            throw new IllegalArgumentException("Invalid decrement amount: " + amount + ", remaining: " + remainingQuantity);
        }
        this.remainingQuantity -= amount;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getStockId() {
        return stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public long getSequence() {
        return sequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
