package com.tradepulse.portfolio.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry — one record per fill.
 * Never UPDATE; append-only to maintain audit trail.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_user_id", columnList = "user_id"),
        @Index(name = "idx_txn_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 4)
    private String side;   // BUY or SELL

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    /** Total trade value = quantity × price. */
    @Column(name = "total_value", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalValue;

    /**
     * Realized P&L for SELL fills only. NULL for BUY fills.
     * Formula: totalValue - (avgCostBasis × quantity)
     */
    @Column(name = "realized_pnl", precision = 18, scale = 8)
    private BigDecimal realizedPnl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
