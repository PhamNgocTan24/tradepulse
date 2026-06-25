package com.tradepulse.notification.domain.entity;

import com.tradepulse.notification.domain.enums.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every notification sent.
 * Never updated — append only.
 */
@Document(collection = "notification_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationHistory {

    @Id
    private String id;

    @Indexed
    private UUID userId;

    private NotificationType type;
    private String title;
    private String message;
    private String referenceId;

    /** Whether email was sent in addition to WebSocket push. */
    private boolean emailSent;

    @Indexed
    private Instant createdAt;
}
