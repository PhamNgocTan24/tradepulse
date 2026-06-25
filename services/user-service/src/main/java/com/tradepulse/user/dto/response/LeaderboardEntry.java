package com.tradepulse.user.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardEntry(
        int rank,
        UUID userId,
        String displayName,
        BigDecimal portfolioValue
) {}
