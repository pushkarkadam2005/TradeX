package com.tradex.order;

import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDomainTest {

    @Test
    @DisplayName("Order entity initializes fields, uppercase symbol, and OPEN status")
    void orderEntityInitialization() {
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("185.5000");

        Order order = new Order(userId, stockId, "aapl", OrderSide.BUY, OrderType.LIMIT,
            100, price, "client-ord-001", 1L);

        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getStockId()).isEqualTo(stockId);
        assertThat(order.getSymbol()).isEqualTo("AAPL");
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(order.getQuantity()).isEqualTo(100);
        assertThat(order.getRemainingQuantity()).isEqualTo(100);
        assertThat(order.getLimitPrice()).isEqualByComparingTo(price);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.getClientOrderId()).isEqualTo("client-ord-001");
        assertThat(order.getOrderSequence()).isEqualTo(1L);
    }
}
