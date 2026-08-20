package com.tradex.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.auth.dto.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() throws Exception {
        // Register User 1
        RegisterRequest req1 = new RegisterRequest("txuser1@tradex.com", "password123", "Tx User One");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated()).andReturn();
        user1Token = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User u1 = userRepository.findByEmail("txuser1@tradex.com").orElseThrow();
        accountService.depositAdmin(new DepositRequest(u1.getId(), new BigDecimal("100.0000"), "User 1 deposit"));

        // Register User 2
        RegisterRequest req2 = new RegisterRequest("txuser2@tradex.com", "password123", "Tx User Two");
        MvcResult res2 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isCreated()).andReturn();
        user2Token = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User u2 = userRepository.findByEmail("txuser2@tradex.com").orElseThrow();
        accountService.depositAdmin(new DepositRequest(u2.getId(), new BigDecimal("500.0000"), "User 2 deposit"));
    }

    @Test
    @DisplayName("GET /api/transactions — Returns authenticated user's activity history without exposing other users' data")
    void getUserTransactionsSuccessAndSecurityIsolation() throws Exception {
        // User 1 requests their transactions
        mockMvc.perform(get("/api/transactions?page=0&size=10")
                .header("Authorization", "Bearer " + user1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].amount").value(100.0));

        // User 2 requests their transactions
        mockMvc.perform(get("/api/transactions?page=0&size=10")
                .header("Authorization", "Bearer " + user2Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].amount").value(500.0));
    }
}
