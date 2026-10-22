package com.example.hr_management_backend.features.banners.controller;

import com.example.hr_management_backend.core.security.UserPrincipal;
import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.BannerReactionSummaryDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.BannerReactionType;
import com.example.hr_management_backend.features.banners.model.BannerType;
import com.example.hr_management_backend.features.banners.model.CelebrationType;
import com.example.hr_management_backend.features.banners.service.BannerImageStorageService;
import com.example.hr_management_backend.features.banners.service.BannerService;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
@Slf4j
public class BannerController {

    private final BannerService bannerService;
    private final BannerImageStorageService bannerImageStorageService;
    private final EmployeeRepository employeeRepository;

    /**
     * Public active banner feed for all authenticated employees.
     * Evaluates active date window, matches employee's department, and loads caller reactions in batch.
     * Prevents IDOR department snooping: regular employees cannot override department via query params.
     */
    @GetMapping("/active")
    public ResponseEntity<List<BannerDto>> getActiveBanners(
            @RequestParam(required = false) String department,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal);
        String effectiveDepartment = resolveDepartment(principal, employeeId, department);
        return ResponseEntity.ok(bannerService.getActiveBanners(effectiveDepartment, employeeId));
    }

    /**
     * Engage Social Hub Feed (/engage).
     * Returns active news posts or celebration cards with batch-hydrated reactions and counts.
     */
    @GetMapping("/engage")
    public ResponseEntity<List<BannerDto>> getEngageFeed(
            @RequestParam(required = false) BannerType tab,
            @RequestParam(required = false) CelebrationType filter,
            @RequestParam(required = false) String department,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal);
        String effectiveDepartment = resolveDepartment(principal, employeeId, department);
        return ResponseEntity.ok(bannerService.getEngageFeed(tab, filter, effectiveDepartment, employeeId));
    }

    /**
     * Returns distinct active corporate departments for banner targeting.
     */
    @GetMapping("/departments")
    public ResponseEntity<List<String>> getDepartments() {
        return ResponseEntity.ok(bannerService.getDistinctDepartments());
    }

    /**
     * Toggles social emoji reaction on a banner idempotently.
     * Supports optional action parameter (ADD, REMOVE, TOGGLE) for network retry safety.
     * Returns authoritative reaction state (counts + user reactions) to guarantee client state sync.
     */
    @PostMapping("/{id}/react")
    public ResponseEntity<BannerReactionSummaryDto> toggleReaction(
            @PathVariable Long id,
            @RequestParam BannerReactionType reactionType,
            @RequestParam(required = false, defaultValue = "TOGGLE") String action,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long employeeId = resolveEmployeeId(principal);
        if (employeeId == null) {
            throw new IllegalArgumentException("Authenticated employee principal required to react.");
        }
        BannerReactionSummaryDto summary = bannerService.toggleReaction(id, reactionType, employeeId, action);
        return ResponseEntity.ok(summary);
    }

    /**
     * Uploads local banner image file (max 5MB, magic byte verified).
     * Executed strictly on "Publish Banner" submission, eliminating orphaned disk files.
     */
    @PostMapping("/upload-image")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<Map<String, String>> uploadBannerImage(@RequestParam("file") MultipartFile file) {
        String result = bannerImageStorageService.storeImage(file);
        String imageUrl = (result.startsWith("http://") || result.startsWith("https://"))
                ? result
                : "/api/v1/banners/images/" + result;
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "filename", result,
                "imageUrl", imageUrl
        ));
    }

    /**
     * Public high-speed banner image asset serving.
     * Permitted in SecurityConfig for unauthenticated 60fps Flutter texture caching.
     * Hardened with X-Content-Type-Options: nosniff and inline Content-Disposition.
     */
    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> serveBannerImage(@PathVariable String filename) {
        Resource resource = bannerImageStorageService.loadImageAsResource(filename);
        String contentType = bannerImageStorageService.determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    private Long resolveEmployeeId(UserPrincipal principal) {
        if (principal == null) return null;
        if (principal.getEmployeeId() != null) return principal.getEmployeeId();
        if (principal.getEmail() != null) {
            return employeeRepository.findByEmail(principal.getEmail().trim().toLowerCase())
                    .map(Employee::getId)
                    .orElse(null);
        }
        return null;
    }

    private String resolveDepartment(UserPrincipal principal, Long employeeId, String requestedDept) {
        if (isPrivileged(principal) && requestedDept != null && !requestedDept.isBlank()) {
            return requestedDept.trim();
        }
        if (employeeId != null) {
            return employeeRepository.findDepartmentById(employeeId).orElse(null);
        }
        if (principal != null && principal.getEmail() != null) {
            return employeeRepository.findByEmail(principal.getEmail().trim().toLowerCase())
                    .map(Employee::getDepartment)
                    .orElse(null);
        }
        return null;
    }

    private boolean isPrivileged(UserPrincipal principal) {
        if (principal == null || principal.getAuthorities() == null) return false;
        return principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority())
                        || "ROLE_SUPER_ADMIN".equalsIgnoreCase(a.getAuthority())
                        || "BANNER_MANAGE".equalsIgnoreCase(a.getAuthority()));
    }

    /**
     * Admin / Manager view: returns all banners (including scheduled, expired, and inactive).
     * Protected by RBAC: Super Admin, Admin, or delegates with BANNER_MANAGE authority.
     */
    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<List<BannerDto>> getAllBannersForManagement() {
        return ResponseEntity.ok(bannerService.getAllBannersForManagement());
    }

    /**
     * Fetch single banner detail by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<BannerDto> getBannerById(@PathVariable Long id) {
        return ResponseEntity.ok(bannerService.getBannerById(id));
    }

    /**
     * Create promotional / celebration banner.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<BannerDto> createBanner(
            @Valid @RequestBody CreateBannerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String actorEmail = principal != null ? principal.getEmail() : "system";
        BannerDto created = bannerService.createBanner(request, actorEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update existing banner with optimistic locking version guard.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<BannerDto> updateBanner(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBannerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String actorEmail = principal != null ? principal.getEmail() : "system";
        BannerDto updated = bannerService.updateBanner(id, request, actorEmail);
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggle banner active status.
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<BannerDto> toggleBanner(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String actorEmail = principal != null ? principal.getEmail() : "system";
        BannerDto toggled = bannerService.toggleBannerStatus(id, actorEmail);
        return ResponseEntity.ok(toggled);
    }

    /**
     * Soft delete/deactivate banner.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('BANNER_MANAGE')")
    public ResponseEntity<Void> deleteBanner(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String actorEmail = principal != null ? principal.getEmail() : "system";
        bannerService.deleteBanner(id, actorEmail);
        return ResponseEntity.noContent().build();
    }
}
