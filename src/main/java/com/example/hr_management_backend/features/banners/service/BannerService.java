package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.BannerReactionSummaryDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.BannerReactionType;
import com.example.hr_management_backend.features.banners.model.BannerType;
import com.example.hr_management_backend.features.banners.model.CelebrationType;

import java.util.List;

public interface BannerService {

    /**
     * Retrieves active banners suitable for the current employee's department.
     */
    default List<BannerDto> getActiveBanners(String userDepartment) {
        return getActiveBanners(userDepartment, null);
    }

    /**
     * Retrieves active banners with caller-specific reactions and aggregated counters.
     * Evaluated using Caffeine cached candidates with in-memory temporal checks (0ms lag).
     */
    List<BannerDto> getActiveBanners(String userDepartment, Long currentEmployeeId);

    /**
     * Retrieves feed for Engage Social Hub (/engage) filtered by banner type and celebration subtype.
     */
    default List<BannerDto> getEngageFeed(BannerType bannerType, CelebrationType celebrationType, Long currentEmployeeId) {
        return getEngageFeed(bannerType, celebrationType, null, currentEmployeeId);
    }

    /**
     * Retrieves feed for Engage Social Hub (/engage) filtered by banner type, celebration subtype, and user department.
     */
    List<BannerDto> getEngageFeed(BannerType bannerType, CelebrationType celebrationType, String userDepartment, Long currentEmployeeId);

    /**
     * Toggles social emoji reaction on a banner idempotently.
     * Returns authoritative BannerReactionSummaryDto with updated counts and caller reactions.
     */
    default BannerReactionSummaryDto toggleReaction(Long bannerId, BannerReactionType reactionType, Long employeeId) {
        return toggleReaction(bannerId, reactionType, employeeId, "TOGGLE");
    }

    /**
     * Mutates social emoji reaction on a banner idempotently based on action (ADD, REMOVE, TOGGLE).
     * Returns authoritative BannerReactionSummaryDto with updated counts and caller reactions.
     */
    BannerReactionSummaryDto toggleReaction(Long bannerId, BannerReactionType reactionType, Long employeeId, String action);

    /**
     * Retrieves all banners for administration / management console.
     */
    List<BannerDto> getAllBannersForManagement();

    /**
     * Retrieves a single banner by ID.
     */
    BannerDto getBannerById(Long id);

    /**
     * Creates a new promotional/celebration banner.
     * Enforces concurrency caps, date validation, anti-XSS tag stripping, and cache eviction.
     */
    BannerDto createBanner(CreateBannerRequest request, String actorEmail);

    /**
     * Updates an existing banner with optimistic locking.
     */
    BannerDto updateBanner(Long id, UpdateBannerRequest request, String actorEmail);

    /**
     * Deactivates/removes a banner.
     */
    void deleteBanner(Long id, String actorEmail);

    /**
     * Toggles banner active status.
     */
    BannerDto toggleBannerStatus(Long id, String actorEmail);

    /**
     * Retrieves unique active corporate departments for banner targeting.
     */
    List<String> getDistinctDepartments();
}
