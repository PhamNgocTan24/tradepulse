package com.tradepulse.user.repository;

import com.tradepulse.user.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    /** Returns the top N users by virtual balance for the leaderboard fallback. */
    @Query("SELECT u FROM UserProfile u ORDER BY u.virtualBalance DESC LIMIT :limit")
    List<UserProfile> findTopByBalance(int limit);

    /**
     * Batch-loads profiles for a set of user IDs in a single SQL query.
     * Used by {@code getLeaderboard()} to avoid the N+1 pattern.
     * Spring Data JPA generates: {@code SELECT * FROM user_profiles WHERE user_id IN (...)}
     */
    List<UserProfile> findAllByUserIdIn(Set<UUID> userIds);
}
