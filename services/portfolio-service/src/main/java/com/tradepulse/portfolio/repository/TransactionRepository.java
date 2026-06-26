package com.tradepulse.portfolio.repository;

import com.tradepulse.portfolio.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Sums all non-null {@code realizedPnl} values for SELL fills belonging to the given user.
     * Returns {@link BigDecimal#ZERO} when no SELL fills exist yet.
     * Used to populate {@code totalRealizedPnl} in portfolio response.
     */
    @Query("SELECT COALESCE(SUM(t.realizedPnl), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.realizedPnl IS NOT NULL")
    BigDecimal sumRealizedPnlByUserId(@Param("userId") UUID userId);
}
