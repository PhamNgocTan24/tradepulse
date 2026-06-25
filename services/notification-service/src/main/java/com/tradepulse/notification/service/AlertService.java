package com.tradepulse.notification.service;

import com.tradepulse.notification.dto.request.CreateAlertRequest;
import com.tradepulse.notification.dto.response.AlertResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AlertService {

    AlertResponse createAlert(UUID userId, CreateAlertRequest request);

    void deleteAlert(String alertId, UUID userId);

    List<AlertResponse> getAlerts(UUID userId);

    /** Called by MarketDataConsumer on each tick. Evaluates and fires matching alerts. */
    void evaluateAlerts(String symbol, BigDecimal currentPrice);
}
