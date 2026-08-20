package com.tradex.order;

import com.tradex.order.engine.MatchingEngine;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.model.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyTest {

    private OrderBookRegistry registry;
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        registry = new OrderBookRegistry();
        matchingEngine = new MatchingEngine(registry);
    }

    @Test
    @DisplayName("Same-Symbol Concurrent Orders — 20 concurrent BUY/SELL orders on AAPL complete without state corruption or negative quantities")
    void sameSymbolConcurrentOrders() throws Exception {
        int orderCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);
        UUID stockId = UUID.randomUUID();

        List<MatchResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < orderCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    OrderSide side = (index % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                    BigDecimal price = new BigDecimal("185.0000");
                    long sequence = registry.nextSequence();
                    BookOrder order = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
                        side, OrderType.LIMIT, price, 10, 10, sequence, Instant.now());

                    MatchResult result = matchingEngine.match(order);
                    results.add(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(results).hasSize(orderCount);

        // Verify remaining quantities are strictly >= 0 and total fills match executed volume
        long totalFillQty = results.stream().flatMap(r -> r.fills().stream()).mapToLong(f -> f.quantity()).sum();
        assertThat(totalFillQty).isEqualTo(100); // 10 BUYs of 10 shares matched by 10 SELLs of 10 shares = 100 total trade quantity
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Different-Symbol Concurrent Orders — AAPL and MSFT matching execute concurrently on separate per-symbol locks")
    void differentSymbolConcurrentOrders() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                latch.await();
                BookOrder aaplBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "AAPL",
                    OrderSide.BUY, OrderType.LIMIT, new BigDecimal("185.0000"), 50, 50, 1L, Instant.now());
                matchingEngine.match(aaplBuy);
            } catch (Exception ignored) {}
        });

        executor.submit(() -> {
            try {
                latch.await();
                BookOrder msftBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "MSFT",
                    OrderSide.BUY, OrderType.LIMIT, new BigDecimal("420.0000"), 50, 50, 2L, Instant.now());
                matchingEngine.match(msftBuy);
            } catch (Exception ignored) {}
        });

        latch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(1);
        assertThat(registry.getOrderBook("MSFT").getBuyOrderCount()).isEqualTo(1);
    }
}
