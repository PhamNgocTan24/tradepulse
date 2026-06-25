package com.tradepulse.notification.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Price alert configured by a user.
 * Evaluated on every market-data Kafka tick for the matching symbol.
 */
@Document(collection = "price_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert {

    @Id
    private String id;

    @Indexed
    private UUID userId;

    @Indexed
    private String symbol;

    /** ABOVE or BELOW */
    private String condition;

    private BigDecimal targetPrice;

    @Builder.Default
    private boolean triggered = false;

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;
    private Instant triggeredAt;
}
