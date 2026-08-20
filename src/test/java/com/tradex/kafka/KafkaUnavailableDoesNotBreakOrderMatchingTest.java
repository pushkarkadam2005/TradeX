package com.tradex.kafka;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
@ActiveProfiles("test")
class KafkaUnavailableDoesNotBreakOrderMatchingTest {

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

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        user = userRepository.save(new User("kafkadown_" + UUID.randomUUID() + "@tradex.com", "passhash", "Kafka Down User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("10000.0000"), "Deposit for Kafka down test"));
    }

    @Test
    @DisplayName("Kafka Downtime Resilience — Order matching, fund reservation, and DB commit succeed even when Kafka is completely down")
    void kafkaDowntimeDoesNotBreakTrading() {
        // Simulate total Kafka failure
        willThrow(new RuntimeException("Kafka cluster unreachable connection refused"))
            .given(kafkaTemplate).send(anyString(), anyString(), any());

        CreateOrderRequest request = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 10, new BigDecimal("180.0000"), "ord-kafka-down-1");

        // Order creation MUST succeed despite Kafka failure
        OrderResponse orderRes = orderService.createOrder(user.getId(), request).orderResponse();
        assertThat(orderRes).isNotNull();
        assertThat(orderRes.clientOrderId()).isEqualTo("ord-kafka-down-1");

        // Verify financial state committed properly to PostgreSQL
        AccountResponse account = accountService.getAccountByUserId(user.getId());
        assertThat(account.availableBalance()).isEqualByComparingTo("8200.0000");
        assertThat(account.lockedBalance()).isEqualByComparingTo("1800.0000");
    }
}
