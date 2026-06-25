package com.tradepulse.notification.service.impl;

import com.tradepulse.common.dto.kafka.notification.NotificationEvent;
import com.tradepulse.common.dto.kafka.notification.NotificationEventType;
import com.tradepulse.notification.domain.entity.PriceAlert;
import com.tradepulse.notification.dto.request.CreateAlertRequest;
import com.tradepulse.notification.dto.response.AlertResponse;
import com.tradepulse.notification.repository.AlertRepository;
import com.tradepulse.notification.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Override
    public AlertResponse createAlert(UUID userId, CreateAlertRequest request) {
        PriceAlert alert = PriceAlert.builder()
                .userId(userId)
                .symbol(request.symbol().toUpperCase())
                .condition(request.condition())
                .targetPrice(request.targetPrice())
                .createdAt(Instant.now())
                .build();
        return toResponse(alertRepository.save(alert));
    }

    @Override
    public void deleteAlert(String alertId, UUID userId) {
        alertRepository.findById(alertId).ifPresent(alert -> {
            if (alert.getUserId().equals(userId)) {
                alert.setActive(false);
                alertRepository.save(alert);
            }
        });
    }

    @Override
    public List<AlertResponse> getAlerts(UUID userId) {
        return alertRepository.findByUserIdAndActiveTrue(userId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public void evaluateAlerts(String symbol, BigDecimal currentPrice) {
        List<PriceAlert> alerts =
                alertRepository.findBySymbolAndActiveTrueAndTriggeredFalse(symbol);

        for (PriceAlert alert : alerts) {
            boolean fires = ("ABOVE".equals(alert.getCondition())
                    && currentPrice.compareTo(alert.getTargetPrice()) >= 0)
                    || ("BELOW".equals(alert.getCondition())
                    && currentPrice.compareTo(alert.getTargetPrice()) <= 0);

            if (fires) {
                alert.setTriggered(true);
                alert.setTriggeredAt(Instant.now());
                alertRepository.save(alert);

                // Self-produce to 'notifications' topic → NotificationConsumer fans out
                NotificationEvent event = new NotificationEvent(
                        UUID.randomUUID(), NotificationEventType.PRICE_ALERT_TRIGGERED,
                        alert.getUserId(),
                        "Price Alert Triggered",
                        String.format("%s hit %s %s (current: %s)",
                                symbol, alert.getCondition(), alert.getTargetPrice(), currentPrice),
                        alert.getId(),
                        Instant.now()
                );
                kafkaTemplate.send("notifications", alert.getUserId().toString(), event);
                log.info("Alert fired: userId={}, symbol={}, condition={}, target={}",
                        alert.getUserId(), symbol, alert.getCondition(), alert.getTargetPrice());
            }
        }
    }

    private AlertResponse toResponse(PriceAlert a) {
        return new AlertResponse(a.getId(), a.getUserId(), a.getSymbol(),
                a.getCondition(), a.getTargetPrice(), a.isTriggered(),
                a.isActive(), a.getCreatedAt(), a.getTriggeredAt());
    }
}
