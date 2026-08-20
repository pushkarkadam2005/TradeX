package com.tradex.auth;

import com.tradex.auth.service.JwtService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(SecurityAuthorizationTest.TestAdminControllerConfig.class)
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @TestConfiguration
    static class TestAdminControllerConfig {
        @Bean
        public TestAdminController testAdminController() {
            return new TestAdminController();
        }
    }

    @RestController
    static class TestAdminController {
        @GetMapping("/api/admin/test")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminTest() {
            return "ADMIN_OK";
        }
    }

    @Test
    @DisplayName("E. 403 Authorization — ROLE_USER attempting ADMIN operation returns 403 FORBIDDEN")
    void userRoleAccessingAdminEndpointReturns403() throws Exception {
        User regularUser = new User("regular@tradex.com", "hash", "Regular User", Role.ROLE_USER);
        User savedUser = userService.save(regularUser);
        String userToken = jwtService.generateToken(savedUser);

        mockMvc.perform(get("/api/admin/test")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    @Test
    @DisplayName("E. 403 Authorization — ROLE_ADMIN accessing ADMIN operation returns 200 OK")
    void adminRoleAccessingAdminEndpointReturns200() throws Exception {
        User adminUser = new User("admin@tradex.com", "hash", "Admin User", Role.ROLE_ADMIN);
        User savedAdmin = userService.save(adminUser);
        String adminToken = jwtService.generateToken(savedAdmin);

        mockMvc.perform(get("/api/admin/test")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }
}
