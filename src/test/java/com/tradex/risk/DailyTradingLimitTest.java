package com.tradex.risk;

import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.transaction.service.TransactionService;
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
class DailyTradingLimitTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private User user;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        user = userRepository.save(new User("dailyuser_" + UUID.randomUUID() + "@tradex.com", "passhash", "Daily User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("1000000.0000"), "Initial deposit for daily limit test"));
    }

    @Test
    @DisplayName("Daily Limit & Cancel Release — Order cancellation releases daily trading capacity")
    void orderCancellationReleasesDailyTradingCapacity() {
        // Place first order: 100 shares @ $100 = $10,000
        CreateOrderRequest req1 = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("100.0000"), "d-ord-1");
        OrderResponse res1 = orderService.createOrder(user.getId(), req1).orderResponse();

        BigDecimal dailyVal1 = transactionService.getDailyTradingValueForUser(user.getId());
        assertThat(dailyVal1).isEqualByComparingTo("10000.0000");

        // Cancel first order -> RELEASE event recorded
        orderService.cancelOrder(res1.id(), user.getId());

        BigDecimal dailyVal2 = transactionService.getDailyTradingValueForUser(user.getId());
        assertThat(dailyVal2).isEqualByComparingTo("0.0000");
    }
}
