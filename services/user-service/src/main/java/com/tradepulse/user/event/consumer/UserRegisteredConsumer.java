package com.tradepulse.user.event.consumer;

import com.tradepulse.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes USER_REGISTERED events from auth-service.
 * Creates the user profile and seeds the $100,000 virtual balance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final UserService userService;

    @KafkaListener(topics = "user-events", groupId = "user-service",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onUserEvent(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> payload = record.value();
        String eventType = (String) payload.get("eventType");

        if (!"USER_REGISTERED".equals(eventType)) {
            return; // idempotency: ignore unknown event types
        }

        String userId = (String) payload.get("userId");
        String email  = (String) payload.get("email");

        if (userId == null || email == null) {
            log.warn("Received malformed USER_REGISTERED event: {}", payload);
            return;
        }

        log.info("Processing USER_REGISTERED: userId={}, email={}", userId, email);
        userService.createProfile(UUID.fromString(userId), email);
    }
}
