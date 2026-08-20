package com.tradex.order;

import com.tradex.order.engine.OrderBook;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.repository.OrderRepository;
import com.tradex.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionRollbackOrderBookRecoveryTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderBookRegistry registry;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
    }

    @Test
    @DisplayName("Transaction Rollback OrderBook Reconciliation — In-memory OrderBook is reconciled from DB upon rollback")
    void transactionRollbackReconcilesOrderBook() {
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();

        // 1. Save an OPEN limit order directly into PostgreSQL
        Order dbOrder = new Order(userId, stockId, "AAPL", OrderSide.SELL, OrderType.LIMIT, 100, new BigDecimal("185.0000"), "client-recovery-1", 1L);
        dbOrder.setStatus(OrderStatus.OPEN);
        orderRepository.saveAndFlush(dbOrder);

        // 2. Mutate OrderBook manually (simulate an uncommitted in-memory mutation)
        OrderBook book = registry.getOrderBook("AAPL");
        book.cancelOrder(dbOrder.getId()); // In-memory book now has 0 orders, while DB has 1 OPEN order!

        assertThat(book.getSellOrderCount()).isEqualTo(0);

        // 3. Trigger reconciliation
        orderService.reconcileOrderBookWithDatabase("AAPL");

        // 4. Verify in-memory OrderBook state matches PostgreSQL perfectly
        OrderBook reconciledBook = registry.getOrderBook("AAPL");
        assertThat(reconciledBook.getSellOrderCount()).isEqualTo(1);
    }
}
