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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("Health Endpoint — GET /api/health returns HTTP 200, success=true, status=UP")
    void healthEndpointReturnsOperationalStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("System operational"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.service").value("TradeX Order Management System"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
