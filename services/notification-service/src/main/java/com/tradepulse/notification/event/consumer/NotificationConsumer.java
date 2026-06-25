package com.tradepulse.notification.event.consumer;

import com.tradepulse.common.dto.kafka.notification.NotificationEvent;
import com.tradepulse.notification.service.impl.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes 'notifications' topic — fans out to WebSocket and AWS SES email.
 * Partitioned by user_id for ordered delivery per user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationServiceImpl notificationService;

    @KafkaListener(topics = "notifications",
                   groupId = "notification-dispatcher",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onNotification(NotificationEvent event) {
        log.info("Dispatching notification: type={}, userId={}", event.eventType(), event.userId());
        notificationService.dispatch(event);
    }
}
