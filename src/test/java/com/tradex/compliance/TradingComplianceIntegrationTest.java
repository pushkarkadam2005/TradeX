package com.tradex.compliance;

import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;
import com.tradex.kyc.repository.KycRepository;
import com.tradex.kyc.service.KycService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TradingComplianceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private KycService kycService;

    @Autowired
    private KycRepository kycRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private User unverifiedUser;
    private Stock stock;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        registry.reinitializeBook("AAPL");

        stock = stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        unverifiedUser = userRepository.save(new User("unverified_" + UUID.randomUUID() + "@tradex.com", "passhash", "Unverified User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(unverifiedUser.getId(), new BigDecimal("10000.0000"), "Deposit for compliance test"));

        KycVerification unverifiedKyc = new KycVerification(unverifiedUser.getId(), KycLevel.BASIC, "MOCK_KYC");
        unverifiedKyc.setStatus(KycStatus.NOT_STARTED);
        kycRepository.save(unverifiedKyc);
    }

    @Test
    @DisplayName("Trading Compliance Enforcement — Unverified user order placement is rejected BEFORE cash reservation or OrderBook placement")
    void unverifiedUserOrderPlacementRejected() {
        CreateOrderRequest request = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("180.0000"), "ord-comp-1");

        // 1. Order submission by unverified user fails with KYC_REQUIRED / KYC_NOT_VERIFIED
        assertThatThrownBy(() -> orderService.createOrder(unverifiedUser.getId(), request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("KYC");

        // Verify account balance was NOT reserved
        var account = accountService.getAccountByUserId(unverifiedUser.getId());
        assertThat(account.availableBalance()).isEqualByComparingTo("10000.0000");
        assertThat(account.lockedBalance()).isEqualByComparingTo("0.0000");

        // Verify OrderBook remained empty
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);

        // 2. User completes KYC
        kycService.submitKyc(unverifiedUser.getId(), new SubmitKycRequest(KycLevel.STANDARD, "PASSPORT"));

        // 3. Resubmit order after KYC verification -> Succeeds cleanly!
        var result = orderService.createOrder(unverifiedUser.getId(), request);
        assertThat(result.orderResponse()).isNotNull();
        assertThat(result.orderResponse().clientOrderId()).isEqualTo("ord-comp-1");
    }
}
