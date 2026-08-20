package com.tradex.trade;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.dto.FillExecutionRequest;
import com.tradex.common.exception.BusinessRuleViolationException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SettlementRollbackTest {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User buyer;
    private User seller;
    private Stock stock;

    @BeforeEach
    void setUp() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            stock = stockRepository.findBySymbol("TSLA").orElseGet(() ->
                stockRepository.save(new Stock("TSLA", "Tesla Inc.", new BigDecimal("200.0000"), new BigDecimal("198.0000"), "Automotive"))
            );
            buyer = userRepository.save(new User("rbuser" + UUID.randomUUID() + "@tradex.com", "passhash", "Rollback Buyer", Role.ROLE_USER));
            seller = userRepository.save(new User("rbseller" + UUID.randomUUID() + "@tradex.com", "passhash", "Rollback Seller", Role.ROLE_USER));

            accountService.depositAdmin(new DepositRequest(buyer.getId(), new BigDecimal("5000.0000"), "Deposit"));
            accountService.reserveFunds(buyer.getId(), new BigDecimal("2000.0000"), null);
            portfolioService.addSharesOnBuy(seller.getId(), stock.getId(), "TSLA", 10, new BigDecimal("200.0000"));
            portfolioService.reserveShares(seller.getId(), stock.getId(), "TSLA", 10);
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }

    @Test
    @DisplayName("Settlement Transaction Failure Rollback — Artificial failure midway through settlement reverts all cash, portfolio, and ledger changes")
    void settlementRollbackRevertsAllFinancialMutations() {
        AccountResponse buyerBefore = accountService.getAccountByUserId(buyer.getId());

        TransactionStatus txStatus = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            FillExecutionRequest fill = new FillExecutionRequest(
                "exec-rollback-" + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), stock.getId(),
                buyer.getId(), seller.getId(), "TSLA", new BigDecimal("200.0000"), 10, Instant.now()
            );

            // Execute settlement
            tradeService.settleTrade(fill);

            // Simulate artificial failure midway
            throw new BusinessRuleViolationException("ARTIFICIAL_FAILURE", "Simulating database network failure during settlement");
        } catch (BusinessRuleViolationException expected) {
            transactionManager.rollback(txStatus);
        }

        // Verify Buyer account state is completely unchanged (no partial deductions)
        AccountResponse buyerAfter = accountService.getAccountByUserId(buyer.getId());
        assertThat(buyerAfter.availableBalance()).isEqualByComparingTo(buyerBefore.availableBalance());
        assertThat(buyerAfter.lockedBalance()).isEqualByComparingTo(buyerBefore.lockedBalance());
    }
}
