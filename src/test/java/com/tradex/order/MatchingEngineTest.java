package com.tradex.order;

import com.tradex.order.engine.MatchingEngine;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.model.Fill;
import com.tradex.order.model.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private OrderBookRegistry registry;
    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        registry = new OrderBookRegistry();
        matchingEngine = new MatchingEngine(registry);
    }

    @Test
    @DisplayName("Exact Fill — Single resting SELL limit order matched by incoming BUY limit order")
    void exactFillMatching() {
        UUID stockId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // 1. Resting SELL limit order: 100 shares @ $185.00
        BookOrder restingSell = new BookOrder(UUID.randomUUID(), user1, stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 100, 100, 1L, Instant.now());
        matchingEngine.match(restingSell);

        // 2. Incoming BUY limit order: 100 shares @ $185.00
        BookOrder incomingBuy = new BookOrder(UUID.randomUUID(), user2, stockId, "AAPL",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("185.0000"), 100, 100, 2L, Instant.now());
        MatchResult result = matchingEngine.match(incomingBuy);

        assertThat(result.fullyMatched()).isTrue();
        assertThat(result.incomingOrder().getRemainingQuantity()).isEqualTo(0);
        assertThat(result.fills()).hasSize(1);

        Fill fill = result.fills().get(0);
        assertThat(fill.quantity()).isEqualTo(100);
        assertThat(fill.price()).isEqualByComparingTo("185.0000");

        // Verify deterministic executionId format
        String expectedIdStr = incomingBuy.getOrderId() + ":" + restingSell.getOrderId() + ":185.0000:100";
        String expectedDeterministicId = UUID.nameUUIDFromBytes(expectedIdStr.getBytes(StandardCharsets.UTF_8)).toString();
        assertThat(fill.tradeExecutionId()).isEqualTo(expectedDeterministicId);
    }

    @Test
    @DisplayName("Partial Fill — Incoming BUY 100 matches resting SELL 40, leaving BUY 60 resting in book")
    void partialFillMatching() {
        UUID stockId = UUID.randomUUID();

        BookOrder restingSell = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 40, 40, 1L, Instant.now());
        matchingEngine.match(restingSell);

        BookOrder incomingBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("185.0000"), 100, 100, 2L, Instant.now());
        MatchResult result = matchingEngine.match(incomingBuy);

        assertThat(result.fullyMatched()).isFalse();
        assertThat(result.incomingOrder().getRemainingQuantity()).isEqualTo(60);
        assertThat(result.fills()).hasSize(1);
        assertThat(result.fills().get(0).quantity()).isEqualTo(40);
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(1);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Price-Time Priority — Multiple resting SELL orders matched in order of lowest price, then earliest sequence")
    void priceTimePriorityMatching() {
        UUID stockId = UUID.randomUUID();

        // Sell order 1: 50 shares @ $186.00 (Seq 1)
        BookOrder sell1 = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("186.0000"), 50, 50, 1L, Instant.now());
        // Sell order 2: 50 shares @ $185.00 (Seq 2) -> Better price, should match first!
        BookOrder sell2 = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 50, 50, 2L, Instant.now());
        // Sell order 3: 50 shares @ $185.00 (Seq 3) -> Same price as sell2, later sequence, matches after sell2
        BookOrder sell3 = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 50, 50, 3L, Instant.now());

        matchingEngine.match(sell1);
        matchingEngine.match(sell2);
        matchingEngine.match(sell3);

        // Incoming BUY 75 @ $186.00 -> Should match all 50 @ $185.00 (sell2) and 25 @ $185.00 (sell3)
        BookOrder incomingBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("186.0000"), 75, 75, 4L, Instant.now());

        MatchResult result = matchingEngine.match(incomingBuy);

        assertThat(result.fills()).hasSize(2);
        assertThat(result.fills().get(0).sellOrderId()).isEqualTo(sell2.getOrderId());
        assertThat(result.fills().get(0).quantity()).isEqualTo(50);
        assertThat(result.fills().get(1).sellOrderId()).isEqualTo(sell3.getOrderId());
        assertThat(result.fills().get(1).quantity()).isEqualTo(25);
    }

    @Test
    @DisplayName("No-Crossing Orders — Limit BUY below resting SELL price does not match and rests in book")
    void noCrossingOrdersRestInBook() {
        UUID stockId = UUID.randomUUID();

        // Resting SELL @ $185.00
        BookOrder restingSell = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 100, 100, 1L, Instant.now());
        matchingEngine.match(restingSell);

        // Incoming BUY @ $180.00 (No crossing!)
        BookOrder incomingBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.BUY, OrderType.LIMIT, new BigDecimal("180.0000"), 50, 50, 2L, Instant.now());
        MatchResult result = matchingEngine.match(incomingBuy);

        assertThat(result.fills()).isEmpty();
        assertThat(result.fullyMatched()).isFalse();
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(1);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Market Order — Consumes available resting liquidity at best price")
    void marketOrderMatching() {
        UUID stockId = UUID.randomUUID();

        BookOrder restingSell = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.SELL, OrderType.LIMIT, new BigDecimal("185.0000"), 50, 50, 1L, Instant.now());
        matchingEngine.match(restingSell);

        // Incoming MARKET BUY for 30 shares
        BookOrder marketBuy = new BookOrder(UUID.randomUUID(), UUID.randomUUID(), stockId, "AAPL",
            OrderSide.BUY, OrderType.MARKET, null, 30, 30, 2L, Instant.now());

        MatchResult result = matchingEngine.match(marketBuy);

        assertThat(result.fullyMatched()).isTrue();
        assertThat(result.fills()).hasSize(1);
        assertThat(result.fills().get(0).quantity()).isEqualTo(30);
        assertThat(result.fills().get(0).price()).isEqualByComparingTo("185.0000");
    }
}
