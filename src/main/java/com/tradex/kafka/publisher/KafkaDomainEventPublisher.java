package com.tradex.kafka.publisher;

import com.tradex.event.model.DomainEvent;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.kafka.config.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    public KafkaDomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void publish(DomainEvent<?> event) {
        if (event == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
        } else {
            doPublish(event);
        }
    }

    public void doPublish(DomainEvent<?> event) {
        try {
            String key = event.aggregateId() != null ? event.aggregateId().toString() : event.eventId().toString();
            kafkaTemplate.send(kafkaProperties.getTopic(), key, event);
            log.info("Domain event published successfully to Kafka. Topic: {}, EventId: {}, Type: {}, AggregateId: {}",
                kafkaProperties.getTopic(), event.eventId(), event.eventType(), event.aggregateId());
        } catch (Exception e) {
            log.error("Kafka domain event publication failed gracefully. EventId: {}, Type: {}, AggregateId: {}, Error: {}",
                event.eventId(), event.eventType(), event.aggregateId(), e.getMessage());
            // Intentionally catch exception so Kafka downtime never breaks financial transaction execution
        }
    }
}
