package com.tradex.withdrawal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.service.KycService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import com.tradex.withdrawal.dto.CreateWithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WithdrawalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private KycService kycService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;
    private String adminToken;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        RegisterRequest req1 = new RegisterRequest("wdruser@tradex.com", "password123", "Withdrawal User");
        MvcResult res1 = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated()).andReturn();
        userToken = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        user = userRepository.findByEmail("wdruser@tradex.com").orElseThrow();

        // Deposit $5,000 cash and complete KYC for user
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("5000.0000"), "Initial test deposit"));
        kycService.submitKyc(user.getId(), new SubmitKycRequest(KycLevel.STANDARD, "PASSPORT"));

        // Register admin
        User admin = new User("adminwdr@tradex.com", passwordEncoder.encode("password123"), "Admin Wdr User", Role.ROLE_ADMIN);
        userRepository.save(admin);

        MvcResult adminResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"adminwdr@tradex.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andReturn();
        adminToken = objectMapper.readTree(adminResult.getResponse().getContentAsString())
            .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("Withdrawal Lifecycle — Submit withdrawal request, list withdrawals, and process admin approval")
    void withdrawalLifecycleAndAdminAuthorization() throws Exception {
        CreateWithdrawalRequest createReq = new CreateWithdrawalRequest(new BigDecimal("1000.0000"), "BANK-DEST-123", "wdr-client-001");

        // 1. User submits withdrawal request -> 201 CREATED
        MvcResult createRes = mockMvc.perform(post("/api/withdrawals")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.amount").value(1000.0))
            .andExpect(jsonPath("$.data.status").value("REQUESTED"))
            .andReturn();

        String withdrawalIdStr = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asText();
        UUID withdrawalId = UUID.fromString(withdrawalIdStr);

        // 2. Regular user attempts admin approval -> 403 Forbidden
        mockMvc.perform(post("/api/admin/withdrawals/" + withdrawalId + "/approve")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());

        // 3. Admin approves withdrawal -> 200 OK & COMPLETED status
        mockMvc.perform(post("/api/admin/withdrawals/" + withdrawalId + "/approve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 4. Verify user withdrawal list shows COMPLETED status
        mockMvc.perform(get("/api/withdrawals?page=0&size=10")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }
}
