package com.tradex.notification.service;

import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.notification.dto.NotificationResponse;
import com.tradex.notification.entity.Notification;
import com.tradex.notification.entity.ProcessedEvent;
import com.tradex.notification.enums.NotificationType;
import com.tradex.notification.repository.NotificationRepository;
import com.tradex.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EmailNotificationService emailNotificationService;

    public NotificationService(NotificationRepository notificationRepository,
                               ProcessedEventRepository processedEventRepository,
                               EmailNotificationService emailNotificationService) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.emailNotificationService = emailNotificationService;
    }

    public Optional<Notification> createNotificationIdempotent(UUID userId, NotificationType type, String title,
                                                              String message, UUID eventId) {
        if (notificationRepository.existsByUserIdAndEventIdAndType(userId, eventId, type)) {
            log.info("Idempotent notification skip for user {} and eventId {}", userId, eventId);
            return notificationRepository.findByUserIdAndEventIdAndType(userId, eventId, type);
        }

        try {
            Notification saved = doSaveNotification(userId, type, title, message, eventId);
            emailNotificationService.send(saved);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate notification insertion caught by DB constraint for eventId {}", eventId);
            for (int i = 0; i < 5; i++) {
                Optional<Notification> existing = notificationRepository.findByUserIdAndEventIdAndType(userId, eventId, type);
                if (existing.isPresent()) {
                    return existing;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
            return notificationRepository.findByUserIdAndEventIdAndType(userId, eventId, type);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification doSaveNotification(UUID userId, NotificationType type, String title, String message, UUID eventId) {
        Notification notification = new Notification(userId, type, title, message, eventId);
        Notification saved = notificationRepository.saveAndFlush(notification);

        if (!processedEventRepository.existsByEventId(eventId)) {
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, "NotificationConsumer"));
            } catch (DataIntegrityViolationException ignored) {}
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable)
            .map(NotificationResponse::fromEntity);
    }

    @Transactional
    public NotificationResponse markNotificationAsRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));

        if (!notification.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("ACCESS_DENIED", "You are not authorized to access this notification");
        }

        notification.markAsRead();
        Notification updated = notificationRepository.save(notification);
        return NotificationResponse.fromEntity(updated);
    }
}
