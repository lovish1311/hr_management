package com.example.hr_management_backend.features.auth.repository;

import com.example.hr_management_backend.features.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findFirstByEmailAndTokenHashAndUsedFalseAndExpiresAtAfter(
            String email, String tokenHash, LocalDateTime now);

    Optional<PasswordResetToken> findFirstByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, LocalDateTime now);

    Optional<PasswordResetToken> findTopByEmailOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.email = :email AND p.used = false")
    void invalidateActiveTokensForEmail(@Param("email") String email);

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.attemptCount = p.attemptCount + 1 WHERE p.id = :id")
    int incrementAttemptCount(@Param("id") Long id);

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.id = :id")
    void markTokenUsed(@Param("id") Long id);
}
