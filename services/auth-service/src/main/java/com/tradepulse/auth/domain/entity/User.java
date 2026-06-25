package com.tradepulse.auth.domain.entity;

import com.tradepulse.auth.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Core auth identity stored in PostgreSQL.
 * Holds credentials, OAuth2 provider info, TOTP secret, and role.
 * Profile data (display name, avatar) lives in user-service.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt-hashed password. Null for OAuth2-only users. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    /** Google OAuth2 subject identifier. Null for email/password users. */
    @Column(name = "oauth2_provider")
    private String oauth2Provider;

    @Column(name = "oauth2_subject")
    private String oauth2Subject;

    /** AES-256 encrypted TOTP secret. Null until user enables 2FA. */
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private boolean totpEnabled = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
