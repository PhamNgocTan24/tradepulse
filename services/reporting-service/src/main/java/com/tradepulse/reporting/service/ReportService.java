package com.tradepulse.reporting.service;

import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.reporting.dto.request.GenerateReportRequest;
import com.tradepulse.reporting.dto.response.ReportResponse;

import java.util.UUID;

public interface ReportService {

    /**
     * Accepts a report request, persists metadata (REQUESTED), and queues to SQS.
     * Returns immediately; client polls GET /reports/{id} for status.
     */
    ReportResponse requestReport(UUID userId, GenerateReportRequest request);

    ReportResponse getReport(UUID reportId, UUID userId);

    PageResponse<ReportResponse> listReports(UUID userId, int page, int size);

    /**
     * Called when SQS/Lambda signals completion.
     * Updates status to COMPLETED, stores S3 key, and generates a fresh pre-signed URL.
     */
    void markCompleted(UUID reportId, String s3Key);

    void markFailed(UUID reportId, String errorMessage);
}
