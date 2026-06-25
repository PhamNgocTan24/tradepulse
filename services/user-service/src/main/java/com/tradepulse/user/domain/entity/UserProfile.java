package com.tradepulse.user.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * User profile and virtual trading account.
 * Created automatically when auth-service publishes USER_REGISTERED.
 * userId matches the UUID issued by auth-service.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Virtual cash balance in USD. Initial allocation: $100,000.
     * Uses DECIMAL(18,8) — never double or float.
     */
    @Column(name = "virtual_balance", nullable = false,
            precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal virtualBalance = new BigDecimal("100000.00000000");

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
