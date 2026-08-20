package com.tradex.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.order.dto.CreateOrderRequest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {

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

    private String trader1Token;
    private String trader2Token;

    @BeforeEach
    void setUp() throws Exception {
        if (stockRepository.count() == 0) {
            stockRepository.saveAll(List.of(
                new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"),
                new Stock("MSFT", "Microsoft Corporation", new BigDecimal("420.7500"), new BigDecimal("418.9000"), "Technology")
            ));
        }

        // Register Trader 1
        RegisterRequest req1 = new RegisterRequest("trader1@tradex.com", "password123", "Trader One");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated()).andReturn();
        trader1Token = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User t1 = userRepository.findByEmail("trader1@tradex.com").orElseThrow();
        accountService.depositAdmin(new DepositRequest(t1.getId(), new BigDecimal("50000.0000"), "Seed Trader 1"));

        // Register Trader 2
        RegisterRequest req2 = new RegisterRequest("trader2@tradex.com", "password123", "Trader Two");
        MvcResult res2 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated()).andReturn();
        trader2Token = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Full Order Lifecycle & Idempotency — Placement (201), Duplicate Retry (200), Query, Ownership Security, and Cancellation")
    void fullOrderLifecycleAndIdempotency() throws Exception {
        // 1. Initial order submission -> 201 Created
        CreateOrderRequest buyReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, 100, new BigDecimal("180.0000"), "client-order-001");
        MvcResult postRes1 = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + trader1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.clientOrderId").value("client-order-001"))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andReturn();

        String orderIdStr = objectMapper.readTree(postRes1.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. Duplicate order submission with same clientOrderId -> 200 OK (idempotent response)
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + trader1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(orderIdStr))
            .andExpect(jsonPath("$.data.clientOrderId").value("client-order-001"));

        // 3. GET /api/orders lists user's orders
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + trader1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(1));

        // 4. Ownership Security — Trader 2 attempting to view Trader 1's order returns error
        mockMvc.perform(get("/api/orders/" + orderIdStr)
                .header("Authorization", "Bearer " + trader2Token))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 5. Ownership Security — Trader 2 attempting to cancel Trader 1's order returns error
        mockMvc.perform(delete("/api/orders/" + orderIdStr)
                .header("Authorization", "Bearer " + trader2Token))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 6. Trader 1 cancels own order -> 200 OK
        mockMvc.perform(delete("/api/orders/" + orderIdStr)
                .header("Authorization", "Bearer " + trader1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
