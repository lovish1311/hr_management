package com.example.hr_management_backend.features.banners.repository;

import com.example.hr_management_backend.features.banners.model.BannerReaction;
import com.example.hr_management_backend.features.banners.model.BannerReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BannerReactionRepository extends JpaRepository<BannerReaction, Long> {

    /**
     * Native atomic insertion with ON CONFLICT DO NOTHING.
     * Prevents race condition exceptions when a user double-taps an emoji on flaky mobile connections.
     * Returns 1 if inserted, 0 if conflict already existed.
     */
    @Modifying
    @Query(value = "INSERT INTO banner_reactions (banner_id, employee_id, reaction_type, created_at) " +
                   "VALUES (:bannerId, :employeeId, :reactionType, NOW()) " +
                   "ON CONFLICT (banner_id, employee_id, reaction_type) DO NOTHING", nativeQuery = true)
    int insertReactionOnConflictDoNothing(@Param("bannerId") Long bannerId,
                                          @Param("employeeId") Long employeeId,
                                          @Param("reactionType") String reactionType);

    /**
     * Removes a reaction when toggling off.
     */
    @Modifying
    @Query("DELETE FROM BannerReaction r WHERE r.bannerId = :bannerId AND r.employeeId = :employeeId AND r.reactionType = :reactionType")
    int deleteByBannerIdAndEmployeeIdAndReactionType(@Param("bannerId") Long bannerId,
                                                     @Param("employeeId") Long employeeId,
                                                     @Param("reactionType") BannerReactionType reactionType);

    /**
     * Checks if a reaction already exists for the given banner, employee, and reaction type.
     */
    boolean existsByBannerIdAndEmployeeIdAndReactionType(Long bannerId, Long employeeId, BannerReactionType reactionType);

    /**
     * Batch Query 1: Grouped counts per banner and reaction type across a collection of banner IDs.
     * Eliminates 2N+1 queries when loading feeds.
     */
    @Query("SELECT r.bannerId AS bannerId, r.reactionType AS reactionType, COUNT(r) AS count " +
           "FROM BannerReaction r WHERE r.bannerId IN :bannerIds " +
           "GROUP BY r.bannerId, r.reactionType")
    List<BannerReactionCountProjection> countReactionsByBannerIds(@Param("bannerIds") Collection<Long> bannerIds);

    /**
     * Batch Query 2: Caller-specific reactions for the logged-in employee across a collection of banner IDs.
     */
    @Query("SELECT r FROM BannerReaction r WHERE r.bannerId IN :bannerIds AND r.employeeId = :employeeId")
    List<BannerReaction> findByBannerIdsAndEmployeeId(@Param("bannerIds") Collection<Long> bannerIds,
                                                      @Param("employeeId") Long employeeId);

    /**
     * Retrieves all reactions for a specific banner (for single banner summary lookup).
     */
    @Query("SELECT r FROM BannerReaction r WHERE r.bannerId = :bannerId")
    List<BannerReaction> findByBannerId(@Param("bannerId") Long bannerId);

    /**
     * Removes all reactions associated with a banner upon deletion.
     */
    @Modifying
    @Query("DELETE FROM BannerReaction r WHERE r.bannerId = :bannerId")
    void deleteByBannerId(@Param("bannerId") Long bannerId);

    /**
     * Batch removes all reactions for multiple banners in a single query.
     */
    @Modifying
    @Query("DELETE FROM BannerReaction r WHERE r.bannerId IN :bannerIds")
    void deleteByBannerIdIn(@Param("bannerIds") Collection<Long> bannerIds);
}
