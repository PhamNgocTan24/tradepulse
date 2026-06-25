package com.tradepulse.reporting.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.tradepulse.reporting.domain.entity.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;

/**
 * Generates PDF reports using iText7 and uploads them to S3.
 * Returns the S3 key and a pre-signed URL for client download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGeneratorService {

    @Value("${spring.cloud.aws.s3.bucket:tradepulse-reports}")
    private String bucket;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    /**
     * Generates the PDF in memory with iText7, uploads to S3, returns the S3 key.
     * TODO: inject portfolio/transaction data via RestTemplate/WebClient calls
     *       to portfolio-service for real report content.
     */
    public String generate(Report report) {
        String s3Key = buildS3Key(report);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            // Report header
            doc.add(new Paragraph("TradePulse Portfolio Report")
                    .setFontSize(20).setBold());
            doc.add(new Paragraph("User ID: " + report.getUserId()));
            doc.add(new Paragraph("Report Type: " + report.getReportType()));
            doc.add(new Paragraph("Generated At: " + Instant.now()));
            doc.add(new Paragraph("\n"));
            doc.add(new Paragraph(
                    "Portfolio holdings and transaction data will be rendered here.")
                    .setItalic());

            doc.close();

            // Upload to S3
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType("application/pdf")
                            .build(),
                    RequestBody.fromBytes(baos.toByteArray())
            );

            log.info("PDF uploaded to S3: bucket={}, key={}", bucket, s3Key);
            return s3Key;

        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    /** Generates a pre-signed GET URL valid for the given number of minutes. */
    public String generatePresignedUrl(String s3Key, int expiryMinutes) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expiryMinutes))
                        .getObjectRequest(r -> r.bucket(bucket).key(s3Key))
                        .build()
        );
        return presigned.url().toString();
    }

    private String buildS3Key(Report report) {
        return String.format("reports/%s/%s/%s.pdf",
                report.getUserId(), report.getReportType().toLowerCase(), report.getId());
    }
}
