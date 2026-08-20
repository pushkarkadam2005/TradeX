package com.tradex.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.account.dto.DepositRequest;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.user.entity.Role;
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
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String userToken;
    private String adminToken;
    private User normalUser;

    @BeforeEach
    void setUp() throws Exception {
        // Register Normal User
        RegisterRequest userReq = new RegisterRequest("accuser@tradex.com", "password123", "Account User");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userReq)))
            .andExpect(status().isCreated()).andReturn();
        userToken = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        normalUser = userRepository.findByEmail("accuser@tradex.com").orElseThrow();

        // Register Admin User
        RegisterRequest adminReq = new RegisterRequest("accadmin@tradex.com", "password123", "Account Admin");
        MvcResult res2 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminReq)))
            .andExpect(status().isCreated()).andReturn();
        adminToken = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        User adminEntity = userRepository.findByEmail("accadmin@tradex.com").orElseThrow();
        adminEntity.setRole(Role.ROLE_ADMIN);
        userRepository.save(adminEntity);
    }

    @Test
    @DisplayName("GET /api/account — Returns 200 OK with authenticated user's account details")
    void getUserAccountSuccess() throws Exception {
        mockMvc.perform(get("/api/account")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.availableBalance").value(0.0));
    }

    @Test
    @DisplayName("POST /api/account/deposit — ADMIN returns 200 OK, normal USER returns 403 FORBIDDEN")
    void adminDepositRoleAuthorization() throws Exception {
        DepositRequest deposit = new DepositRequest(normalUser.getId(), new BigDecimal("1000.0000"), "Admin funding");

        // Normal USER attempts deposit -> 403 FORBIDDEN
        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deposit)))
            .andExpect(status().isForbidden());

        // ADMIN attempts deposit -> 200 OK
        mockMvc.perform(post("/api/account/deposit")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deposit)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availableBalance").value(1000.0));
    }
}
