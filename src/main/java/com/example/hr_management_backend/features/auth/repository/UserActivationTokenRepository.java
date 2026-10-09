package com.example.hr_management_backend.features.auth.repository;

import com.example.hr_management_backend.features.auth.model.UserActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserActivationTokenRepository extends JpaRepository<UserActivationToken, Long> {

    Optional<UserActivationToken> findFirstByActivationKeyHashAndUsedFalseAndExpiresAtAfter(
            String activationKeyHash, LocalDateTime now);

    @Modifying
    @Query("UPDATE UserActivationToken u SET u.used = true WHERE u.userId = :userId AND u.used = false")
    void invalidateActiveTokensForUser(@Param("userId") Long userId);
}
