package com.tradepulse.reporting.domain.entity;

import com.tradepulse.reporting.domain.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Report metadata stored in PostgreSQL.
 * The actual PDF binary is stored in S3; only the S3 key is persisted here.
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_user_id", columnList = "user_id"),
        @Index(name = "idx_reports_status",  columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "report_type", nullable = false, length = 50)
    @Builder.Default
    private String reportType = "PORTFOLIO_SUMMARY";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.REQUESTED;

    /** S3 object key — set when status = COMPLETED. */
    @Column(name = "s3_key", length = 500)
    private String s3Key;

    /** Pre-signed URL returned to client. Short-lived — not stored long term. */
    @Column(name = "presigned_url", length = 1000)
    private String presignedUrl;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
