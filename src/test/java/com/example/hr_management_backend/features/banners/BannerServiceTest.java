package com.example.hr_management_backend.features.banners;

import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.BannerCategory;
import com.example.hr_management_backend.features.banners.repository.BannerRepository;
import com.example.hr_management_backend.features.banners.service.BannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BannerServiceTest {

    @Autowired
    private BannerService bannerService;

    @Autowired
    private BannerRepository bannerRepository;

    @BeforeEach
    void setUp() {
        bannerRepository.deleteAll();
    }

    @Test
    @DisplayName("HTML tags are cleanly stripped to prevent Stored XSS while preserving text and emojis")
    void testCreateBanner_SanitizesHtmlTagsAndValidatesUrls() {
        LocalDateTime now = LocalDateTime.now();
        CreateBannerRequest request = CreateBannerRequest.builder()
                .title("<script>alert('xss')</script>Annual Tech Summit <b>2026</b> 🚀")
                .subtitle("<i>Join us</i> for exciting tech talks & prizes! 🏆")
                .category(BannerCategory.OFFICE_EVENT)
                .imageUrl("https://images.unsplash.com/photo-1540575467063-178a50c2df87")
                .actionUrl("https://company.internal/events/tech-summit")
                .actionButtonText("<marquee>RSVP Now</marquee>")
                .backgroundGradient("linear-gradient(135deg, #6366F1, #EC4899)")
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(3))
                .displayOrder(1)
                .isActive(true)
                .build();

        BannerDto created = bannerService.createBanner(request, "admin@company.com");
        assertNotNull(created.getId());
        assertEquals("Annual Tech Summit 2026 🚀", created.getTitle());
        assertEquals("Join us for exciting tech talks & prizes! 🏆", created.getSubtitle());
        assertEquals("RSVP Now", created.getActionButtonText());
        assertEquals("https://images.unsplash.com/photo-1540575467063-178a50c2df87", created.getImageUrl());
        assertTrue(created.isCurrentlyActive());
    }

    @Test
    @DisplayName("Dangerous URL schemes (javascript:, data:) are rejected with IllegalArgumentException")
    void testCreateBanner_RejectsDangerousUrlSchemes() {
        LocalDateTime now = LocalDateTime.now();
        CreateBannerRequest dangerousUrlRequest = CreateBannerRequest.builder()
                .title("Harmless Title")
                .category(BannerCategory.ANNOUNCEMENT)
                .imageUrl("javascript:stealCookies()")
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(1))
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                bannerService.createBanner(dangerousUrlRequest, "admin@company.com")
        );
    }

    @Test
    @DisplayName("Start date must be strictly before expiry date")
    void testCreateBanner_RejectsInvalidDates() {
        LocalDateTime now = LocalDateTime.now();
        CreateBannerRequest invalidDateRequest = CreateBannerRequest.builder()
                .title("Invalid Date Event")
                .category(BannerCategory.FESTIVAL)
                .startDate(now.plusDays(2))
                .expiryDate(now.plusDays(1)) // Before start date!
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                bannerService.createBanner(invalidDateRequest, "admin@company.com")
        );
    }

    @Test
    @DisplayName("Active banner query evaluates temporal window with 0ms lag (Scheduled and Expired excluded)")
    void testGetActiveBanners_TemporalWindowFiltering() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Current Active Banner
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Active Banner")
                .category(BannerCategory.CELEBRATION)
                .startDate(now.minusHours(2))
                .expiryDate(now.plusHours(5))
                .displayOrder(1)
                .isActive(true)
                .build(), "admin@company.com");

        // 2. Future Scheduled Banner (not active yet)
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Future Scheduled Banner")
                .category(BannerCategory.OFFICE_EVENT)
                .startDate(now.plusHours(2))
                .expiryDate(now.plusDays(2))
                .displayOrder(2)
                .isActive(true)
                .build(), "admin@company.com");

        // 3. Expired Banner
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Expired Banner")
                .category(BannerCategory.BIRTHDAY)
                .startDate(now.minusDays(5))
                .expiryDate(now.minusHours(1))
                .displayOrder(3)
                .isActive(true)
                .build(), "admin@company.com");

        // Query active banners for employee
        List<BannerDto> active = bannerService.getActiveBanners(null);
        assertEquals(1, active.size());
        assertEquals("Active Banner", active.get(0).getTitle());
        assertTrue(active.get(0).isCurrentlyActive());

        // Management query sees all 3
        List<BannerDto> all = bannerService.getAllBannersForManagement();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("Department targeting: Employees only see their department's banners plus global banners")
    void testGetActiveBanners_DepartmentTargeting() {
        LocalDateTime now = LocalDateTime.now();

        // Global Banner
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Global Townhall")
                .category(BannerCategory.ANNOUNCEMENT)
                .targetDepartment(null)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(1))
                .displayOrder(1)
                .build(), "admin@company.com");

        // Engineering Only
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Engineering Hackathon")
                .category(BannerCategory.OFFICE_EVENT)
                .targetDepartment("Engineering")
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(1))
                .displayOrder(2)
                .build(), "admin@company.com");

        // Sales Only
        bannerService.createBanner(CreateBannerRequest.builder()
                .title("Sales Kickoff")
                .category(BannerCategory.CELEBRATION)
                .targetDepartment("Sales")
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(1))
                .displayOrder(3)
                .build(), "admin@company.com");

        // Engineering employee: should see Global + Engineering (2 banners)
        List<BannerDto> engBanners = bannerService.getActiveBanners("Engineering");
        assertEquals(2, engBanners.size());
        assertTrue(engBanners.stream().anyMatch(b -> b.getTitle().equals("Global Townhall")));
        assertTrue(engBanners.stream().anyMatch(b -> b.getTitle().equals("Engineering Hackathon")));

        // Marketing employee: should only see Global (1 banner)
        List<BannerDto> mktBanners = bannerService.getActiveBanners("Marketing");
        assertEquals(1, mktBanners.size());
        assertEquals("Global Townhall", mktBanners.get(0).getTitle());
    }

    @Test
    @DisplayName("Enforce system concurrency cap of maximum 15 active banners")
    void testMax15ActiveBannersLimit() {
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= 15; i++) {
            bannerService.createBanner(CreateBannerRequest.builder()
                    .title("Banner " + i)
                    .category(BannerCategory.ANNOUNCEMENT)
                    .startDate(now.minusHours(1))
                    .expiryDate(now.plusDays(5))
                    .displayOrder(i)
                    .build(), "admin@company.com");
        }

        // 16th active banner creation should be rejected
        CreateBannerRequest sixteenth = CreateBannerRequest.builder()
                .title("Banner 16")
                .category(BannerCategory.ANNOUNCEMENT)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(5))
                .build();

        assertThrows(IllegalStateException.class, () ->
                bannerService.createBanner(sixteenth, "admin@company.com")
        );
    }

    @Test
    @DisplayName("Optimistic locking rejects stale updates when version does not match")
    void testOptimisticLocking_RejectsStaleUpdate() {
        LocalDateTime now = LocalDateTime.now();
        BannerDto created = bannerService.createBanner(CreateBannerRequest.builder()
                .title("Initial Title")
                .category(BannerCategory.BIRTHDAY)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .build(), "admin@company.com");

        assertEquals(0L, created.getVersion());

        // Update 1: using version 0 succeeds and increments version
        UpdateBannerRequest update1 = UpdateBannerRequest.builder()
                .title("Updated Title By Admin 1")
                .category(BannerCategory.BIRTHDAY)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .version(0L)
                .build();

        BannerDto updated = bannerService.updateBanner(created.getId(), update1, "admin1@company.com");
        assertNotEquals(0L, updated.getVersion());

        // Update 2: trying to update using stale version 0 throws ObjectOptimisticLockingFailureException
        UpdateBannerRequest staleUpdate = UpdateBannerRequest.builder()
                .title("Stale Update By Admin 2")
                .category(BannerCategory.BIRTHDAY)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(2))
                .version(0L) // Stale version!
                .build();

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                bannerService.updateBanner(created.getId(), staleUpdate, "admin2@company.com")
        );
    }

    @Test
    @DisplayName("Soft-delete and toggle immediately evict cache and update feed")
    void testSoftDeleteAndToggle() {
        LocalDateTime now = LocalDateTime.now();
        BannerDto created = bannerService.createBanner(CreateBannerRequest.builder()
                .title("Toggle Test Banner")
                .category(BannerCategory.CELEBRATION)
                .startDate(now.minusHours(1))
                .expiryDate(now.plusDays(1))
                .build(), "admin@company.com");

        assertEquals(1, bannerService.getActiveBanners(null).size());

        // Soft delete/deactivate
        bannerService.deleteBanner(created.getId(), "admin@company.com");
        assertEquals(0, bannerService.getActiveBanners(null).size());

        // Toggle back active
        BannerDto toggled = bannerService.toggleBannerStatus(created.getId(), "admin@company.com");
        assertTrue(toggled.getIsActive());
        assertEquals(1, bannerService.getActiveBanners(null).size());
    }
}
