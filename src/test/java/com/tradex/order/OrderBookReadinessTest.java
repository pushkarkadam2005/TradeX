package com.tradex.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderBookReadinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrderBookRegistry registry;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        if (stockRepository.count() == 0) {
            stockRepository.saveAll(List.of(
                new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology")
            ));
        }

        RegisterRequest req = new RegisterRequest("readinesstrader@tradex.com", "password123", "Readiness Trader");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated()).andReturn();
        userToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User u = userRepository.findByEmail("readinesstrader@tradex.com").orElseThrow();
        accountService.depositAdmin(new DepositRequest(u.getId(), new BigDecimal("50000.0000"), "Seed readiness trader"));
    }

    private void setInitialized(boolean value) throws Exception {
        Field field = OrderBookRegistry.class.getDeclaredField("initialized");
        field.setAccessible(true);
        AtomicBoolean initialized = (AtomicBoolean) field.get(registry);
        initialized.set(value);
    }

    @Test
    @DisplayName("Startup Readiness Gate — Rejects order placement with HTTP 503 SERVICE_UNAVAILABLE when matching engine is uninitialized")
    void uninitializedRegistryReturns503() throws Exception {
        setInitialized(false);

        CreateOrderRequest request = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("180.0000"), "client-gate-1");

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INFRASTRUCTURE_NOT_READY"))
            .andExpect(jsonPath("$.message").value("Order matching engine is not ready"));

        // Restore initialized state for subsequent tests
        setInitialized(true);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }
}
