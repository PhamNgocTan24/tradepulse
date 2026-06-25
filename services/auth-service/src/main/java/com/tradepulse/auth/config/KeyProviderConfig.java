package com.tradepulse.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Provides the RSA private and public keys for signing and validating JWTs.
 * For local development, generates an ephemeral 2048-bit RSA key pair.
 */
@Slf4j
@Configuration
public class KeyProviderConfig {

    private final KeyPair keyPair;

    public KeyProviderConfig() {
        log.info("Initializing JWT cryptographic key pair for local development...");
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            log.info("Successfully generated ephemeral 2048-bit RSA key pair.");
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate ephemeral RSA key pair", e);
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    @Bean
    public PrivateKey jwtPrivateKey() {
        return keyPair.getPrivate();
    }

    @Bean
    public PublicKey jwtPublicKey() {
        return keyPair.getPublic();
    }
}
