package com.tradex.account;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
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
class FinancialReconciliationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private User userA;
    private User counterparty;
    private Stock stock;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        stock = stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        userA = userRepository.save(new User("usera_" + UUID.randomUUID() + "@tradex.com", "passhash", "User A", Role.ROLE_USER));
        counterparty = userRepository.save(new User("cparty_" + UUID.randomUUID() + "@tradex.com", "passhash", "Counterparty", Role.ROLE_USER));

        // 1. User A deposits $100,000
        accountService.depositAdmin(new DepositRequest(userA.getId(), new BigDecimal("100000.0000"), "Initial seed"));

        // Counterparty gets 500 AAPL shares to provide sell liquidity
        portfolioService.addSharesOnBuy(counterparty.getId(), stock.getId(), "AAPL", 500, new BigDecimal("100.0000"));
        // Counterparty deposits $50,000 to provide buy liquidity
        accountService.depositAdmin(new DepositRequest(counterparty.getId(), new BigDecimal("50000.0000"), "Counterparty deposit"));
    }

    @Test
    @DisplayName("Deterministic Financial Reconciliation — User A buys 100 @ $100, buys 50 @ $120, sells 30 @ $150; math reconciles independently")
    void deterministicFinancialReconciliation() {
        // Step 1: Counterparty places LIMIT SELL: 100 @ $100
        orderService.createOrder(counterparty.getId(), new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 100, new BigDecimal("100.0000"), "sell-1"));
        // User A buys 100 AAPL @ $100
        orderService.createOrder(userA.getId(), new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("100.0000"), "buy-1"));

        // Step 2: Counterparty places LIMIT SELL: 50 @ $120
        orderService.createOrder(counterparty.getId(), new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 50, new BigDecimal("120.0000"), "sell-2"));
        // User A buys 50 AAPL @ $120
        orderService.createOrder(userA.getId(), new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 50, new BigDecimal("120.0000"), "buy-2"));

        // Step 3: Counterparty places LIMIT BUY: 30 @ $150
        orderService.createOrder(counterparty.getId(), new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 30, new BigDecimal("150.0000"), "buy-cparty-30"));
        // User A sells 30 AAPL @ $150
        orderService.createOrder(userA.getId(), new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 30, new BigDecimal("150.0000"), "sell-3"));

        // Independent Reconciliation Math:
        // Initial Cash = $100,000.00
        // Spent on Buy 1 (100 * $100) = -$10,000.00
        // Spent on Buy 2 (50 * $120) = -$6,000.00
        // Received on Sell 3 (30 * $150) = +$4,500.00
        // Expected Cash Balance = $100,000 - $10,000 - $6,000 + $4,500 = $88,500.00
        AccountResponse userAAccount = accountService.getAccountByUserId(userA.getId());
        assertThat(userAAccount.availableBalance()).isEqualByComparingTo("88500.0000");
        assertThat(userAAccount.lockedBalance()).isEqualByComparingTo("0.0000");

        // Position Quantity = 100 + 50 - 30 = 120 AAPL shares
        // Average Buy Price = (100 * $100 + 50 * $120) / 150 = $16,000 / 150 = $106.6666...
        PortfolioPositionResponse userAPosition = portfolioService.getPositionBySymbol(userA.getId(), "AAPL");
        assertThat(userAPosition.quantity()).isEqualTo(120);
        assertThat(userAPosition.lockedQuantity()).isEqualTo(0);
        assertThat(userAPosition.averageBuyPrice()).isEqualByComparingTo("106.6667");
    }
}
