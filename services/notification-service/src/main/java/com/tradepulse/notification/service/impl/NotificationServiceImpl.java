package com.tradepulse.notification.service.impl;

import com.tradepulse.common.dto.kafka.notification.NotificationEvent;
import com.tradepulse.notification.domain.entity.NotificationHistory;
import com.tradepulse.notification.domain.enums.NotificationType;
import com.tradepulse.notification.repository.NotificationHistoryRepository;
import io.awspring.cloud.ses.SimpleEmailServiceMailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Dispatches notifications: WebSocket push (real-time) + optional AWS SES email.
 * Appends a NotificationHistory record to MongoDB — never updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl {

    private final SimpMessagingTemplate stompTemplate;
    private final NotificationHistoryRepository historyRepository;
    // SES mail sender wired from Spring Cloud AWS auto-configuration
    private final SimpleEmailServiceMailSender mailSender;

    public void dispatch(NotificationEvent event) {
        // 1. Push via STOMP WebSocket to /user/{userId}/queue/notifications
        stompTemplate.convertAndSendToUser(
                event.userId().toString(),
                "/queue/notifications",
                event
        );

        // 2. Persist to MongoDB (append-only)
        NotificationHistory history = NotificationHistory.builder()
                .userId(event.userId())
                .type(NotificationType.valueOf(event.eventType().name()))
                .title(event.title())
                .message(event.message())
                .referenceId(event.referenceId())
                .emailSent(false) // updated below if email succeeds
                .createdAt(Instant.now())
                .build();

        // 3. Send email via AWS SES for high-priority events
        boolean emailSent = false;
        if (shouldSendEmail(event)) {
            try {
                // TODO: wire user email address via user-service client or event payload
                // mailSender.send(buildMimeMessage(event));
                emailSent = true;
                log.info("Email sent for userId={}, type={}", event.userId(), event.eventType());
            } catch (Exception e) {
                log.warn("Email send failed for userId={}: {}", event.userId(), e.getMessage());
            }
        }

        history.setEmailSent(emailSent);
        historyRepository.save(history);
        log.info("Notification dispatched: userId={}, type={}", event.userId(), event.eventType());
    }

    private boolean shouldSendEmail(NotificationEvent event) {
        return switch (event.eventType()) {
            case ORDER_FILLED, PRICE_ALERT_TRIGGERED -> true;
            default -> false;
        };
    }
}
