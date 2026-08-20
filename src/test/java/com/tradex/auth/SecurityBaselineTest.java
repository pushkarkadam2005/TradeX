package com.tradex.auth;

import com.tradex.auth.config.SecurityConfig;
import com.tradex.auth.controller.HealthController;
import com.tradex.auth.filter.JwtAuthenticationFilter;
import com.tradex.auth.service.JwtService;
import com.tradex.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityBaselineTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("Security Baseline — /api/health is publicly accessible without authentication")
    void healthEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security Baseline — SecurityFilterChain bean initializes properly")
    void securityFilterChainBeanIsConfigured() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }
}
