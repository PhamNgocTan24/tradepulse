package com.tradepulse.common.dto.kafka.notification;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for the 'notifications' topic.
 * Partitioned by user_id.
 * Producer:  notification-service (self-produced after alert evaluation)
 * Consumer:  notification-service (dispatcher → WebSocket + email)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationEvent(
        UUID eventId,
        NotificationEventType eventType,
        UUID userId,
        String title,
        String message,
        String referenceId,   // orderId or alertId as string
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
