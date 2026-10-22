package com.example.hr_management_backend.features.banners.repository;

import com.example.hr_management_backend.features.banners.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    /**
     * Query active candidate banners that have not expired.
     * In-memory filtering will evaluate start_date and target_department
     * to eliminate stale cache delays and department cache collisions.
     */
    @Query("SELECT b FROM Banner b WHERE b.isActive = true AND b.expiryDate >= :now ORDER BY b.displayOrder ASC, b.createdAt DESC")
    List<Banner> findActiveCandidates(@Param("now") LocalDateTime now);

    /**
     * For Admin Management Console: retrieve all banners (active, scheduled, expired)
     */
    List<Banner> findAllByOrderByCreatedAtDesc();

    /**
     * Counts active, unexpired banners to enforce the system concurrency cap (max 15 banners).
     */
    long countByIsActiveTrueAndExpiryDateGreaterThanEqual(LocalDateTime now);
}
