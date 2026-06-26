package com.tradepulse.user.service;

import com.tradepulse.user.domain.entity.UserProfile;
import com.tradepulse.user.dto.response.LeaderboardEntry;
import com.tradepulse.user.repository.UserProfileRepository;
import com.tradepulse.user.service.impl.UserServiceImpl;
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
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for UserServiceImpl — focused on leaderboard N+1 fix.
 *
 * <p>StringRedisTemplate and ZSetOperations cannot be mocked via Mockito inline mocks
 * on Java 26, so we use anonymous subclass stubs (same technique as PortfolioServiceImplTest).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ZSetOperations<String, String> zSetOps;

    private StringRedisTemplate redisTemplate;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // Anonymous subclass stub — avoids Mockito inline mock limitation on Java 26
        redisTemplate = new StringRedisTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public ZSetOperations<String, String> opsForZSet() {
                return zSetOps;
            }
        };
        userService = new UserServiceImpl(userProfileRepository, redisTemplate);
    }

    // ─── LEADERBOARD TESTS ───────────────────────────────────────────────────

    @Test
    @DisplayName("getLeaderboard: returns empty list when Redis ZSet is empty")
    void getLeaderboard_emptyRedis_returnsEmptyList() {
        // GIVEN
        given(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(Set.of());

        // WHEN
        List<LeaderboardEntry> result = userService.getLeaderboard(10);

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getLeaderboard: calls userProfileRepository exactly ONCE for batch name lookup — no N+1")
    void getLeaderboard_batchesProfileLookup_singleDbCall() {
        // GIVEN — Redis ZSet with 3 traders
        UUID trader1 = UUID.randomUUID();
        UUID trader2 = UUID.randomUUID();
        UUID trader3 = UUID.randomUUID();

        // Use LinkedHashSet to preserve insertion order for rank assertion
        Set<ZSetOperations.TypedTuple<String>> redisEntries = new LinkedHashSet<>();
        redisEntries.add(tuple(trader1.toString(), 150_000.0));
        redisEntries.add(tuple(trader2.toString(), 120_000.0));
        redisEntries.add(tuple(trader3.toString(), 90_000.0));

        given(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(redisEntries);

        // Batch profiles returned by repo (one call)
        List<UserProfile> profiles = List.of(
                profile(trader1, "Alice", "alice@test.com"),
                profile(trader2, null, "bob@test.com"),    // displayName null — falls back to email
                profile(trader3, "Charlie", "charlie@test.com")
        );
        given(userProfileRepository.findAllByUserIdIn(Set.of(trader1, trader2, trader3)))
                .willReturn(profiles);

        // WHEN
        List<LeaderboardEntry> result = userService.getLeaderboard(10);

        // THEN — batch repo called exactly ONCE (no N+1)
        ArgumentCaptor<Set<UUID>> idsCaptor = ArgumentCaptor.captor();
        verify(userProfileRepository).findAllByUserIdIn(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(trader1, trader2, trader3);

        // 3 entries returned
        assertThat(result).hasSize(3);

        // Rank 1 has highest score
        LeaderboardEntry first = result.get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.userId()).isEqualTo(trader1);
        assertThat(first.displayName()).isEqualTo("Alice");
        assertThat(first.portfolioValue()).isEqualByComparingTo(new BigDecimal("150000.0"));

        // Rank 2 falls back to email when displayName is null
        LeaderboardEntry second = result.get(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(second.displayName()).isEqualTo("bob@test.com");
    }

    @Test
    @DisplayName("getLeaderboard: uses userId string as fallback display name when profile not found")
    void getLeaderboard_missingProfile_fallsBackToUserIdString() {
        UUID trader = UUID.randomUUID();
        Set<ZSetOperations.TypedTuple<String>> redisEntries = Set.of(tuple(trader.toString(), 50_000.0));
        given(zSetOps.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(redisEntries);

        // Profile NOT found in DB
        given(userProfileRepository.findAllByUserIdIn(Set.of(trader)))
                .willReturn(List.of());

        List<LeaderboardEntry> result = userService.getLeaderboard(10);

        assertThat(result).hasSize(1);
        // Falls back to userId string when profile is missing
        assertThat(result.get(0).displayName()).isEqualTo(trader.toString());
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private ZSetOperations.TypedTuple<String> tuple(String value, double score) {
        return new ZSetOperations.TypedTuple<>() {
            @Override public String getValue() { return value; }
            @Override public Double getScore() { return score; }
            @Override public int compareTo(ZSetOperations.TypedTuple<String> o) {
                return Double.compare(score, o.getScore());
            }
        };
    }

    private UserProfile profile(UUID id, String displayName, String email) {
        UserProfile p = new UserProfile();
        p.setUserId(id);
        p.setDisplayName(displayName);
        p.setEmail(email);
        return p;
    }
}
