package com.example.hr_management_backend.features.email.repository;

import com.example.hr_management_backend.features.email.model.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @Query("SELECT e FROM EmailOutbox e WHERE e.status = 'PENDING' AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) ORDER BY e.id ASC")
    List<EmailOutbox> findPendingEmails(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    /**
     * PostgreSQL FOR UPDATE SKIP LOCKED: Atomic, non-blocking queue dequeue.
     * Prevents race conditions and duplicate email dispatches across multiple application replicas.
     */
    @Query(value = "SELECT id FROM email_outbox " +
            "WHERE (status = 'PENDING' OR (status = 'PROCESSING' AND updated_at < :staleThreshold)) " +
            "  AND (next_retry_at IS NULL OR next_retry_at <= :now) " +
            "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> claimPendingIds(@Param("now") LocalDateTime now,
                               @Param("staleThreshold") LocalDateTime staleThreshold,
                               @Param("limit") int limit);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE EmailOutbox e SET e.status = 'PROCESSING', e.updatedAt = :now WHERE e.id IN :ids")
    int markAsProcessing(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    List<EmailOutbox> findAllByIdIn(List<Long> ids);
}
