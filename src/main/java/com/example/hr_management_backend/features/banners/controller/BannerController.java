package com.example.hr_management_backend.features.banners.controller;

import com.example.hr_management_backend.core.security.UserPrincipal;
import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.service.BannerService;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
@Slf4j
public class BannerController {

    private final BannerService bannerService;
    private final EmployeeRepository employeeRepository;

    /**
     * Public active banner feed for all authenticated employees.
     * Evaluates active date window and matches employee's department.
     * Prevents IDOR department snooping: regular employees cannot override department via query params.
     * Only privileged roles (ADMIN, SUPER_ADMIN, or BANNER_MANAGE) can preview other departments via ?department=...
     */
    @GetMapping("/active")
    public ResponseEntity<List<BannerDto>> getActiveBanners(
            @RequestParam(required = false) String department,
            @AuthenticationPrincipal UserPrincipal principal) {
        String effectiveDepartment = null;
        if (isPrivileged(principal) && department != null && !department.isBlank()) {
            effectiveDepartment = department.trim();
        } else if (principal != null) {
            if (principal.getEmployeeId() != null) {
                effectiveDepartment = employeeRepository.findById(principal.getEmployeeId())
                        .map(Employee::getDepartment)
                        .orElse(null);
            } else if (principal.getEmail() != null) {
                effectiveDepartment = employeeRepository.findByEmail(principal.getEmail())
                        .map(Employee::getDepartment)
                        .orElse(null);
            }
        }
        return ResponseEntity.ok(bannerService.getActiveBanners(effectiveDepartment));
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
