package com.tradex.ledger;

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
class LedgerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest req = new RegisterRequest("ledgeruser@tradex.com", "password123", "Ledger User");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated()).andReturn();
        userToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User user = userRepository.findByEmail("ledgeruser@tradex.com").orElseThrow();
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("250.0000"), "Deposit for ledger test"));
    }

    @Test
    @DisplayName("GET /api/ledger — Returns authenticated user's paginated double-entry ledger history")
    void getUserLedgerSuccess() throws Exception {
        mockMvc.perform(get("/api/ledger?page=0&size=10")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].entryType").value("ADMIN_DEPOSIT"))
            .andExpect(jsonPath("$.data.content[0].amount").value(250.0));
    }
}
