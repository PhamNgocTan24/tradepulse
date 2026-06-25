package com.tradepulse.reporting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GenerateReportRequest(
        /** Currently only PORTFOLIO_SUMMARY is supported; extensible for future types. */
        @NotBlank @Pattern(regexp = "PORTFOLIO_SUMMARY|TRANSACTION_HISTORY")
        String reportType
) {
    public GenerateReportRequest() {
        this("PORTFOLIO_SUMMARY");
    }
}
