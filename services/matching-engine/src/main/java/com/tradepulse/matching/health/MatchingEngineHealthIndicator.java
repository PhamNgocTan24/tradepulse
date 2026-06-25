package com.tradepulse.matching.health;

import com.tradepulse.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator reporting order book depth for key symbols.
 * Exposed at /actuator/health — used by K8s liveness/readiness probes.
 */
@Component
@RequiredArgsConstructor
public class MatchingEngineHealthIndicator implements HealthIndicator {

    private final MatchingService matchingService;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("btcusdt_bids", matchingService.getBidCount("BTCUSDT"))
                .withDetail("btcusdt_asks", matchingService.getAskCount("BTCUSDT"))
                .withDetail("ethusdt_bids", matchingService.getBidCount("ETHUSDT"))
                .withDetail("ethusdt_asks", matchingService.getAskCount("ETHUSDT"))
                .build();
    }
}
