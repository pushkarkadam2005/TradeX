package com.tradex.order.service;

import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.InfrastructureNotReadyException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
import com.tradex.order.engine.MatchingEngine;
import com.tradex.order.engine.OrderBook;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.model.Fill;
import com.tradex.order.model.MatchResult;
import com.tradex.order.repository.OrderRepository;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.risk.service.RiskService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.service.StockService;
import com.tradex.trade.service.TradeService;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final TradeService tradeService;
    private final MatchingEngine matchingEngine;
    private final OrderBookRegistry registry;
    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final RiskService riskService;
    private final TransactionService transactionService;
    private final DomainEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, StockService stockService,
                        TradeService tradeService, MatchingEngine matchingEngine,
                        OrderBookRegistry registry, AccountService accountService,
                        PortfolioService portfolioService, RiskService riskService,
                        TransactionService transactionService, DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.stockService = stockService;
        this.tradeService = tradeService;
        this.matchingEngine = matchingEngine;
        this.registry = registry;
        this.accountService = accountService;
        this.portfolioService = portfolioService;
        this.riskService = riskService;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    public record CreateOrderResult(OrderResponse orderResponse, boolean isDuplicate) {}

    @Transactional
    public CreateOrderResult createOrder(UUID userId, CreateOrderRequest request) {
        // Startup readiness gate check
        if (!registry.isInitialized()) {
            throw new InfrastructureNotReadyException("Order matching engine is not ready");
        }

        String clientOrderId = request.clientOrderId().trim();

        // 1. Idempotency Check (Application-level pre-check)
        Optional<Order> existingOrder = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId);
        if (existingOrder.isPresent()) {
            log.info("Duplicate order request detected for userId {} and clientOrderId {}. Returning existing order.", userId, clientOrderId);
            return new CreateOrderResult(OrderResponse.fromEntity(existingOrder.get()), true);
        }

        // 2. Validate Stock
        Stock stock = stockService.getStockEntityBySymbol(request.symbol());

        // 3. Pre-Trade Risk Evaluation (Rejects BEFORE fund reservation, order persistence, or OrderBook entry!)
        riskService.evaluateOrderRisk(userId, request, stock);

        // 4. Reserve Financial Funds / Share Assets before Order Persistence & Record Activity Log
        BigDecimal reservePrice = (request.orderType() == OrderType.LIMIT) ? request.limitPrice() : stock.getCurrentPrice();
        if (request.side() == OrderSide.BUY) {
            BigDecimal requiredFunds = BigDecimal.valueOf(request.quantity()).multiply(reservePrice);
            accountService.reserveFunds(userId, requiredFunds, null);
            transactionService.recordTransaction(
                userId, TransactionType.RESERVATION, TransactionStatus.COMPLETED, requiredFunds, "USD",
                "ORDER_RESERVATION", null, "tx-res-buy-" + clientOrderId, "Cash reserved for BUY order placement"
            );
        } else if (request.side() == OrderSide.SELL) {
            portfolioService.reserveShares(userId, stock.getId(), stock.getSymbol(), request.quantity());
            BigDecimal estimatedValue = BigDecimal.valueOf(request.quantity()).multiply(reservePrice);
            transactionService.recordTransaction(
                userId, TransactionType.RESERVATION, TransactionStatus.COMPLETED, estimatedValue, "USD",
                "ORDER_RESERVATION", null, "tx-res-sell-" + clientOrderId, "Shares reserved for SELL order placement"
            );
        }

        // 5. Assign Monotonic Sequence Number & Build Order Entity
        long sequence = registry.nextSequence();
        Order newOrder = new Order(
            userId,
            stock.getId(),
            stock.getSymbol(),
            request.side(),
            request.orderType(),
            request.quantity(),
            request.limitPrice(),
            clientOrderId,
            sequence
        );

        Order savedOrder;
        try {
            savedOrder = orderRepository.saveAndFlush(newOrder);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate clientOrderId submission caught by database unique constraint");
            Order duplicate = orderRepository.findByUserIdAndClientOrderId(userId, clientOrderId)
                .orElseThrow(() -> e);
            return new CreateOrderResult(OrderResponse.fromEntity(duplicate), true);
        }

        // Register transaction rollback synchronization to reconcile OrderBook if DB transaction fails
        final String affectedSymbol = savedOrder.getSymbol();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK || status == STATUS_UNKNOWN) {
                        log.warn("Transaction rolled back for symbol {}. Reconciling in-memory OrderBook with database.", affectedSymbol);
                        reconcileOrderBookWithDatabase(affectedSymbol);
                    }
                }
            });
        }

        // Publish ORDER_CREATED event
        eventPublisher.publish(DomainEvent.of(
            EventType.ORDER_CREATED, "ORDER", savedOrder.getId(),
            Map.of("userId", userId, "symbol", savedOrder.getSymbol(), "quantity", savedOrder.getQuantity(), "side", savedOrder.getSide().name())
        ));

        // 6. Execute Synchronous Matching Engine
        BookOrder bookOrder = BookOrder.fromEntity(savedOrder);
        MatchResult matchResult = matchingEngine.match(bookOrder);

        // 7. Process Fills & Update Database State & Settle Trades
        for (Fill fill : matchResult.fills()) {
            tradeService.recordFill(fill.toExecutionRequest());

            // Release price improvement excess reserved cash for BUY orders if executed price < limit price
            if (savedOrder.getSide() == OrderSide.BUY && savedOrder.getLimitPrice() != null && fill.price().compareTo(savedOrder.getLimitPrice()) < 0) {
                BigDecimal excessReserved = savedOrder.getLimitPrice().subtract(fill.price()).multiply(BigDecimal.valueOf(fill.quantity()));
                accountService.releaseFunds(userId, excessReserved, savedOrder.getId());
                transactionService.recordTransaction(
                    userId, TransactionType.RELEASE, TransactionStatus.COMPLETED, excessReserved, "USD",
                    "PRICE_IMPROVEMENT_RELEASE", savedOrder.getId(), "tx-rel-pi-" + fill.tradeExecutionId(), "Excess cash released due to price improvement fill"
                );
            }

            // Update resting order state in DB
            UUID restingOrderId = fill.buyOrderId().equals(savedOrder.getId()) ? fill.sellOrderId() : fill.buyOrderId();
            orderRepository.findById(restingOrderId).ifPresent(restingOrder -> {
                long newRemaining = Math.max(0, restingOrder.getRemainingQuantity() - fill.quantity());
                restingOrder.setRemainingQuantity(newRemaining);
                if (newRemaining == 0) {
                    restingOrder.setStatus(OrderStatus.FILLED);
                    eventPublisher.publish(DomainEvent.of(EventType.ORDER_FILLED, "ORDER", restingOrder.getId(),
                        Map.of("userId", restingOrder.getUserId(), "symbol", restingOrder.getSymbol(), "quantity", restingOrder.getQuantity(), "price", fill.price())));
                } else {
                    restingOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                    eventPublisher.publish(DomainEvent.of(EventType.ORDER_PARTIALLY_FILLED, "ORDER", restingOrder.getId(),
                        Map.of("userId", restingOrder.getUserId(), "symbol", restingOrder.getSymbol(), "quantity", fill.quantity(), "price", fill.price())));
                }
                orderRepository.save(restingOrder);

                if (restingOrder.getSide() == OrderSide.BUY && restingOrder.getLimitPrice() != null && fill.price().compareTo(restingOrder.getLimitPrice()) < 0) {
                    BigDecimal excessReserved = restingOrder.getLimitPrice().subtract(fill.price()).multiply(BigDecimal.valueOf(fill.quantity()));
                    accountService.releaseFunds(restingOrder.getUserId(), excessReserved, restingOrder.getId());
                    transactionService.recordTransaction(
                        restingOrder.getUserId(), TransactionType.RELEASE, TransactionStatus.COMPLETED, excessReserved, "USD",
                        "PRICE_IMPROVEMENT_RELEASE", restingOrder.getId(), "tx-rel-pi-rest-" + fill.tradeExecutionId(), "Excess cash released for resting BUY order price improvement"
                    );
                }
            });
        }

        // 8. Update incoming order state in DB & Release unexecuted funds/shares for MARKET orders
        savedOrder.setRemainingQuantity(bookOrder.getRemainingQuantity());
        if (bookOrder.getRemainingQuantity() == 0) {
            savedOrder.setStatus(OrderStatus.FILLED);
            eventPublisher.publish(DomainEvent.of(EventType.ORDER_FILLED, "ORDER", savedOrder.getId(),
                Map.of("userId", savedOrder.getUserId(), "symbol", savedOrder.getSymbol(), "quantity", savedOrder.getQuantity())));
        } else {
            if (savedOrder.getOrderType() == OrderType.MARKET) {
                // MARKET orders NEVER rest in OrderBook. Unexecuted remaining quantity is CANCELLED and unexecuted reservation released.
                savedOrder.setStatus(OrderStatus.CANCELLED);
                if (savedOrder.getSide() == OrderSide.BUY) {
                    BigDecimal unexecutedFunds = BigDecimal.valueOf(savedOrder.getRemainingQuantity()).multiply(reservePrice);
                    accountService.releaseFunds(userId, unexecutedFunds, savedOrder.getId());
                    transactionService.recordTransaction(
                        userId, TransactionType.RELEASE, TransactionStatus.COMPLETED, unexecutedFunds, "USD",
                        "ORDER_CANCEL", savedOrder.getId(), "tx-rel-mkt-buy-" + savedOrder.getId(), "Unexecuted MARKET BUY reservation released"
                    );
                } else {
                    portfolioService.releaseShares(userId, stock.getId(), savedOrder.getRemainingQuantity());
                    BigDecimal unexecutedValue = BigDecimal.valueOf(savedOrder.getRemainingQuantity()).multiply(reservePrice);
                    transactionService.recordTransaction(
                        userId, TransactionType.RELEASE, TransactionStatus.COMPLETED, unexecutedValue, "USD",
                        "ORDER_CANCEL", savedOrder.getId(), "tx-rel-mkt-sell-" + savedOrder.getId(), "Unexecuted MARKET SELL shares released"
                    );
                }
                eventPublisher.publish(DomainEvent.of(EventType.ORDER_CANCELLED, "ORDER", savedOrder.getId(),
                    Map.of("userId", savedOrder.getUserId(), "symbol", savedOrder.getSymbol(), "quantity", savedOrder.getRemainingQuantity())));
            } else if (bookOrder.getRemainingQuantity() < savedOrder.getQuantity()) {
                savedOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                eventPublisher.publish(DomainEvent.of(EventType.ORDER_PARTIALLY_FILLED, "ORDER", savedOrder.getId(),
                    Map.of("userId", savedOrder.getUserId(), "symbol", savedOrder.getSymbol(), "quantity", savedOrder.getQuantity() - savedOrder.getRemainingQuantity())));
            } else {
                savedOrder.setStatus(OrderStatus.OPEN);
            }
        }

        Order finalOrder = orderRepository.save(savedOrder);
        return new CreateOrderResult(OrderResponse.fromEntity(finalOrder), false);
    }

    public void reconcileOrderBookWithDatabase(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        registry.reinitializeBook(normalizedSymbol);

        List<Order> activeOrders = orderRepository.findByStatusInOrderByOrderSequenceAsc(
            List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
        );

        OrderBook book = registry.getOrderBook(normalizedSymbol);
        for (Order o : activeOrders) {
            if (o.getSymbol().equalsIgnoreCase(normalizedSymbol) && o.getOrderType() == OrderType.LIMIT) {
                book.addRestingOrder(BookOrder.fromEntity(o));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(UUID userId) {
        return orderRepository.findByUserId(userId).stream()
            .map(OrderResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("ACCESS_DENIED", "You are not authorized to view this order");
        }

        return OrderResponse.fromEntity(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("ACCESS_DENIED", "You are not authorized to cancel this order");
        }

        if (order.getStatus() == OrderStatus.FILLED) {
            throw new BusinessRuleViolationException("ORDER_ALREADY_FILLED", "Cannot cancel a completely filled order");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleViolationException("ORDER_ALREADY_CANCELLED", "Order is already cancelled");
        }

        // Evict order from in-memory order book
        matchingEngine.cancelOrder(order.getSymbol(), orderId);

        // Financial Release of locked cash or shares for unexecuted remaining quantity
        if (order.getRemainingQuantity() > 0) {
            if (order.getSide() == OrderSide.BUY && order.getLimitPrice() != null) {
                BigDecimal releaseAmount = BigDecimal.valueOf(order.getRemainingQuantity()).multiply(order.getLimitPrice());
                accountService.releaseFunds(userId, releaseAmount, orderId);
                transactionService.recordTransaction(
                    userId, TransactionType.RELEASE, TransactionStatus.COMPLETED, releaseAmount, "USD",
                    "ORDER_CANCEL", orderId, "tx-rel-cancel-buy-" + orderId, "Funds released from cancelled BUY order"
                );
            } else if (order.getSide() == OrderSide.SELL) {
                portfolioService.releaseShares(userId, order.getStockId(), order.getRemainingQuantity());
                BigDecimal releaseValue = (order.getLimitPrice() != null) ? BigDecimal.valueOf(order.getRemainingQuantity()).multiply(order.getLimitPrice()) : BigDecimal.ZERO;
                transactionService.recordTransaction(
                    userId, TransactionType.RELEASE, TransactionStatus.COMPLETED, releaseValue, "USD",
                    "ORDER_CANCEL", orderId, "tx-rel-cancel-sell-" + orderId, "Shares released from cancelled SELL order"
                );
            }
        }

        // Update order status in DB
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publish(DomainEvent.of(EventType.ORDER_CANCELLED, "ORDER", savedOrder.getId(),
            Map.of("userId", userId, "symbol", savedOrder.getSymbol(), "quantity", savedOrder.getRemainingQuantity())));

        return OrderResponse.fromEntity(savedOrder);
    }
}
