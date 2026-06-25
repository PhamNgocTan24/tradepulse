package com.tradepulse.reporting.dto.response;

import com.tradepulse.reporting.domain.enums.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID userId,
        String reportType,
        ReportStatus status,
        /** Populated only when status = COMPLETED. Expires after 15 minutes. */
        String presignedUrl,
        Instant createdAt,
        Instant updatedAt
) {}
