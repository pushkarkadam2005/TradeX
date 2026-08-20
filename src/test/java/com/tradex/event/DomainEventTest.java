package com.tradex.event;

import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTest {

    @Test
    @DisplayName("DomainEvent Envelope Creation — Builds immutable event envelope with UUID, timestamp, version, and payload")
    void domainEventCreation() {
        UUID aggregateId = UUID.randomUUID();
        DomainEvent<Map<String, Object>> event = DomainEvent.of(EventType.ORDER_CREATED, "ORDER", aggregateId, Map.of("symbol", "AAPL", "quantity", 100));

        assertThat(event.eventId()).isNotNull();
        assertThat(event.eventType()).isEqualTo("ORDER_CREATED");
        assertThat(event.aggregateType()).isEqualTo("ORDER");
        assertThat(event.aggregateId()).isEqualTo(aggregateId);
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.payload().get("symbol")).isEqualTo("AAPL");
    }
}
