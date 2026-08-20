package com.tradex.event.model;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Instant occurredAt,
    int version,
    T payload
) {
    public static <T> DomainEvent<T> of(EventType eventType, String aggregateType, UUID aggregateId, T payload) {
        return new DomainEvent<>(
            UUID.randomUUID(),
            eventType.name(),
            aggregateType,
            aggregateId,
            Instant.now(),
            1,
            payload
        );
    }

    public static <T> DomainEvent<T> of(String eventType, String aggregateType, UUID aggregateId, T payload) {
        return new DomainEvent<>(
            UUID.randomUUID(),
            eventType,
            aggregateType,
            aggregateId,
            Instant.now(),
            1,
            payload
        );
    }
}
