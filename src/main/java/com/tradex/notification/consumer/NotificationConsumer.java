package com.tradex.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.notification.enums.NotificationType;
import com.tradex.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${tradex.kafka.topic:tradex.domain.events}",
        groupId = "${spring.kafka.consumer.group-id:tradex-notification-group}"
    )
    public void consumeDomainEvent(DomainEvent<?> event) {
        if (event == null || event.eventType() == null) {
            return;
        }

        log.info("Kafka NotificationConsumer received domain event: EventId: {}, Type: {}, AggregateId: {}",
            event.eventId(), event.eventType(), event.aggregateId());

        try {
            processEventPayload(event);
        } catch (Exception e) {
            log.error("Error processing domain event in NotificationConsumer: EventId: {}, Error: {}", event.eventId(), e.getMessage());
            throw new RuntimeException("Notification processing failure for eventId " + event.eventId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processEventPayload(DomainEvent<?> event) {
        EventType eventType;
        try {
            eventType = EventType.valueOf(event.eventType());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized eventType '{}' in NotificationConsumer. Skipping.", event.eventType());
            return;
        }

        Map<String, Object> payloadMap = (event.payload() instanceof Map) ? (Map<String, Object>) event.payload() : objectMapper.convertValue(event.payload(), Map.class);
        if (payloadMap == null) {
            return;
        }

        Object userIdObj = payloadMap.get("userId");
        if (userIdObj == null) {
            log.info("Event payload has no userId. EventId: {}. Skipping notification.", event.eventId());
            return;
        }

        UUID userId = UUID.fromString(userIdObj.toString());
        NotificationType type = NotificationType.valueOf(eventType.name());
        String title = buildTitle(eventType, payloadMap);
        String message = buildMessage(eventType, payloadMap);

        notificationService.createNotificationIdempotent(userId, type, title, message, event.eventId());
    }

    private String buildTitle(EventType type, Map<String, Object> payload) {
        return switch (type) {
            case ORDER_CREATED -> "Order Placed Successfully";
            case ORDER_CANCELLED -> "Order Cancelled";
            case ORDER_PARTIALLY_FILLED -> "Order Partially Filled";
            case ORDER_FILLED -> "Order Completely Filled";
            case TRADE_EXECUTED -> "Trade Executed";
            case DEPOSIT_COMPLETED -> "Deposit Account Credit";
            case BUY_SETTLEMENT_COMPLETED -> "BUY Trade Settled";
            case SELL_SETTLEMENT_COMPLETED -> "SELL Trade Settled";
            case RISK_ORDER_REJECTED -> "Pre-Trade Risk Rejection";
            case KYC_SUBMITTED -> "KYC Verification Submitted";
            case KYC_VERIFIED -> "KYC Verification Approved";
            case KYC_REJECTED -> "KYC Verification Rejected";
            case AML_REVIEW_REQUIRED -> "Compliance Review Flagged";
            case AML_BLOCKED -> "Account Transaction Blocked";
            case WITHDRAWAL_REQUESTED -> "Withdrawal Request Submitted";
            case WITHDRAWAL_APPROVED -> "Withdrawal Approved";
            case WITHDRAWAL_REJECTED -> "Withdrawal Request Rejected";
            case WITHDRAWAL_COMPLETED -> "Withdrawal Payout Completed";
            case WITHDRAWAL_FAILED -> "Withdrawal Payout Failed";
        };
    }

    private String buildMessage(EventType type, Map<String, Object> payload) {
        Object symbol = payload.getOrDefault("symbol", "N/A");
        Object quantity = payload.getOrDefault("quantity", "N/A");
        Object price = payload.getOrDefault("price", "N/A");
        Object amount = payload.getOrDefault("amount", "N/A");
        Object reason = payload.getOrDefault("reason", "N/A");
        return "Notification for " + type + " (Amount: $" + amount + ", Symbol: " + symbol + ", Reason: " + reason + ")";
    }
}
