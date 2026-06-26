package com.tradepulse.portfolio.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency guard for the portfolio-events Kafka consumer.
 * Each successfully processed Kafka event is recorded here by its eventId.
 * A UNIQUE constraint on event_id prevents double-processing on replay or rebalance.
 */
@Entity
@Table(name = "event_log",
       uniqueConstraints = @UniqueConstraint(name = "uk_event_log_event_id", columnNames = "event_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable identifier from the upstream Kafka event. Used for deduplication. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
