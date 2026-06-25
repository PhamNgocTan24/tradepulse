package com.tradepulse.auth.repository;

import com.tradepulse.auth.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByOauth2ProviderAndOauth2Subject(String provider, String subject);
}
