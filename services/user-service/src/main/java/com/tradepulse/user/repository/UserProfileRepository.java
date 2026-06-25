package com.tradepulse.user.repository;

import com.tradepulse.user.domain.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmail(String email);

    /** Returns the top N users by virtual balance for the leaderboard fallback. */
    @Query("SELECT u FROM UserProfile u ORDER BY u.virtualBalance DESC LIMIT :limit")
    List<UserProfile> findTopByBalance(int limit);
}
