package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;

import java.util.List;

public interface BannerService {

    /**
     * Retrieves active banners suitable for the current employee's department.
     * Evaluated using Caffeine cached candidates with in-memory temporal checks (0ms lag).
     */
    List<BannerDto> getActiveBanners(String userDepartment);

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
}
