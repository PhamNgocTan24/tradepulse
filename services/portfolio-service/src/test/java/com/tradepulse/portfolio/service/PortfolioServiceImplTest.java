package com.tradepulse.portfolio.service;

import com.tradepulse.common.dto.kafka.portfolio.PortfolioEvent;
import com.tradepulse.common.dto.kafka.portfolio.PortfolioEventType;
import com.tradepulse.portfolio.domain.entity.Holding;
import com.tradepulse.portfolio.domain.entity.PortfolioAccount;
import com.tradepulse.portfolio.domain.entity.Transaction;
import com.tradepulse.portfolio.exception.PortfolioAccountNotFoundException;
import com.tradepulse.portfolio.repository.HoldingRepository;
import com.tradepulse.portfolio.repository.PortfolioAccountRepository;
import com.tradepulse.portfolio.repository.TransactionRepository;
import com.tradepulse.portfolio.service.impl.PortfolioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PortfolioServiceImpl.
 * StringRedisTemplate is tricky to mock on newer JVMs, so we use interface-typed mocks
 * for the sub-operations (ValueOperations, ZSetOperations) and stub the template accordingly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PortfolioServiceImpl Unit Tests")
class PortfolioServiceImplTest {

    @Mock private PortfolioAccountRepository accountRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private TransactionRepository transactionRepository;

    // Mock the sub-operation interfaces, not StringRedisTemplate itself
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ZSetOperations<String, String> zSetOps;

    private StringRedisTemplate redisTemplate;
    private PortfolioServiceImpl portfolioService;

