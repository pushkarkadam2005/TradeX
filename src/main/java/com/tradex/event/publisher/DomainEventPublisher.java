package com.tradex.event.publisher;

import com.tradex.event.model.DomainEvent;

public interface DomainEventPublisher {
    void publish(DomainEvent<?> event);
}
