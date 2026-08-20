package com.tradex.order;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
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
class OrderRiskIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private User user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        registry.reinitializeBook("AAPL");

        stock = stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        user = userRepository.save(new User("riskuser_" + UUID.randomUUID() + "@tradex.com", "passhash", "Risk User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("10000.0000"), "Risk test deposit"));
    }

    @Test
    @DisplayName("Pre-Trade Risk Check Rejection — Rejects max order value limit breach BEFORE order creation or OrderBook entry")
    void riskRejectionPreventsOrderCreationAndBookPlacement() {
        // Limit max order value is $100,000. Submit order for 1,000 shares @ $150 = $150,000
        CreateOrderRequest maxOrderReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 1000, new BigDecimal("150.0000"), "risk-breach-1");

        assertThatThrownBy(() -> orderService.createOrder(user.getId(), maxOrderReq))
            .isInstanceOf(BusinessRuleViolationException.class)
            .extracting("errorCode").isEqualTo("ORDER_VALUE_LIMIT_EXCEEDED");

        // Verify account balance was NOT locked or reserved
        AccountResponse account = accountService.getAccountByUserId(user.getId());
        assertThat(account.availableBalance()).isEqualByComparingTo("10000.0000");
        assertThat(account.lockedBalance()).isEqualByComparingTo("0.0000");

        // Verify OrderBook remained completely empty
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }
}