    private UUID userId;
    private UUID orderId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        // Use a subclass stub to avoid mocking the final-ish StringRedisTemplate class
        redisTemplate = new StringRedisTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }

            @Override
            @SuppressWarnings("unchecked")
            public ZSetOperations<String, String> opsForZSet() {
                return zSetOps;
            }
        };

        portfolioService = new PortfolioServiceImpl(
                accountRepository, holdingRepository, transactionRepository, redisTemplate);
    }

    // ─── BUY FILL TESTS ───────────────────────────────────────────────────────

    @Test
    @DisplayName("BUY fill: deducts cashBalance and creates new holding with correct qty and avgCostBasis")
    void applyFill_buy_newHolding_createsCorrectHolding() {
        // GIVEN
        PortfolioAccount account = PortfolioAccount.builder()
                .userId(userId)
                .cashBalance(new BigDecimal("100000.00000000"))
                .build();
        PortfolioEvent event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, orderId, userId,
                "BTCUSDT", "BUY", new BigDecimal("0.50000000"), new BigDecimal("67000.00000000"),
                Instant.now());

        given(accountRepository.findById(userId)).willReturn(Optional.of(account));
        given(holdingRepository.findByUserIdAndSymbol(userId, "BTCUSDT")).willReturn(Optional.empty());
        given(holdingRepository.findByUserId(userId)).willReturn(List.of());
        // Redis price for holdings value computation
        given(valueOps.get(anyString())).willReturn(null);

        // WHEN
        portfolioService.applyFill(event);

        // THEN — cash deducted: 100000 - (0.5 × 67000) = 66500
        ArgumentCaptor<PortfolioAccount> accountCaptor = ArgumentCaptor.forClass(PortfolioAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getCashBalance())
                .isEqualByComparingTo(new BigDecimal("66500.00000000"));

        // Holding created with correct qty and avgCostBasis
        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity())
                .isEqualByComparingTo(new BigDecimal("0.50000000"));
        assertThat(holdingCaptor.getValue().getAvgCostBasis())
                .isEqualByComparingTo(new BigDecimal("67000.00000000"));

        // Transaction persisted with null realizedPnl (BUY)
        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getRealizedPnl()).isNull();
        assertThat(txnCaptor.getValue().getSide()).isEqualTo("BUY");
    }

    @Test
    @DisplayName("BUY fill: updates avgCostBasis correctly using weighted average when existing holding exists")
    void applyFill_buy_existingHolding_updatesWeightedAvgCost() {
        // GIVEN — existing 1 BTC at avg 60000; buy 1 more at 70000
        Holding existingHolding = Holding.builder()
                .userId(userId).symbol("BTCUSDT")
                .quantity(new BigDecimal("1.00000000"))
                .avgCostBasis(new BigDecimal("60000.00000000"))
                .build();
        PortfolioAccount account = PortfolioAccount.builder()
                .userId(userId)
                .cashBalance(new BigDecimal("70000.00000000"))
                .build();
        PortfolioEvent event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, orderId, userId,
                "BTCUSDT", "BUY", new BigDecimal("1.00000000"), new BigDecimal("70000.00000000"),
                Instant.now());

        given(accountRepository.findById(userId)).willReturn(Optional.of(account));
        given(holdingRepository.findByUserIdAndSymbol(userId, "BTCUSDT"))
                .willReturn(Optional.of(existingHolding));
        given(holdingRepository.findByUserId(userId)).willReturn(List.of(existingHolding));
        given(valueOps.get(anyString())).willReturn("70000.00");

        // WHEN
        portfolioService.applyFill(event);

        // THEN — new avgCostBasis = (1×60000 + 1×70000) / 2 = 65000
        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity())
                .isEqualByComparingTo(new BigDecimal("2.00000000"));
        assertThat(holdingCaptor.getValue().getAvgCostBasis())
                .isEqualByComparingTo(new BigDecimal("65000.00000000"));
    }

    // ─── SELL FILL TESTS ──────────────────────────────────────────────────────

    @Test
    @DisplayName("SELL fill: credits cashBalance, reduces holding qty, and computes realizedPnl")
    void applyFill_sell_computesRealizedPnlAndReducesHolding() {
        // GIVEN — 1 BTC at avg 60000; sell 0.5 BTC at 70000
        Holding existingHolding = Holding.builder()
                .userId(userId).symbol("BTCUSDT")
                .quantity(new BigDecimal("1.00000000"))
                .avgCostBasis(new BigDecimal("60000.00000000"))
                .build();
        PortfolioAccount account = PortfolioAccount.builder()
                .userId(userId)
                .cashBalance(new BigDecimal("40000.00000000"))
                .build();
        PortfolioEvent event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, orderId, userId,
                "BTCUSDT", "SELL", new BigDecimal("0.50000000"), new BigDecimal("70000.00000000"),
                Instant.now());

        given(accountRepository.findById(userId)).willReturn(Optional.of(account));
        given(holdingRepository.findByUserIdAndSymbol(userId, "BTCUSDT"))
                .willReturn(Optional.of(existingHolding));
        given(holdingRepository.findByUserId(userId)).willReturn(List.of(existingHolding));
        given(valueOps.get(anyString())).willReturn("70000.00");

        // WHEN
        portfolioService.applyFill(event);

        // THEN — cash credited: 40000 + (0.5 × 70000) = 75000
        ArgumentCaptor<PortfolioAccount> accountCaptor = ArgumentCaptor.forClass(PortfolioAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getCashBalance())
                .isEqualByComparingTo(new BigDecimal("75000.00000000"));

        // Holding qty reduced: 1.0 - 0.5 = 0.5
        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity())
                .isEqualByComparingTo(new BigDecimal("0.50000000"));

        // Realized P&L = 35000 - (0.5 × 60000) = 35000 - 30000 = 5000
        ArgumentCaptor<Transaction> txnCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getRealizedPnl())
                .isEqualByComparingTo(new BigDecimal("5000.00000000"));
        assertThat(txnCaptor.getValue().getSide()).isEqualTo("SELL");
    }

    // ─── ERROR PATH TESTS ─────────────────────────────────────────────────────

    @Test
    @DisplayName("applyFill throws PortfolioAccountNotFoundException when account is missing")
    void applyFill_missingAccount_throwsException() {
        PortfolioEvent event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, orderId, userId,
                "ETHUSDT", "BUY", new BigDecimal("1.00000000"), new BigDecimal("3500.00000000"),
                Instant.now());

        given(accountRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.applyFill(event))
                .isInstanceOf(PortfolioAccountNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verifyNoInteractions(transactionRepository);
        verify(holdingRepository, never()).save(any());
    }

    // ─── REDIS TESTS ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyFill writes portfolio_value and leaderboard to Redis after every fill")
    void applyFill_writesRedisPortfolioValueAndLeaderboard() {
        PortfolioAccount account = PortfolioAccount.builder()
                .userId(userId)
                .cashBalance(new BigDecimal("50000.00000000"))
                .build();
        PortfolioEvent event = new PortfolioEvent(
                eventId, PortfolioEventType.ORDER_FILLED, orderId, userId,
                "SOLUSDT", "BUY", new BigDecimal("10.00000000"), new BigDecimal("150.00000000"),
                Instant.now());

        given(accountRepository.findById(userId)).willReturn(Optional.of(account));
        given(holdingRepository.findByUserIdAndSymbol(userId, "SOLUSDT")).willReturn(Optional.empty());
        given(holdingRepository.findByUserId(userId)).willReturn(List.of());
        given(valueOps.get(anyString())).willReturn(null);

        portfolioService.applyFill(event);

        verify(valueOps).set(eq("portfolio_value:" + userId), anyString(), any());
        verify(zSetOps).add(eq("leaderboard"), eq(userId.toString()), anyDouble());
    }
}
