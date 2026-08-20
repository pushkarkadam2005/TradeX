package com.tradex.kafka;

import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.kafka.publisher.KafkaDomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class RollbackDoesNotPublishEventTest {

    @Autowired
    private KafkaDomainEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("Transaction Rollback Guarantee — ZERO Kafka domain events are published if PostgreSQL transaction rolls back")
    void transactionRollbackEmitsZeroEvents() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        UUID aggregateId = UUID.randomUUID();
        DomainEvent<Map<String, Object>> event = DomainEvent.of(EventType.ORDER_CREATED, "ORDER", aggregateId, Map.of("symbol", "AAPL"));

        try {
            txTemplate.executeWithoutResult(status -> {
                publisher.publish(event);
                status.setRollbackOnly();
            });
        } catch (Exception ignored) {}

        // Verify KafkaTemplate was NEVER called on transaction rollback
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
