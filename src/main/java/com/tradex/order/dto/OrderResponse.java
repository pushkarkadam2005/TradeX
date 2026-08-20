package com.tradex.order.dto;

import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    UUID userId,
    UUID stockId,
    String symbol,
    OrderSide side,
    OrderType orderType,
    long quantity,
    long remainingQuantity,
    BigDecimal limitPrice,
    OrderStatus status,
    String clientOrderId,
    long orderSequence,
    Instant createdAt
) {
    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getStockId(),
            order.getSymbol(),
            order.getSide(),
            order.getOrderType(),
            order.getQuantity(),
            order.getRemainingQuantity(),
            order.getLimitPrice(),
            order.getStatus(),
            order.getClientOrderId(),
            order.getOrderSequence(),
            order.getCreatedAt()
        );
    }
}
