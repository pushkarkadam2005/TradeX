package com.tradex.order.entity;

import com.tradex.common.entity.BaseEntity;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    @Column(name = "limit_price", precision = 19, scale = 4)
    private BigDecimal limitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "client_order_id", nullable = false, length = 64)
    private String clientOrderId;

    @Column(name = "order_sequence", nullable = false)
    private long orderSequence;

    public Order() {
    }

    public Order(UUID userId, UUID stockId, String symbol, OrderSide side, OrderType orderType,
                 long quantity, BigDecimal limitPrice, String clientOrderId, long orderSequence) {
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.limitPrice = limitPrice;
        this.status = OrderStatus.OPEN;
        this.clientOrderId = clientOrderId;
        this.orderSequence = orderSequence;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getStockId() {
        return stockId;
    }

    public void setStockId(UUID stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(long remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public long getOrderSequence() {
        return orderSequence;
    }

    public void setOrderSequence(long orderSequence) {
        this.orderSequence = orderSequence;
    }
}
