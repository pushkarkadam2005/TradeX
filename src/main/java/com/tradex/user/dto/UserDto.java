package com.tradex.user.dto;

import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String fullName,
    Role role,
    Instant createdAt
) {
    public static UserDto fromEntity(User user) {
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
