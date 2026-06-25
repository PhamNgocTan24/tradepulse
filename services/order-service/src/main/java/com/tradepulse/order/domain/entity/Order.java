package com.tradepulse.order.domain.entity;

import com.tradepulse.order.domain.enums.OrderSide;
import com.tradepulse.order.domain.enums.OrderStatus;
import com.tradepulse.order.domain.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order stored in PostgreSQL (tradepulse_orders).
 * All monetary fields use DECIMAL(18,8) — never double/float.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_symbol", columnList = "symbol"),
        @Index(name = "idx_orders_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    /** Null for MARKET orders. */
    @Column(precision = 18, scale = 8)
    private BigDecimal price;

    @Column(name = "filled_quantity", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "average_fill_price", precision = 18, scale = 8)
    private BigDecimal averageFillPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
