package com.tradepulse.reporting.controller;

import com.tradepulse.common.dto.response.ApiResponse;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.reporting.dto.request.GenerateReportRequest;
import com.tradepulse.reporting.dto.response.ReportResponse;
import com.tradepulse.reporting.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ReportResponse>> generateReport(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) GenerateReportRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (request == null) request = new GenerateReportRequest();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Report generation started",
                        reportService.requestReport(userId, request)));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportResponse>> getReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(reportService.getReport(reportId, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReportResponse>>> listReports(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(reportService.listReports(userId, page, size)));
    }
}
