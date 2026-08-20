package com.tradex.kafka;

import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.kafka.config.KafkaProperties;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PostCommitEventPublishingTest {

    @Autowired
    private KafkaDomainEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private KafkaProperties kafkaProperties;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("Post-Commit Event Guarantee — Event is published to Kafka ONLY AFTER database transaction successfully commits")
    void eventPublishedOnlyAfterTransactionCommit() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        UUID aggregateId = UUID.randomUUID();
        DomainEvent<Map<String, Object>> event = DomainEvent.of(EventType.ORDER_CREATED, "ORDER", aggregateId, Map.of("symbol", "AAPL"));

        txTemplate.executeWithoutResult(status -> {
            publisher.publish(event);
            // Verify KafkaTemplate has NOT been called inside active transaction prior to commit
            verify(kafkaTemplate, never()).send(any(), any(), any());
        });

        // After transaction commits, verify KafkaTemplate was called
        verify(kafkaTemplate).send(eq(kafkaProperties.getTopic()), eq(aggregateId.toString()), eq(event));
    }
}
