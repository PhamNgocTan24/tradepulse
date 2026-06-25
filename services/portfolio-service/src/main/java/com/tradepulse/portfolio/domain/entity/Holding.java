package com.tradepulse.portfolio.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Current position for one symbol in a user's portfolio.
 * avgCostBasis is recalculated on each BUY fill (weighted average).
 */
@Entity
@Table(name = "holdings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "symbol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** Total quantity held. DECIMAL(18,8). */
    @Column(nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    /** Weighted average cost per unit. Used for P&L calculation. */
    @Column(name = "avg_cost_basis", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal avgCostBasis = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
