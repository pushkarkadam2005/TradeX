package com.tradex.auth;

import com.tradex.auth.dto.AuthResponse;
import com.tradex.auth.dto.LoginRequest;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.auth.service.AuthService;
import com.tradex.auth.service.JwtService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.InvalidCredentialsException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userService, passwordEncoder, jwtService, 86400000);
    }

    @Test
    @DisplayName("Register successful — encodes password with BCrypt, saves user, generates JWT")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest("newuser@tradex.com", "secret123", "New User");
        given(userService.existsByEmail("newuser@tradex.com")).willReturn(false);

        User savedUser = new User("newuser@tradex.com", "encodedPassword", "New User", Role.ROLE_USER);
        savedUser.setId(UUID.randomUUID());
        given(userService.save(any(User.class))).willReturn(savedUser);
        given(jwtService.generateToken(savedUser)).willReturn("mocked.jwt.token");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.user().email()).isEqualTo("newuser@tradex.com");
        verify(userService).save(any(User.class));
    }

    @Test
    @DisplayName("Register duplicate email — throws BusinessRuleViolationException EMAIL_ALREADY_EXISTS")
    void registerDuplicateEmailThrowsException() {
        RegisterRequest request = new RegisterRequest("existing@tradex.com", "secret123", "Existing User");
        given(userService.existsByEmail("existing@tradex.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Login successful — matches BCrypt password and generates JWT")
    void loginSuccess() {
        String plainPassword = "password123";
        String encodedPassword = passwordEncoder.encode(plainPassword);
        User user = new User("user@tradex.com", encodedPassword, "User Name", Role.ROLE_USER);
        user.setId(UUID.randomUUID());

        given(userService.findByEmail("user@tradex.com")).willReturn(user);
        given(jwtService.generateToken(user)).willReturn("mocked.jwt.token");

        LoginRequest request = new LoginRequest("user@tradex.com", plainPassword);
        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mocked.jwt.token");
    }

    @Test
    @DisplayName("Login invalid password — throws InvalidCredentialsException 401")
    void loginInvalidPasswordThrowsException() {
        String encodedPassword = passwordEncoder.encode("correctPassword");
        User user = new User("user@tradex.com", encodedPassword, "User Name", Role.ROLE_USER);

        given(userService.findByEmail("user@tradex.com")).willReturn(user);

        LoginRequest request = new LoginRequest("user@tradex.com", "wrongPassword");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Login unknown user email — throws InvalidCredentialsException 401 without revealing non-existence")
    void loginUnknownUserThrowsException() {
        given(userService.findByEmail("nonexistent@tradex.com"))
            .willThrow(new ResourceNotFoundException("User", "email", "nonexistent@tradex.com"));

        LoginRequest request = new LoginRequest("nonexistent@tradex.com", "anyPassword");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessageContaining("Invalid email or password");
    }
}
