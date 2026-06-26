package com.tradepulse.portfolio.repository;

import com.tradepulse.portfolio.domain.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for idempotency log records.
 * existsByEventId() is used in the Kafka consumer before processing each event.
 */
public interface EventLogRepository extends JpaRepository<EventLog, UUID> {

    boolean existsByEventId(UUID eventId);
}
