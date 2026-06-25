package com.tradepulse.reporting.service.impl;

import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.reporting.domain.entity.Report;
import com.tradepulse.reporting.domain.enums.ReportStatus;
import com.tradepulse.reporting.dto.request.GenerateReportRequest;
import com.tradepulse.reporting.dto.response.ReportResponse;
import com.tradepulse.reporting.exception.ReportNotFoundException;
import com.tradepulse.reporting.repository.ReportRepository;
import com.tradepulse.reporting.service.PdfGeneratorService;
import com.tradepulse.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    @Value("${report.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

    private final ReportRepository reportRepository;
    private final PdfGeneratorService pdfGeneratorService;

    @Override
    @Transactional
    public ReportResponse requestReport(UUID userId, GenerateReportRequest request) {
        Report report = Report.builder()
                .userId(userId)
                .reportType(request.reportType())
                .status(ReportStatus.REQUESTED)
                .build();
        reportRepository.save(report);

        // Async: generate PDF immediately (in production would queue to SQS → Lambda)
        // For local dev, generate inline and mark completed
        try {
            report.setStatus(ReportStatus.GENERATING);
            reportRepository.save(report);
            String s3Key = pdfGeneratorService.generate(report);
            markCompleted(report.getId(), s3Key);
        } catch (Exception e) {
            log.error("PDF generation failed for reportId={}", report.getId(), e);
            markFailed(report.getId(), e.getMessage());
        }

        return toResponse(reportRepository.findById(report.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId, UUID userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        // Refresh pre-signed URL on each read (it may have expired)
        if (report.getStatus() == ReportStatus.COMPLETED && report.getS3Key() != null) {
            String freshUrl = pdfGeneratorService.generatePresignedUrl(
                    report.getS3Key(), presignedUrlExpiryMinutes);
            report.setPresignedUrl(freshUrl);
        }
        return toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listReports(UUID userId, int page, int size) {
        Page<Report> result = reportRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(this::toResponse).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    @Transactional
    public void markCompleted(UUID reportId, String s3Key) {
        reportRepository.findById(reportId).ifPresent(r -> {
            r.setStatus(ReportStatus.COMPLETED);
            r.setS3Key(s3Key);
            r.setPresignedUrl(pdfGeneratorService.generatePresignedUrl(
                    s3Key, presignedUrlExpiryMinutes));
            reportRepository.save(r);
            log.info("Report completed: reportId={}, s3Key={}", reportId, s3Key);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID reportId, String errorMessage) {
        reportRepository.findById(reportId).ifPresent(r -> {
            r.setStatus(ReportStatus.FAILED);
            r.setErrorMessage(errorMessage);
            reportRepository.save(r);
            log.warn("Report failed: reportId={}, error={}", reportId, errorMessage);
        });
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(r.getId(), r.getUserId(), r.getReportType(),
                r.getStatus(), r.getPresignedUrl(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
