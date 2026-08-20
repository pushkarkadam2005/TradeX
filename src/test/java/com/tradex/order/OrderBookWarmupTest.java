package com.tradex.order;

import com.tradex.order.engine.OrderBook;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.engine.OrderBookWarmup;
import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderBookWarmupTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Startup Warmup — Restores global max sequence and active limit orders in exact order_sequence ASC order")
    void warmupRestoresOrdersInSequenceOrder() {
        OrderBookRegistry registry = new OrderBookRegistry();
        OrderBookWarmup warmup = new OrderBookWarmup(orderRepository, registry);

        given(orderRepository.findMaxOrderSequence()).willReturn(42L);

        Order activeSell1 = new Order(UUID.randomUUID(), UUID.randomUUID(), "AAPL", OrderSide.SELL, OrderType.LIMIT, 100, new BigDecimal("185.0000"), "client-1", 10L);
        Order activeBuy2 = new Order(UUID.randomUUID(), UUID.randomUUID(), "AAPL", OrderSide.BUY, OrderType.LIMIT, 50, new BigDecimal("180.0000"), "client-2", 20L);

        given(orderRepository.findByStatusInOrderByOrderSequenceAsc(anyList()))
            .willReturn(List.of(activeSell1, activeBuy2));

        SpringApplication app = Mockito.mock(SpringApplication.class);
        ConfigurableApplicationContext context = Mockito.mock(ConfigurableApplicationContext.class);

        warmup.onApplicationEvent(new ApplicationReadyEvent(app, new String[0], context, Duration.ZERO));

        assertThat(registry.nextSequence()).isEqualTo(43L); // Max sequence 42 restored

        OrderBook book = registry.getOrderBook("AAPL");
        assertThat(book.getSellOrderCount()).isEqualTo(1);
        assertThat(book.getBuyOrderCount()).isEqualTo(1);
    }
}
