package com.tradex.notification.repository;

import com.tradex.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);

    Optional<ProcessedEvent> findByEventId(UUID eventId);
}
