package com.tradepulse.reporting.exception;

import java.util.UUID;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(UUID reportId) {
        super("Report not found: " + reportId);
    }
}
