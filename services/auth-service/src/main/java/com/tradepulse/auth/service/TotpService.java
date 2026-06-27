package com.tradepulse.auth.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.tradepulse.auth.dto.response.TotpSetupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;

/**
 * TOTP (RFC 6238) service using java-otp.
 * Secrets are Base32-encoded; in production, encrypt with AES-256 before storing.
 */
@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "TradePulse";
    private static final int QR_SIZE = 200;

    private final TimeBasedOneTimePasswordGenerator totpGenerator;
    private final byte[] aesKeyBytes;

    public record TotpGenerateResult(
            String secretBase32,
            TotpSetupResponse setupResponse
    ) {}

    public TotpService(@Value("${totp.encryption-key:my-super-secret-key-32-bytes-long!}") String encryptionKey) {
        try {
            this.totpGenerator = new TimeBasedOneTimePasswordGenerator();
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            this.aesKeyBytes = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise TOTP generator or key derivation", e);
        }
    }

    /**
     * Generates a new TOTP setup for the user.
     * The secret is returned as Base32; persist it (encrypted) in the users table.
     */
    public TotpGenerateResult generateSetup(String email) {
        try {
            // Generate 20-byte random secret
            byte[] secretBytes = new byte[20];
            new java.security.SecureRandom().nextBytes(secretBytes);

            // Encode to Base32 and strip padding '=' characters
            String secretBase32 = new org.apache.commons.codec.binary.Base32().encodeToString(secretBytes).replace("=", "");

            String otpAuthUri = buildOtpAuthUri(email, secretBase32);
            String qrCodeBase64 = generateQrCode(otpAuthUri);

            return new TotpGenerateResult(secretBase32, new TotpSetupResponse(otpAuthUri, qrCodeBase64));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP setup", e);
        }
    }

    /** Encrypts the raw Base32 secret using AES-256 before storing it. */
    public String encrypt(String plainText) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(aesKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt TOTP secret", e);
        }
    }

    /** Decrypts the AES-256 encrypted secret. */
    public String decrypt(String encryptedText) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(aesKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt TOTP secret", e);
        }
    }

    /** Validates a 6-digit TOTP code against the stored (AES-256 encrypted) secret. */
    public boolean isValid(String encryptedSecret, String totpCode) {
        if (encryptedSecret == null || totpCode == null) return false;
        try {
            String plainSecretBase32 = decrypt(encryptedSecret);
            byte[] secretBytes = new org.apache.commons.codec.binary.Base32().decode(plainSecretBase32);
            Key key = new SecretKeySpec(secretBytes, totpGenerator.getAlgorithm());
            int expected = totpGenerator.generateOneTimePassword(key, Instant.now());
            return String.format("%06d", expected).equals(totpCode.trim());
        } catch (Exception e) {
            log.warn("TOTP validation error: {}", e.getMessage());
            return false;
        }
    }

    private String buildOtpAuthUri(String email, String secret) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        String encodedIssuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
                encodedIssuer, encodedEmail, secret, encodedIssuer);
    }

    private String generateQrCode(String content) throws Exception {
        BitMatrix matrix = new MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
