package com.tradex.trade.service;

import com.tradex.account.service.AccountService;
import com.tradex.common.dto.FillExecutionRequest;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.trade.entity.Trade;
import com.tradex.trade.entity.TradeSettlement;
import com.tradex.trade.repository.TradeRepository;
import com.tradex.trade.repository.TradeSettlementRepository;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final TradeRepository tradeRepository;
    private final TradeSettlementRepository settlementRepository;
    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final DomainEventPublisher eventPublisher;

    public TradeService(TradeRepository tradeRepository,
                        TradeSettlementRepository settlementRepository,
                        AccountService accountService,
                        PortfolioService portfolioService,
                        TransactionService transactionService,
                        DomainEventPublisher eventPublisher) {
        this.tradeRepository = tradeRepository;
        this.settlementRepository = settlementRepository;
        this.accountService = accountService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Optional<Trade> recordFill(FillExecutionRequest request) {
        if (tradeRepository.existsByExecutionId(request.executionId())) {
            log.warn("Trade with executionId {} already recorded. Skipping duplicate.", request.executionId());
            return tradeRepository.findByExecutionId(request.executionId());
        }

        Trade trade = new Trade(
            request.executionId(),
            request.buyOrderId(),
            request.sellOrderId(),
            request.stockId(),
            request.buyerId(),
            request.sellerId(),
            request.symbol(),
            request.price(),
            request.quantity(),
            request.executedAt()
        );

        Trade savedTrade;
        try {
            savedTrade = tradeRepository.save(trade);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate executionId {} caught by unique DB constraint.", request.executionId());
            return tradeRepository.findByExecutionId(request.executionId());
        }

        eventPublisher.publish(DomainEvent.of(EventType.TRADE_EXECUTED, "TRADE", savedTrade.getId(),
            Map.of("executionId", request.executionId(), "symbol", request.symbol(), "quantity", request.quantity(), "price", request.price())));

        // Execute Financial Settlement
        settleTrade(request);

        return Optional.of(savedTrade);
    }

    @Transactional
    public void settleTrade(FillExecutionRequest request) {
        if (settlementRepository.existsByExecutionId(request.executionId())) {
            log.info("Trade settlement for executionId {} already processed. Idempotent skip.", request.executionId());
            return;
        }

        BigDecimal tradeValue = BigDecimal.valueOf(request.quantity()).multiply(request.price());

        // 1. Buyer Settlement: Deduct locked cash, add shares & update average buy price
        accountService.deductLockedFunds(
            request.buyerId(),
            tradeValue,
            request.buyOrderId(),
            "Deduct locked funds for BUY fill of " + request.quantity() + " shares of " + request.symbol() + " @ $" + request.price()
        );
        portfolioService.addSharesOnBuy(
            request.buyerId(),
            request.stockId(),
            request.symbol(),
            request.quantity(),
            request.price()
        );

        // Record Buyer Transaction Activity Record
        transactionService.recordTransaction(
            request.buyerId(),
            TransactionType.BUY_SETTLEMENT,
            TransactionStatus.COMPLETED,
            tradeValue,
            "USD",
            "SETTLEMENT",
            request.buyOrderId(),
            "tx-settle-buy-" + request.executionId(),
            "Settlement for BUY fill of " + request.quantity() + " shares of " + request.symbol() + " @ $" + request.price()
        );

        eventPublisher.publish(DomainEvent.of(EventType.BUY_SETTLEMENT_COMPLETED, "SETTLEMENT", request.buyOrderId(),
            Map.of("userId", request.buyerId(), "symbol", request.symbol(), "quantity", request.quantity(), "price", request.price())));

        // 2. Seller Settlement: Deduct locked shares, credit available cash
        portfolioService.deductLockedShares(
            request.sellerId(),
            request.stockId(),
            request.quantity()
        );
        accountService.creditAvailableFunds(
            request.sellerId(),
            tradeValue,
            request.sellOrderId(),
            "Credit available funds for SELL fill of " + request.quantity() + " shares of " + request.symbol() + " @ $" + request.price()
        );

        // Record Seller Transaction Activity Record
        transactionService.recordTransaction(
            request.sellerId(),
            TransactionType.SELL_SETTLEMENT,
            TransactionStatus.COMPLETED,
            tradeValue,
            "USD",
            "SETTLEMENT",
            request.sellOrderId(),
            "tx-settle-sell-" + request.executionId(),
            "Settlement for SELL fill of " + request.quantity() + " shares of " + request.symbol() + " @ $" + request.price()
        );

        eventPublisher.publish(DomainEvent.of(EventType.SELL_SETTLEMENT_COMPLETED, "SETTLEMENT", request.sellOrderId(),
            Map.of("userId", request.sellerId(), "symbol", request.symbol(), "quantity", request.quantity(), "price", request.price())));

        // 3. Save TradeSettlement record for idempotency
        TradeSettlement settlement = new TradeSettlement(
            request.executionId(),
            request.buyerId(),
            request.sellerId(),
            request.stockId(),
            request.symbol(),
            request.price(),
            request.quantity()
        );

        try {
            settlementRepository.save(settlement);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate settlement executionId {} caught by unique constraint.", request.executionId());
        }
    }

    @Transactional(readOnly = true)
    public List<Trade> getTradesBySymbol(String symbol) {
        return tradeRepository.findBySymbol(symbol.toUpperCase().trim());
    }
}
