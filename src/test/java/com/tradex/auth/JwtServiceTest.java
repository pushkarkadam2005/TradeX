package com.tradex.auth;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.auth.service.JwtService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971";
    private static final long TEST_EXPIRATION = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, TEST_EXPIRATION);
    }

    @Test
    @DisplayName("JWT Service generates valid signed token with sub, userId, role claims (no fullName)")
    void generateAndValidateToken() {
        User user = new User("trader@tradex.com", "hash", "Trader One", Role.ROLE_USER);
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        String token = jwtService.generateToken(user);
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("trader@tradex.com");
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();

        // Verify claims
        String userIdClaim = jwtService.extractClaim(token, claims -> claims.get("userId", String.class));
        String roleClaim = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
        String fullNameClaim = jwtService.extractClaim(token, claims -> claims.get("fullName", String.class));

        assertThat(userIdClaim).isEqualTo(userId.toString());
        assertThat(roleClaim).isEqualTo("ROLE_USER");
        assertThat(fullNameClaim).isNull(); // fullName removed from claims
    }

    @Test
    @DisplayName("JWT Service rejects expired token")
    void expiredTokenValidation() {
        JwtService shortLivedJwtService = new JwtService(TEST_SECRET, -1000); // expired 1s ago
        User user = new User("trader@tradex.com", "hash", "Trader One", Role.ROLE_USER);
        user.setId(UUID.randomUUID());

        String token = shortLivedJwtService.generateToken(user);
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(jwtService.isTokenValid(token, principal)).isFalse();
    }
}
