package com.tradepulse.portfolio.service.impl;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.portfolio.domain.entity.Holding;
import com.tradepulse.portfolio.domain.entity.PortfolioAccount;
import com.tradepulse.portfolio.domain.entity.Transaction;
import com.tradepulse.portfolio.dto.response.HoldingResponse;
import com.tradepulse.portfolio.dto.response.PortfolioResponse;
import com.tradepulse.portfolio.repository.HoldingRepository;
import com.tradepulse.portfolio.repository.PortfolioAccountRepository;
import com.tradepulse.portfolio.repository.TransactionRepository;
import com.tradepulse.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private static final String PRICE_PREFIX     = "price:";
    private static final String PORTFOLIO_PREFIX = "portfolio_value:";
    private static final String LEADERBOARD_KEY  = "leaderboard";

    private final PortfolioAccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID userId) {
        PortfolioAccount account = accountRepository.findById(userId)
                .orElse(PortfolioAccount.builder().userId(userId).build());

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

        // Update Redis portfolio_value cache + leaderboard sorted set
        redisTemplate.opsForValue().set(PORTFOLIO_PREFIX + userId, totalValue.toPlainString());
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId.toString(),
                totalValue.doubleValue());

        return new PortfolioResponse(userId, account.getCashBalance(),
                holdingsValue, totalValue, totalPnl, responses);
    }

    @Override
    @Transactional
    public void applyFill(PortfolioEvent event) {
        UUID userId = event.userId();
        String symbol = event.symbol();
        BigDecimal qty = event.filledQuantity();
        BigDecimal price = event.fillPrice();
        boolean isBuy = "BUY".equalsIgnoreCase(event.side());

        PortfolioAccount account = accountRepository.findById(userId)
                .orElse(PortfolioAccount.builder().userId(userId).build());

        BigDecimal tradeValue = qty.multiply(price).setScale(8, RoundingMode.HALF_UP);

        if (isBuy) {
            // Debit cash
            account.setCashBalance(account.getCashBalance().subtract(tradeValue));

            // Update holding with weighted average cost basis
            Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                    .orElse(Holding.builder().userId(userId).symbol(symbol).build());

            BigDecimal existingCost = holding.getQuantity().multiply(holding.getAvgCostBasis());
            BigDecimal newTotalQty  = holding.getQuantity().add(qty);
            BigDecimal newAvgCost   = newTotalQty.compareTo(BigDecimal.ZERO) > 0
                    ? existingCost.add(tradeValue).divide(newTotalQty, 8, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            holding.setQuantity(newTotalQty);
            holding.setAvgCostBasis(newAvgCost);
            holdingRepository.save(holding);
        } else {
            // Credit cash
            account.setCashBalance(account.getCashBalance().add(tradeValue));

            // Reduce holding quantity
            holdingRepository.findByUserIdAndSymbol(userId, symbol).ifPresent(h -> {
                h.setQuantity(h.getQuantity().subtract(qty).max(BigDecimal.ZERO));
                holdingRepository.save(h);
            });
        }

        accountRepository.save(account);

        // Append immutable transaction ledger entry
        transactionRepository.save(Transaction.builder()
                .userId(userId).orderId(event.orderId()).symbol(symbol)
                .side(event.side()).quantity(qty).price(price).totalValue(tradeValue)
                .build());

        log.info("Fill applied: userId={}, symbol={}, side={}, qty={}, price={}",
                userId, symbol, event.side(), qty, price);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Transaction> getHistory(UUID userId, int page, int size) {
        Page<Transaction> result = transactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }

    /** Reads current price from Redis. Per CLAUDE.md: never query PostgreSQL for price. */
    private BigDecimal getLivePrice(String symbol) {
        String raw = redisTemplate.opsForValue().get(PRICE_PREFIX + symbol.toUpperCase());
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(raw); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
