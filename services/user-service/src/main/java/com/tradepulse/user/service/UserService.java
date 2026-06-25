package com.tradepulse.user.service;

import com.tradepulse.user.dto.request.UpdateProfileRequest;
import com.tradepulse.user.dto.response.LeaderboardEntry;
import com.tradepulse.user.dto.response.UserProfileResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    BigDecimal getBalance(UUID userId);

    /** Returns the global leaderboard from the Redis sorted set. */
    List<LeaderboardEntry> getLeaderboard(int topN);

    /** Called by Kafka consumer when USER_REGISTERED event is received. */
    void createProfile(UUID userId, String email);
}
