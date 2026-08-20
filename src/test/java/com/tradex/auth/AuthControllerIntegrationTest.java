package com.tradex.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.auth.dto.AuthResponse;
import com.tradex.auth.dto.LoginRequest;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.auth.service.JwtService;
import com.tradex.common.dto.ApiResponse;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full Auth Pipeline — Register, Login, Access Protected /me, and Reject Unauthorized")
    void fullAuthPipeline() throws Exception {
        // 1. Register new user -> 201 Created
        RegisterRequest registerReq = new RegisterRequest("trader.test@tradex.com", "securePass123", "Test Trader");
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.user.email").value("trader.test@tradex.com"))
            .andExpect(jsonPath("$.data.user.fullName").value("Test Trader"))
            .andReturn();

        // 2. Duplicate registration -> 422 UNPROCESSABLE_ENTITY
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        // 3. Login with correct credentials -> 200 OK
        LoginRequest loginReq = new LoginRequest("trader.test@tradex.com", "securePass123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        ApiResponse<?> apiResp = objectMapper.readValue(responseJson, ApiResponse.class);
        String token = objectMapper.convertValue(apiResp.getData(), AuthResponse.class).accessToken();

        // 4. Access protected endpoint GET /api/auth/me with Bearer token -> 200 OK
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("trader.test@tradex.com"))
            .andExpect(jsonPath("$.data.fullName").value("Test Trader"));

        // 5. Access protected endpoint GET /api/auth/me without token -> 401 Unauthorized
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("A. DTO Validation Failure — Invalid email format and short password return 400 Bad Request")
    void dtoValidationFailuresReturn400() throws Exception {
        // Invalid email format
        RegisterRequest invalidEmail = new RegisterRequest("not-an-email", "validPassword123", "User Name");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmail)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Short password (< 6 chars)
        RegisterRequest shortPassword = new RegisterRequest("user@tradex.com", "12345", "User Name");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortPassword)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Blank login fields
        LoginRequest blankLogin = new LoginRequest("", "");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankLogin)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("B. Unknown User Login & Wrong Password — Return 401 Unauthorized (INVALID_CREDENTIALS)")
    void unknownUserAndWrongPasswordReturn401() throws Exception {
        // Non-existent email -> 401
        LoginRequest unknownUserReq = new LoginRequest("nobody@tradex.com", "somePassword");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unknownUserReq)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Invalid email or password"));

        // Register valid user
        RegisterRequest registerReq = new RegisterRequest("realuser@tradex.com", "correctPass123", "Real User");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isCreated());

        // Wrong password -> 401
        LoginRequest wrongPassReq = new LoginRequest("realuser@tradex.com", "wrongPass123");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPassReq)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("C. Invalid JWT Signature — Token signed with different key returns 401 Unauthorized")
    void invalidJwtSignatureReturns401() throws Exception {
        SecretKey differentKey = Keys.hmacShaKeyFor("different_secret_key_for_testing_signature_rejection_12345678".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
            .claims(Map.of("userId", UUID.randomUUID().toString(), "role", "ROLE_USER"))
            .subject("trader@tradex.com")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(differentKey)
            .compact();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + tamperedToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("D. Malformed Authorization Header — Handled gracefully without crash returning 401 Unauthorized")
    void malformedAuthHeaderHandledGracefully() throws Exception {
        // Missing token string after Bearer
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized());

        // Invalid prefix without Bearer
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized());

        // Garbage token string
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer garbage_invalid_token_string"))
            .andExpect(status().isUnauthorized());
    }
}
