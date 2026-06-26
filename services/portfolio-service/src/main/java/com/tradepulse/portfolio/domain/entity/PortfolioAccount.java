package com.tradepulse.portfolio.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Virtual cash account — tracks available USD balance per user.
 * Debited on BUY fills, credited on SELL fills.
 */
@Entity
@Table(name = "portfolio_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioAccount {

    @Id
    private UUID userId;

    /** Optimistic locking — prevents concurrent fills from overwriting cashBalance. */
    @Version
    private long version;

    /** Available virtual cash. DECIMAL(18,8). Never double/float. */
    @Column(name = "cash_balance", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal cashBalance = new BigDecimal("100000.00000000");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
