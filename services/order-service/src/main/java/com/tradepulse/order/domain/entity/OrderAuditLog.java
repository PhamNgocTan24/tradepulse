package com.tradepulse.order.domain.entity;

import com.tradepulse.order.domain.enums.OrderStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log stored in MongoDB (order_audit_log collection).
 * Every state transition creates a new document — never UPDATE existing records.
 */
@Document(collection = "order_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAuditLog {

    @Id
    private String id;

    @Indexed
    private UUID orderId;

    @Indexed
    private UUID userId;

    private String symbol;
    private String side;
    private String orderType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal filledQuantity;
    private BigDecimal averageFillPrice;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private String eventSource;   // "USER_REQUEST" | "MATCHING_ENGINE"

    @Indexed
    private Instant timestamp;
}
