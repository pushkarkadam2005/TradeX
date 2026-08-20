package com.tradex.risk;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.risk.config.RiskProperties;
import com.tradex.risk.model.RiskDecision;
import com.tradex.risk.service.RiskService;
import com.tradex.stock.entity.Stock;
import com.tradex.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private RiskProperties riskProperties;
    private RiskService riskService;

    private UUID userId;
    private Stock stock;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskProperties();
        riskProperties.setMaxOrderValue(new BigDecimal("100000.0000"));
        riskProperties.setMaxOrderQuantity(10000L);
        riskProperties.setMaxDailyTradingValue(new BigDecimal("500000.0000"));

        riskService = new RiskService(riskProperties, accountService, portfolioService, transactionService, eventPublisher);

        userId = UUID.randomUUID();
        stock = new Stock("AAPL", "Apple Inc.", new BigDecimal("185.0000"), new BigDecimal("184.0000"), "Technology");
    }

    @Test
    @DisplayName("Valid Pre-Trade Risk Check — Approved when within limits and sufficient assets exist")
    void validRiskCheckApproved() {
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("180.0000"), "r-1");
        given(transactionService.getDailyTradingValueForUser(userId)).willReturn(BigDecimal.ZERO);
        given(accountService.getAccountByUserId(userId)).willReturn(
            new AccountResponse(UUID.randomUUID(), userId, "USD", new BigDecimal("5000.0000"), BigDecimal.ZERO)
        );

        RiskDecision decision = riskService.evaluateOrderRisk(userId, buyReq, stock);
        assertThat(decision.isApproved()).isTrue();
    }

    @Test
    @DisplayName("Max Order Value Exceeded — Throws BusinessRuleViolationException ORDER_VALUE_LIMIT_EXCEEDED")
    void maxOrderValueExceededRejection() {
        // Order value = 1,000 shares @ $150 = $150,000 (exceeds $100,000 limit)
        CreateOrderRequest largeValueOrder = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 1000, new BigDecimal("150.0000"), "r-2");

        assertThatThrownBy(() -> riskService.evaluateOrderRisk(userId, largeValueOrder, stock))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("errorCode").isEqualTo("ORDER_VALUE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Max Order Quantity Exceeded — Throws BusinessRuleViolationException ORDER_QUANTITY_LIMIT_EXCEEDED")
    void maxOrderQuantityExceededRejection() {
        // Order quantity = 20,000 shares (exceeds 10,000 limit)
        CreateOrderRequest largeQtyOrder = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 20000, new BigDecimal("1.0000"), "r-3");

        assertThatThrownBy(() -> riskService.evaluateOrderRisk(userId, largeQtyOrder, stock))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("errorCode").isEqualTo("ORDER_QUANTITY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Daily Trading Limit Exceeded — Throws BusinessRuleViolationException DAILY_TRADING_LIMIT_EXCEEDED")
    void dailyTradingLimitExceededRejection() {
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("100.0000"), "r-4");
        // User already traded $495,000 today. New order is $10,000 -> Total $505,000 exceeds $500,000 limit
        given(transactionService.getDailyTradingValueForUser(userId)).willReturn(new BigDecimal("495000.0000"));

        assertThatThrownBy(() -> riskService.evaluateOrderRisk(userId, buyReq, stock))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("errorCode").isEqualTo("DAILY_TRADING_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("Insufficient Available Shares — Throws BusinessRuleViolationException INSUFFICIENT_SHARES for SELL order")
    void insufficientSharesRejection() {
        CreateOrderRequest sellReq = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 50, new BigDecimal("180.0000"), "r-5");
        given(transactionService.getDailyTradingValueForUser(userId)).willReturn(BigDecimal.ZERO);
        given(portfolioService.getPositionBySymbol(userId, "AAPL")).willReturn(
            new PortfolioPositionResponse(UUID.randomUUID(), userId, stock.getId(), "AAPL", 20L, 0L, 20L, new BigDecimal("170.0000"))
        );

        assertThatThrownBy(() -> riskService.evaluateOrderRisk(userId, sellReq, stock))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("errorCode").isEqualTo("INSUFFICIENT_SHARES");
    }
}
