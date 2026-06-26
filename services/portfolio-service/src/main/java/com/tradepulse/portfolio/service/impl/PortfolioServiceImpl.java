package com.tradepulse.portfolio.service.impl;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.portfolio.domain.entity.Holding;
import com.tradepulse.portfolio.domain.entity.PortfolioAccount;
import com.tradepulse.portfolio.domain.entity.Transaction;
import com.tradepulse.portfolio.dto.response.HoldingResponse;
import com.tradepulse.portfolio.dto.response.PortfolioResponse;
import com.tradepulse.portfolio.dto.response.TransactionResponse;
import com.tradepulse.portfolio.exception.PortfolioAccountNotFoundException;
import com.tradepulse.portfolio.repository.HoldingRepository;
import com.tradepulse.portfolio.repository.PortfolioAccountRepository;
import com.tradepulse.portfolio.repository.TransactionRepository;
import com.tradepulse.portfolio.service.PortfolioService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Core portfolio business logic.
 *
 * <p><b>CQRS split (Pattern #9):</b>
 * <ul>
 *   <li>Write path: {@link #applyFill} writes to PostgreSQL (holdings + transactions + account)
 *       and immediately refreshes Redis leaderboard + portfolio_value cache.</li>
 *   <li>Read path: {@link #getPortfolio} reads from PostgreSQL and live prices from Redis only —
 *       never queries another service's DB.</li>
 * </ul>
 *
 * <p><b>Monetary precision:</b> All money values use {@link BigDecimal} + DECIMAL(18,8). Never double/float.
 *
 * <p><b>Optimistic locking (Pattern #12):</b> {@link Holding} and {@link PortfolioAccount} carry
 * {@code @Version}. {@link #applyFill} is annotated with {@link Retryable} to handle concurrent fills
 * from the same user landing on different Kafka partitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final String PRICE_PREFIX     = "price:";
    private static final String PORTFOLIO_PREFIX = "portfolio_value:";
    private static final String LEADERBOARD_KEY  = "leaderboard";
    private static final int    PORTFOLIO_VALUE_TTL_SECONDS = 60;

    private final PortfolioAccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    // ─────────────────────────────────────────────────────────────────────────
    // READ PATH
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID userId) {
        PortfolioAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new PortfolioAccountNotFoundException(userId));

        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal holdingsValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;

        List<HoldingResponse> responses = holdings.stream()
                .filter(h -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(h -> {
                    BigDecimal currentPrice = getLivePrice(h.getSymbol());
                    BigDecimal currentValue = h.getQuantity().multiply(currentPrice)
                            .setScale(8, RoundingMode.HALF_UP);
                    BigDecimal costBasisTotal = h.getQuantity().multiply(h.getAvgCostBasis())
                            .setScale(8, RoundingMode.HALF_UP);
                    BigDecimal pnl = currentValue.subtract(costBasisTotal);
                    BigDecimal pnlPct = costBasisTotal.compareTo(BigDecimal.ZERO) > 0
                            ? pnl.divide(costBasisTotal, 8, RoundingMode.HALF_UP)
                                  .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    return new HoldingResponse(h.getSymbol(), h.getQuantity(),
                            h.getAvgCostBasis(), currentPrice, currentValue, pnl, pnlPct);
                })
                .toList();

        for (HoldingResponse hr : responses) {
            holdingsValue = holdingsValue.add(hr.currentValue());
            totalPnl = totalPnl.add(hr.unrealizedPnl());
        }

        BigDecimal totalValue = account.getCashBalance().add(holdingsValue)
                .setScale(8, RoundingMode.HALF_UP);

        // Cumulative realized P&L from all SELL fills — single aggregate query
        BigDecimal totalRealizedPnl = transactionRepository.sumRealizedPnlByUserId(userId);

        // Refresh Redis on explicit portfolio fetch (covers stale price reads)
        writeRedisPortfolioValue(userId, totalValue);

        return new PortfolioResponse(userId, account.getCashBalance(),
                holdingsValue, totalValue, totalPnl, totalRealizedPnl, responses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getHistory(UUID userId, int page, int size) {
        Page<Transaction> result = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        List<TransactionResponse> dtos = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        return PageResponse.of(dtos, page, size, result.getTotalElements());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE PATH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a portfolio fill event (BUY or SELL) from the matching engine.
     *
     * <p>This method is {@link Retryable} for {@link OptimisticLockException} — two concurrent
     * fills for the same user (from different Kafka partitions in theory) will cause one to retry
     * rather than overwriting the other's data.
     *
     * <p>Called by {@link com.tradepulse.portfolio.event.consumer.PortfolioEventConsumer}
     * inside its own {@code @Transactional} boundary.
     */
    @Retryable(
            retryFor = OptimisticLockException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Override
    @Transactional
    public void applyFill(PortfolioEvent event) {
        UUID userId     = event.userId();
        String symbol   = event.symbol();
        BigDecimal qty  = event.filledQuantity();
        BigDecimal price = event.fillPrice();
        boolean isBuy   = "BUY".equalsIgnoreCase(event.side());

        // Fail fast — never silently create phantom accounts
        PortfolioAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new PortfolioAccountNotFoundException(userId));

        BigDecimal tradeValue = qty.multiply(price).setScale(8, RoundingMode.HALF_UP);
        BigDecimal realizedPnl = null;

        if (isBuy) {
            applyBuy(userId, symbol, qty, price, tradeValue, account);
        } else {
            realizedPnl = applySell(userId, symbol, qty, tradeValue, account);
        }

        accountRepository.save(account);

        // Append immutable transaction ledger entry (never UPDATE — append-only)
        transactionRepository.save(Transaction.builder()
                .userId(userId)
                .orderId(event.orderId())
                .symbol(symbol)
                .side(event.side())
                .quantity(qty)
                .price(price)
                .totalValue(tradeValue)
                .realizedPnl(realizedPnl)
                .build());

        log.info("Fill applied: userId={}, symbol={}, side={}, qty={}, price={}, realizedPnl={}",
                userId, symbol, event.side(), qty, price, realizedPnl);

        // Update Redis portfolio_value cache and leaderboard after every fill
        updateRedisPortfolioValue(userId, account.getCashBalance());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Debit cash and upsert holding with weighted average cost basis. */
    private void applyBuy(UUID userId, String symbol, BigDecimal qty,
                          BigDecimal price, BigDecimal tradeValue,
                          PortfolioAccount account) {
        account.setCashBalance(account.getCashBalance().subtract(tradeValue));

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseGet(() -> Holding.builder().userId(userId).symbol(symbol).build());

        BigDecimal existingCost = holding.getQuantity().multiply(holding.getAvgCostBasis());
        BigDecimal newTotalQty  = holding.getQuantity().add(qty);
        BigDecimal newAvgCost   = newTotalQty.compareTo(BigDecimal.ZERO) > 0
                ? existingCost.add(tradeValue).divide(newTotalQty, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        holding.setQuantity(newTotalQty);
        holding.setAvgCostBasis(newAvgCost);
        holdingRepository.save(holding);
    }

    /**
     * Credit cash, reduce holding quantity, and compute realized P&L.
     *
     * @return realized P&L = tradeValue - costBasis of sold units
     */
    private BigDecimal applySell(UUID userId, String symbol, BigDecimal qty,
                                 BigDecimal tradeValue, PortfolioAccount account) {
        account.setCashBalance(account.getCashBalance().add(tradeValue));

        BigDecimal[] realizedPnl = { null };

        holdingRepository.findByUserIdAndSymbol(userId, symbol).ifPresent(h -> {
            BigDecimal costBasisForSoldQty = h.getAvgCostBasis().multiply(qty)
                    .setScale(8, RoundingMode.HALF_UP);
            realizedPnl[0] = tradeValue.subtract(costBasisForSoldQty)
                    .setScale(8, RoundingMode.HALF_UP);

            h.setQuantity(h.getQuantity().subtract(qty).max(BigDecimal.ZERO));
            holdingRepository.save(h);
        });

        return realizedPnl[0];
    }

    /**
     * Reads all current holdings, prices them from Redis, and writes the total portfolio value
     * to Redis ({@code portfolio_value:{userId}} with TTL 60s) and the leaderboard ZSet.
     *
     * <p>Per architecture rule: NEVER query PostgreSQL for real-time price — always use Redis.
     * A cache miss ({@code price:{SYMBOL}} expired) returns {@code BigDecimal.ZERO} for that holding.
     */
    private void updateRedisPortfolioValue(UUID userId, BigDecimal cashBalance) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        BigDecimal holdingsValue = holdings.stream()
                .filter(h -> h.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(h -> getLivePrice(h.getSymbol()).multiply(h.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);

        BigDecimal totalValue = cashBalance.add(holdingsValue).setScale(8, RoundingMode.HALF_UP);

        // Cache portfolio value (TTL 60s)
        redisTemplate.opsForValue().set(
                PORTFOLIO_PREFIX + userId,
                totalValue.toPlainString(),
                Duration.ofSeconds(PORTFOLIO_VALUE_TTL_SECONDS));

        // Update leaderboard sorted set
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId.toString(), totalValue.doubleValue());

        log.info("Redis portfolio updated: userId={}, totalValue={}", userId, totalValue);
    }

    /**
     * Reads live price from Redis ({@code price:{SYMBOL}}, TTL 30s, written by market-data-service).
     * Returns {@code BigDecimal.ZERO} on cache miss — never falls back to PostgreSQL.
     */
    private BigDecimal getLivePrice(String symbol) {
        String raw = redisTemplate.opsForValue().get(PRICE_PREFIX + symbol.toUpperCase());
        if (raw == null || raw.isBlank()) {
            log.warn("Price cache miss for symbol={}; defaulting to 0", symbol);
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.error("Malformed price value in Redis for symbol={}: '{}'", symbol, raw);
            return BigDecimal.ZERO;
        }
    }

    /** Writes total portfolio value to Redis (used by getPortfolio read path). */
    private void writeRedisPortfolioValue(UUID userId, BigDecimal totalValue) {
        redisTemplate.opsForValue().set(
                PORTFOLIO_PREFIX + userId,
                totalValue.toPlainString(),
                Duration.ofSeconds(PORTFOLIO_VALUE_TTL_SECONDS));
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId.toString(), totalValue.doubleValue());
    }
}
