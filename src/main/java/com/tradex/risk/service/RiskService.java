package com.tradex.risk.service;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.compliance.model.ComplianceDecision;
import com.tradex.compliance.service.ComplianceService;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.risk.config.RiskProperties;
import com.tradex.risk.model.RiskDecision;
import com.tradex.stock.entity.Stock;
import com.tradex.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);

    private final RiskProperties riskProperties;
    private final AccountService accountService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final DomainEventPublisher eventPublisher;
    private final ComplianceService complianceService;

    @Autowired
    public RiskService(RiskProperties riskProperties, AccountService accountService,
                       PortfolioService portfolioService, TransactionService transactionService,
                       DomainEventPublisher eventPublisher, @Nullable ComplianceService complianceService) {
        this.riskProperties = riskProperties;
        this.accountService = accountService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
        this.complianceService = complianceService;
    }

    public RiskService(RiskProperties riskProperties, AccountService accountService,
                       PortfolioService portfolioService, TransactionService transactionService,
                       DomainEventPublisher eventPublisher) {
        this(riskProperties, accountService, portfolioService, transactionService, eventPublisher, null);
    }

    @Transactional(readOnly = true)
    public RiskDecision evaluateOrderRisk(UUID userId, CreateOrderRequest request, Stock stock) {
        try {
            return doEvaluate(userId, request, stock);
        } catch (BusinessRuleViolationException e) {
            eventPublisher.publish(DomainEvent.of(EventType.RISK_ORDER_REJECTED, "RISK", userId,
                Map.of("userId", userId, "symbol", stock.getSymbol(), "reason", e.getMessage(), "code", e.getErrorCode())));
            throw e;
        }
    }

    private RiskDecision doEvaluate(UUID userId, CreateOrderRequest request, Stock stock) {
        // 0. Pre-Trade Compliance & KYC/AML Check
        if (complianceService != null) {
            ComplianceDecision complianceDecision = complianceService.checkTradingEligibility(userId);
            if (!complianceDecision.isApproved()) {
                throw new BusinessRuleViolationException(complianceDecision.ruleCode(), complianceDecision.reason());
            }
        }

        // 1. Validate Stock Tradability
        if (!stock.isTradable()) {
            throw new BusinessRuleViolationException("STOCK_NOT_TRADABLE", "Stock symbol '" + stock.getSymbol() + "' is not active for trading");
        }

        // 2. Validate Order Quantity
        if (request.quantity() <= 0) {
            throw new BusinessRuleViolationException("INVALID_ORDER_QUANTITY", "Order quantity must be strictly positive");
        }

        if (request.quantity() > riskProperties.getMaxOrderQuantity()) {
            throw new BusinessRuleViolationException("ORDER_QUANTITY_LIMIT_EXCEEDED",
                "Order quantity (" + request.quantity() + ") exceeds maximum allowed order quantity (" + riskProperties.getMaxOrderQuantity() + ")");
        }

        // 3. Calculate Estimated Order Value
        BigDecimal referencePrice = (request.orderType() == OrderType.LIMIT) ? request.limitPrice() : stock.getCurrentPrice();
        BigDecimal estimatedOrderValue = BigDecimal.valueOf(request.quantity()).multiply(referencePrice);

        // 4. Validate Max Order Value Limit
        if (estimatedOrderValue.compareTo(riskProperties.getMaxOrderValue()) > 0) {
            throw new BusinessRuleViolationException("ORDER_VALUE_LIMIT_EXCEEDED",
                "Estimated order value ($" + estimatedOrderValue + ") exceeds maximum order value limit ($" + riskProperties.getMaxOrderValue() + ")");
        }

        // 5. Validate Daily Trading Limit
        BigDecimal currentDailyValue = transactionService.getDailyTradingValueForUser(userId);
        if (currentDailyValue == null) {
            currentDailyValue = BigDecimal.ZERO;
        }

        BigDecimal projectedDailyValue = currentDailyValue.add(estimatedOrderValue);
        if (projectedDailyValue.compareTo(riskProperties.getMaxDailyTradingValue()) > 0) {
            throw new BusinessRuleViolationException("DAILY_TRADING_LIMIT_EXCEEDED",
                "Projected daily trading value ($" + projectedDailyValue + ") exceeds maximum daily trading limit ($" + riskProperties.getMaxDailyTradingValue() + ")");
        }

        // 6. Validate Available Cash or Shares
        if (request.side() == OrderSide.BUY) {
            AccountResponse account = accountService.getAccountByUserId(userId);
            if (account.availableBalance().compareTo(estimatedOrderValue) < 0) {
                throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS",
                    "Insufficient available balance. Required: $" + estimatedOrderValue + ", Available: $" + account.availableBalance());
            }
        } else if (request.side() == OrderSide.SELL) {
            PortfolioPositionResponse position = portfolioService.getPositionBySymbol(userId, stock.getSymbol());
            long availableShares = (position != null) ? position.availableQuantity() : 0;
            if (availableShares < request.quantity()) {
                throw new BusinessRuleViolationException("INSUFFICIENT_SHARES",
                    "Insufficient available shares. Required: " + request.quantity() + ", Available: " + availableShares);
            }
        }

        return RiskDecision.approved();
    }
}
