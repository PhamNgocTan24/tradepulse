package com.tradepulse.portfolio.dto.response;

import com.tradepulse.portfolio.domain.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe HTTP response view of a {@link Transaction}.
 *
 * <p>JPA entities MUST NOT be serialized directly as response bodies.
 * This record provides a stable, versioned view decoupled from the persistence model.
 *
 * <p>Monetary fields: all {@link BigDecimal}, DECIMAL(18,8) precision.
 * {@code realizedPnl} is {@code null} for BUY fills; populated for SELL fills only.
 */
public record TransactionResponse(
        UUID id,
        UUID orderId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal totalValue,
        BigDecimal realizedPnl,   // null for BUY fills
        Instant createdAt
) {
    /**
     * Maps a {@link Transaction} entity to this response record.
     *
     * @param t the transaction entity (must not be null)
     * @return a TransactionResponse populated from the entity's fields
     */
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getOrderId(),
                t.getSymbol(),
                t.getSide(),
                t.getQuantity(),
                t.getPrice(),
                t.getTotalValue(),
                t.getRealizedPnl(),
                t.getCreatedAt()
        );
    }
}
