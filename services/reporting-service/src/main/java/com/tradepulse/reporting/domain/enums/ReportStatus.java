package com.tradepulse.reporting.domain.enums;

public enum ReportStatus {
    REQUESTED,    // job accepted, queued to SQS
    GENERATING,   // Lambda / worker picked up the job
    COMPLETED,    // PDF uploaded to S3; pre-signed URL available
    FAILED        // generation error
}
