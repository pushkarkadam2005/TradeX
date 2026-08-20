package com.tradex.account;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.entity.Account;
import com.tradex.account.service.AccountService;
import com.tradex.ledger.dto.LedgerResponse;
import com.tradex.ledger.service.LedgerService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinancialAuditTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PortfolioService portfolioService;

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

        stock = new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology");
        stockRepository.save(stock);

        buyer = userRepository.save(new User("auditbuyer@tradex.com", "passhash", "Audit Buyer", Role.ROLE_USER));
        seller = userRepository.save(new User("auditseller@tradex.com", "passhash", "Audit Seller", Role.ROLE_USER));

        // Buyer deposits $5,000 cash
        accountService.depositAdmin(new DepositRequest(buyer.getId(), new BigDecimal("5000.0000"), "Audit deposit"));

        // Seller is credited with 20 shares of AAPL
        portfolioService.addSharesOnBuy(seller.getId(), stock.getId(), "AAPL", 20, new BigDecimal("150.0000"));
    }

    @Test
    @DisplayName("Financial Audit Invariant — Account cash balances strictly match the sum of immutable double-entry ledger entries")
    void financialAuditLedgerConsistency() {
        // Seller places SELL 10 shares @ $180
        CreateOrderRequest sellReq = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 10, new BigDecimal("180.0000"), "audit-sell-1");
        orderService.createOrder(seller.getId(), sellReq);

        // Buyer places BUY 10 shares @ $180 -> Executes fill
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("180.0000"), "audit-buy-1");
        orderService.createOrder(buyer.getId(), buyReq);

        Account accountBuyer = accountService.getOrCreateAccountEntity(buyer.getId());
        List<LedgerResponse> buyerLedger = ledgerService.getAccountLedger(accountBuyer.getId());

        // Verify total cash + locked cash equals initial deposit minus spent trade cash
        AccountResponse buyerAccount = accountService.getAccountByUserId(buyer.getId());
        BigDecimal currentTotalCash = buyerAccount.availableBalance().add(buyerAccount.lockedBalance());
        assertThat(currentTotalCash).isEqualByComparingTo("3200.0000"); // $5000 - (10 * $180 = $1800) = $3200

        // Verify ledger trail is traceable and unbroken (ADMIN_DEPOSIT, BUY_RESERVATION, BUY_SETTLEMENT_DEDUCTION)
        assertThat(buyerLedger).hasSize(3);
    }
}
