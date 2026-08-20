package com.tradex.auth.dto;

import com.tradex.user.dto.UserDto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresInMs,
    UserDto user
) {
    public AuthResponse(String accessToken, long expiresInMs, UserDto user) {
        this(accessToken, "Bearer", expiresInMs, user);
    }
}
