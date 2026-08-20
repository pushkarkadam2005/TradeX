package com.tradex.notification.repository;

import com.tradex.notification.entity.Notification;
import com.tradex.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndEventIdAndType(UUID userId, UUID eventId, NotificationType type);

    Optional<Notification> findByUserIdAndEventIdAndType(UUID userId, UUID eventId, NotificationType type);
}
