package com.tradepulse.notification.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        String id,
        UUID userId,
        String symbol,
        String condition,
        BigDecimal targetPrice,
        boolean triggered,
        boolean active,
        Instant createdAt,
        Instant triggeredAt
) {}
