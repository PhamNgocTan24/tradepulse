package com.tradepulse.auth.service;

import com.tradepulse.auth.dto.response.TotpSetupResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService("my-super-secret-key-32-bytes-long!");
    }

    @Test
    void testEncryptDecrypt() {
        String plainText = "JBSWY3DPEHPK3PXP";
        String encrypted = totpService.encrypt(plainText);
        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);

        String decrypted = totpService.decrypt(encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    void testGenerateSetup() {
        for (int i = 0; i < 10000; i++) {
            TotpService.TotpGenerateResult result = totpService.generateSetup("test@example.com");
            assertNotNull(result);
            assertNotNull(result.secretBase32());
            assertEquals(32, result.secretBase32().length(), "Failed at iteration " + i + " with secret: " + result.secretBase32());
            assertFalse(result.secretBase32().contains("="));
        }
    }

    @Test
    void testIsValid() {
        TotpService.TotpGenerateResult result = totpService.generateSetup("test@example.com");
        String plainSecret = result.secretBase32();
        String encryptedSecret = totpService.encrypt(plainSecret);

        byte[] secretBytes = new org.apache.commons.codec.binary.Base32().decode(plainSecret);
        javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(secretBytes, "RAW");

        try {
            com.eatthepath.otp.TimeBasedOneTimePasswordGenerator generator = new com.eatthepath.otp.TimeBasedOneTimePasswordGenerator();
            int otp = generator.generateOneTimePassword(key, java.time.Instant.now());
            String otpStr = String.format("%06d", otp);

            assertTrue(totpService.isValid(encryptedSecret, otpStr));
        } catch (Exception e) {
            fail("Exception during OTP generation: " + e.getMessage());
        }
    }
}
