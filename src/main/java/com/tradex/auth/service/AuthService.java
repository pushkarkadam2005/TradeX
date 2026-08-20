package com.tradex.auth.service;

import com.tradex.auth.dto.AuthResponse;
import com.tradex.auth.dto.LoginRequest;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.InvalidCredentialsException;
import com.tradex.user.dto.UserDto;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthService(
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${tradex.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userService.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("EMAIL_ALREADY_EXISTS", "Email address is already registered");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User newUser = new User(
            request.email().toLowerCase().trim(),
            encodedPassword,
            request.fullName().trim(),
            Role.ROLE_USER
        );

        User savedUser = userService.save(newUser);
        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(token, expirationMs, UserDto.fromEntity(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (Exception e) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new InvalidCredentialsException("User account is disabled");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, expirationMs, UserDto.fromEntity(user));
    }
}
