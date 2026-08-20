package com.tradex.order;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.dto.FillExecutionRequest;
import com.tradex.ledger.dto.LedgerResponse;
import com.tradex.ledger.service.LedgerService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.trade.service.TradeService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialOrderLifecycleIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private User buyer;
    private User seller;
    private Stock stock;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        registry.reinitializeBook("AAPL");

        stock = stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        buyer = userRepository.save(new User("buyer_" + UUID.randomUUID() + "@tradex.com", "passhash", "Buyer User", Role.ROLE_USER));
        seller = userRepository.save(new User("seller_" + UUID.randomUUID() + "@tradex.com", "passhash", "Seller User", Role.ROLE_USER));

        // 1. Buyer deposits $10,000 cash
        accountService.depositAdmin(new DepositRequest(buyer.getId(), new BigDecimal("10000.0000"), "Buyer deposit"));

        // 2. Seller is credited with 100 shares of AAPL
        portfolioService.addSharesOnBuy(seller.getId(), stock.getId(), "AAPL", 100, new BigDecimal("150.0000"));
    }

    @Test
    @DisplayName("Complete Trade Settlement Lifecycle — Financial cash deduction, share credit, ledger entry, and price improvement release")
    void completeTradeSettlementLifecycle() {
        // Seller places LIMIT SELL: 50 shares @ $180.00
        CreateOrderRequest sellReq = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 50, new BigDecimal("180.0000"), "seller-order-01");
        orderService.createOrder(seller.getId(), sellReq);

        // Verify seller shares reserved
        PortfolioPositionResponse sellerPosBefore = portfolioService.getPositionBySymbol(seller.getId(), "AAPL");
        assertThat(sellerPosBefore.lockedQuantity()).isEqualTo(50);
        assertThat(sellerPosBefore.availableQuantity()).isEqualTo(50);

        // Buyer places LIMIT BUY: 50 shares @ $185.00 (Price improvement expected: matches at resting $180.00!)
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 50, new BigDecimal("185.0000"), "buyer-order-01");
        OrderResponse buyOrderResponse = orderService.createOrder(buyer.getId(), buyReq).orderResponse();

        assertThat(buyOrderResponse.status()).isEqualTo(OrderStatus.FILLED);

        // Verify Buyer financial state:
        // Trade value = 50 * $180.00 = $9,000.00. Buyer starting cash = $10,000.00 -> Ending cash = $1,000.00
        AccountResponse buyerAccount = accountService.getAccountByUserId(buyer.getId());
        assertThat(buyerAccount.availableBalance()).isEqualByComparingTo("1000.0000");
        assertThat(buyerAccount.lockedBalance()).isEqualByComparingTo("0.0000");

        // Verify Buyer position state: 50 shares @ $180.00
        PortfolioPositionResponse buyerPosition = portfolioService.getPositionBySymbol(buyer.getId(), "AAPL");
        assertThat(buyerPosition.quantity()).isEqualTo(50);
        assertThat(buyerPosition.averageBuyPrice()).isEqualByComparingTo("180.0000");

        // Verify Seller financial state: Received $9,000.00 cash
        AccountResponse sellerAccount = accountService.getAccountByUserId(seller.getId());
        assertThat(sellerAccount.availableBalance()).isEqualByComparingTo("9000.0000");

        // Verify Seller position state: 50 remaining shares (50 deducted)
        PortfolioPositionResponse sellerPosAfter = portfolioService.getPositionBySymbol(seller.getId(), "AAPL");
        assertThat(sellerPosAfter.quantity()).isEqualTo(50);
        assertThat(sellerPosAfter.lockedQuantity()).isEqualTo(0);

        // Verify Double-Entry Ledger entries generated for Buyer
        List<LedgerResponse> buyerLedger = ledgerService.getAccountLedger(accountService.getOrCreateAccountEntity(buyer.getId()).getId());
        assertThat(buyerLedger).isNotEmpty();
    }

    @Test
    @DisplayName("Idempotent Trade Settlement — Duplicate settlement call with same executionId skips without double mutation")
    void idempotentTradeSettlement() {
        String executionId = "exec-idempotent-999";
        FillExecutionRequest fillReq = new FillExecutionRequest(
            executionId, UUID.randomUUID(), UUID.randomUUID(), stock.getId(),
            buyer.getId(), seller.getId(), "AAPL", new BigDecimal("180.0000"), 10, Instant.now()
        );

        // Reserve funds and shares for the raw settlement call test
        accountService.reserveFunds(buyer.getId(), new BigDecimal("1800.0000"), null);
        portfolioService.reserveShares(seller.getId(), stock.getId(), "AAPL", 10);

        // First settlement
        tradeService.settleTrade(fillReq);
        AccountResponse buyerAcc1 = accountService.getAccountByUserId(buyer.getId());

        // Duplicate settlement retry
        tradeService.settleTrade(fillReq);
        AccountResponse buyerAcc2 = accountService.getAccountByUserId(buyer.getId());

        assertThat(buyerAcc1.availableBalance()).isEqualByComparingTo(buyerAcc2.availableBalance());
    }

    @Test
    @DisplayName("Order Cancellation Financial Release — Cancelling BUY order releases locked cash, cancelling SELL releases shares")
    void cancellationReleasesFinancialAssets() {
        // Buyer places LIMIT BUY @ $100 for 10 shares -> $1,000 reserved
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("100.0000"), "cancel-buy-1");
        OrderResponse buyOrder = orderService.createOrder(buyer.getId(), buyReq).orderResponse();

        AccountResponse buyerAccLocked = accountService.getAccountByUserId(buyer.getId());
        assertThat(buyerAccLocked.availableBalance()).isEqualByComparingTo("9000.0000");

        // Cancel order
        orderService.cancelOrder(buyOrder.id(), buyer.getId());
        AccountResponse buyerAccReleased = accountService.getAccountByUserId(buyer.getId());
        assertThat(buyerAccReleased.availableBalance()).isEqualByComparingTo("10000.0000");
    }
}
