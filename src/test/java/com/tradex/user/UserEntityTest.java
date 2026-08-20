package com.tradex.user;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    @DisplayName("User entity maps domain fields cleanly without Spring Security coupling")
    void userEntityMapping() {
        User user = new User("trader@tradex.com", "hashed_secret", "Alex Trader", Role.ROLE_USER);

        assertThat(user.getEmail()).isEqualTo("trader@tradex.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed_secret");
        assertThat(user.getFullName()).isEqualTo("Alex Trader");
        assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("UserPrincipal adapter wraps User entity and exposes UserDetails methods")
    void userPrincipalAdapter() {
        User user = new User("trader@tradex.com", "hashed_secret", "Alex Trader", Role.ROLE_USER);
        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getUsername()).isEqualTo("trader@tradex.com");
        assertThat(principal.getPassword()).isEqualTo("hashed_secret");
        assertThat(principal.getFullName()).isEqualTo("Alex Trader");
        assertThat(principal.getUser()).isSameAs(user);
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_USER");
    }
}
