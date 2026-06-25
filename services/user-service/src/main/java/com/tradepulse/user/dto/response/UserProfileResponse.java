package com.tradepulse.user.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String email,
        String displayName,
        String avatarUrl,
        BigDecimal virtualBalance,
        Instant createdAt
) {}
