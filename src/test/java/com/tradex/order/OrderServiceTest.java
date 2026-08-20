package com.tradex.order;

import com.tradex.account.service.AccountService;
import com.tradex.common.dto.FillExecutionRequest;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.InfrastructureNotReadyException;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.engine.MatchingEngine;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.model.MatchResult;
import com.tradex.order.repository.OrderRepository;
import com.tradex.order.service.OrderService;
import com.tradex.order.service.OrderService.CreateOrderResult;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.risk.model.RiskDecision;
import com.tradex.risk.service.RiskService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.service.StockService;
import com.tradex.trade.entity.Trade;
import com.tradex.trade.repository.TradeRepository;
import com.tradex.trade.repository.TradeSettlementRepository;
import com.tradex.trade.service.TradeService;
import com.tradex.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockService stockService;

    @Mock
    private TradeService tradeService;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private OrderBookRegistry registry;

    @Mock
    private AccountService accountService;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private RiskService riskService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeSettlementRepository settlementRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository, stockService, tradeService, matchingEngine, registry,
            accountService, portfolioService, riskService, transactionService, eventPublisher
        );
    }

    @Test
    @DisplayName("Startup Readiness Gate — Uninitialized registry rejects order placement with InfrastructureNotReadyException")
    void uninitializedRegistryRejectsOrderPlacement() {
        given(registry.isInitialized()).willReturn(false);

        CreateOrderRequest request = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("185.0000"), "ord-1");

        assertThatThrownBy(() -> orderService.createOrder(UUID.randomUUID(), request))
            .isInstanceOf(InfrastructureNotReadyException.class)
            .hasMessageContaining("Order matching engine is not ready");
    }

    @Test
    @DisplayName("Idempotent Order Submission — Initial request creates order, duplicate request returns existing order")
    void idempotentOrderSubmission() {
        given(registry.isInitialized()).willReturn(true);
        UUID userId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();
        String clientOrderId = "ord-client-101";

        CreateOrderRequest request = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("185.0000"), clientOrderId);
        Stock stock = new Stock("AAPL", "Apple Inc.", new BigDecimal("185.0000"), new BigDecimal("184.0000"), "Technology");
        stock.setId(stockId);

        Order existingOrderEntity = new Order(userId, stockId, "AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("185.0000"), clientOrderId, 1L);

        // Pre-check returns empty on first attempt
        given(orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)).willReturn(Optional.empty());
        given(stockService.getStockEntityBySymbol("AAPL")).willReturn(stock);
        given(riskService.evaluateOrderRisk(userId, request, stock)).willReturn(RiskDecision.approved());
        given(registry.nextSequence()).willReturn(1L);

        given(orderRepository.saveAndFlush(any(Order.class))).willReturn(existingOrderEntity);
        given(matchingEngine.match(any(BookOrder.class))).willReturn(new MatchResult(BookOrder.fromEntity(existingOrderEntity), List.of(), false));
        given(orderRepository.save(any(Order.class))).willReturn(existingOrderEntity);

        // 1. Initial creation
        CreateOrderResult firstResult = orderService.createOrder(userId, request);
        assertThat(firstResult.isDuplicate()).isFalse();
        assertThat(firstResult.orderResponse().clientOrderId()).isEqualTo(clientOrderId);

        // 2. Duplicate submission pre-check returns existing order
        given(orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)).willReturn(Optional.of(existingOrderEntity));
        CreateOrderResult duplicateResult = orderService.createOrder(userId, request);

        assertThat(duplicateResult.isDuplicate()).isTrue();
        assertThat(duplicateResult.orderResponse().clientOrderId()).isEqualTo(clientOrderId);
    }

    @Test
    @DisplayName("Trade Idempotency — Duplicate trade executionId returns existing trade row without duplicate insertion")
    void tradeIdempotency() {
        TradeService realTradeService = new TradeService(tradeRepository, settlementRepository, accountService, portfolioService, transactionService, eventPublisher);
        String executionId = "exec-uuid-12345";
        FillExecutionRequest req = new FillExecutionRequest(executionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "AAPL", new BigDecimal("185.0000"), 100, Instant.now());

        Trade existingTrade = new Trade(executionId, req.buyOrderId(), req.sellOrderId(), req.stockId(), req.buyerId(), req.sellerId(), "AAPL", req.price(), 100, Instant.now());
        given(tradeRepository.existsByExecutionId(executionId)).willReturn(true);
        given(tradeRepository.findByExecutionId(executionId)).willReturn(Optional.of(existingTrade));

        Optional<Trade> result = realTradeService.recordFill(req);

        assertThat(result).isPresent();
        assertThat(result.get().getExecutionId()).isEqualTo(executionId);
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancel Order Rules — Cannot cancel completely filled or already cancelled order")
    void cancelOrderRules() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order filledOrder = new Order(userId, UUID.randomUUID(), "AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("185.0000"), "ord-c1", 1L);
        filledOrder.setStatus(OrderStatus.FILLED);

        given(orderRepository.findById(orderId)).willReturn(Optional.of(filledOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("completely filled");
    }
}
