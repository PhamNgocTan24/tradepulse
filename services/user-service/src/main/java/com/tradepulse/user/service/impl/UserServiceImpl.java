package com.tradepulse.user.service.impl;

import com.tradepulse.user.domain.entity.UserProfile;
import com.tradepulse.user.dto.request.UpdateProfileRequest;
import com.tradepulse.user.dto.response.LeaderboardEntry;
import com.tradepulse.user.dto.response.UserProfileResponse;
import com.tradepulse.user.exception.UserNotFoundException;
import com.tradepulse.user.repository.UserProfileRepository;
import com.tradepulse.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String LEADERBOARD_KEY = "leaderboard";
    private static final String PORTFOLIO_VALUE_PREFIX = "portfolio_value:";

    private final UserProfileRepository userProfileRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = findOrThrow(userId);
        return toResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = findOrThrow(userId);

        if (request.displayName() != null) profile.setDisplayName(request.displayName());
        if (request.avatarUrl() != null)    profile.setAvatarUrl(request.avatarUrl());

        return toResponse(userProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID userId) {
        return findOrThrow(userId).getVirtualBalance();
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(int topN) {
        // Primary: Redis sorted set — ZREVRANGE leaderboard 0 (topN-1) WITHSCORES
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, topN - 1L);

        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            String userId = entry.getValue();
            Double score = entry.getScore();
            String displayName = fetchDisplayName(userId);
            result.add(new LeaderboardEntry(
                    rank++,
                    UUID.fromString(userId),
                    displayName,
                    score != null ? BigDecimal.valueOf(score) : BigDecimal.ZERO
            ));
        }
        return result;
    }

    @Override
    @Transactional
    public void createProfile(UUID userId, String email) {
        if (userProfileRepository.existsById(userId)) {
            log.warn("Profile already exists for userId={}, skipping", userId);
            return;
        }
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .email(email)
                .build();
        userProfileRepository.save(profile);
        log.info("Profile created: userId={}", userId);
    }

    // --- helpers ---

    private UserProfile findOrThrow(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private String fetchDisplayName(String userId) {
        return userProfileRepository.findById(UUID.fromString(userId))
                .map(p -> p.getDisplayName() != null ? p.getDisplayName() : p.getEmail())
                .orElse(userId);
    }

    private UserProfileResponse toResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getUserId(), p.getEmail(), p.getDisplayName(),
                p.getAvatarUrl(), p.getVirtualBalance(), p.getCreatedAt());
    }
}
