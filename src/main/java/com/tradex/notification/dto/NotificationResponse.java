package com.tradex.notification.dto;

import com.tradex.notification.entity.Notification;
import com.tradex.notification.enums.NotificationStatus;
import com.tradex.notification.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID userId,
    NotificationType type,
    String title,
    String message,
    NotificationStatus status,
    UUID eventId,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationResponse fromEntity(Notification entity) {
        return new NotificationResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getType(),
            entity.getTitle(),
            entity.getMessage(),
            entity.getStatus(),
            entity.getEventId(),
            entity.getCreatedAt(),
            entity.getReadAt()
        );
    }
}
