package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.core.exception.ResourceNotFoundException;
import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.Banner;
import com.example.hr_management_backend.features.banners.repository.BannerRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BannerServiceImpl implements BannerService {

    private static final int MAX_ACTIVE_BANNERS_LIMIT = 15;
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile("<(script|style)[^>]*>.*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern VALID_HTTP_URL = Pattern.compile("^(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+)?$", Pattern.CASE_INSENSITIVE);

    private final BannerRepository bannerRepository;
    private final EmployeeRepository employeeRepository;

    @Lazy
    @Autowired
    private BannerServiceImpl self;

    /**
     * Cache active candidate pool (where expiryDate >= NOW).
     * Intra-class invocation via @Lazy self ensures Spring AOP Caffeine caching proxy intercept.
     */
    @Cacheable(value = "banner_candidates", key = "'all'")
    public List<Banner> getCandidateBanners() {
        return bannerRepository.findActiveCandidates(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerDto> getActiveBanners(String userDepartment) {
        LocalDateTime now = LocalDateTime.now();
        List<Banner> candidates = self.getCandidateBanners();

        return candidates.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .filter(b -> !now.isBefore(b.getStartDate()) && !now.isAfter(b.getExpiryDate()))
                .filter(b -> matchesDepartment(b.getTargetDepartment(), userDepartment))
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerDto> getAllBannersForManagement() {
        return bannerRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BannerDto getBannerById(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));
        return mapToDto(banner);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banner_candidates", allEntries = true)
    public BannerDto createBanner(CreateBannerRequest request, String actorEmail) {
        validateDates(request.getStartDate(), request.getExpiryDate());

        long activeCount = bannerRepository.countByIsActiveTrueAndExpiryDateGreaterThanEqual(LocalDateTime.now());
        if (activeCount >= MAX_ACTIVE_BANNERS_LIMIT) {
            throw new IllegalStateException("Maximum limit of " + MAX_ACTIVE_BANNERS_LIMIT +
                    " active banners reached. Please expire or deactivate existing banners before creating new ones.");
        }

        Long actorEmpId = null;
        if (actorEmail != null) {
            actorEmpId = employeeRepository.findByEmail(actorEmail.trim().toLowerCase())
                    .map(Employee::getId)
                    .orElse(null);
        }

        Banner banner = Banner.builder()
                .title(sanitizeText(request.getTitle()))
                .subtitle(sanitizeText(request.getSubtitle()))
                .category(request.getCategory())
                .imageUrl(sanitizeUrl(request.getImageUrl()))
                .actionUrl(sanitizeUrl(request.getActionUrl()))
                .actionButtonText(sanitizeText(request.getActionButtonText()))
                .backgroundGradient(sanitizeText(request.getBackgroundGradient()))
                .targetDepartment(cleanDepartment(request.getTargetDepartment()))
                .startDate(request.getStartDate())
                .expiryDate(request.getExpiryDate())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdById(actorEmpId)
                .createdByEmail(actorEmail)
                .version(0L)
                .build();

        Banner saved = bannerRepository.saveAndFlush(banner);
        log.info("Banner created successfully: id={}, title='{}', actor={}", saved.getId(), saved.getTitle(), actorEmail);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banner_candidates", allEntries = true)
    public BannerDto updateBanner(Long id, UpdateBannerRequest request, String actorEmail) {
        validateDates(request.getStartDate(), request.getExpiryDate());

        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));

        if (request.getVersion() != null && !request.getVersion().equals(banner.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Banner.class, id);
        }

        banner.setTitle(sanitizeText(request.getTitle()));
        banner.setSubtitle(sanitizeText(request.getSubtitle()));
        banner.setCategory(request.getCategory());
        banner.setImageUrl(sanitizeUrl(request.getImageUrl()));
        banner.setActionUrl(sanitizeUrl(request.getActionUrl()));
        banner.setActionButtonText(sanitizeText(request.getActionButtonText()));
        banner.setBackgroundGradient(sanitizeText(request.getBackgroundGradient()));
        banner.setTargetDepartment(cleanDepartment(request.getTargetDepartment()));
        banner.setStartDate(request.getStartDate());
        banner.setExpiryDate(request.getExpiryDate());

        if (request.getDisplayOrder() != null) {
            banner.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            banner.setIsActive(request.getIsActive());
        }

        Banner saved = bannerRepository.saveAndFlush(banner);
        log.info("Banner updated successfully: id={}, title='{}', actor={}, version={}", saved.getId(), saved.getTitle(), actorEmail, saved.getVersion());
        return mapToDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banner_candidates", allEntries = true)
    public void deleteBanner(Long id, String actorEmail) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));

        // Soft deactivation to preserve audit and historical records
        banner.setIsActive(false);
        bannerRepository.save(banner);
        log.info("Banner soft-deactivated/deleted: id={}, actor={}", id, actorEmail);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banner_candidates", allEntries = true)
    public BannerDto toggleBannerStatus(Long id, String actorEmail) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));

        boolean newStatus = !Boolean.TRUE.equals(banner.getIsActive());
        banner.setIsActive(newStatus);
        Banner saved = bannerRepository.save(banner);
        log.info("Banner status toggled to {}: id={}, actor={}", newStatus, id, actorEmail);
        return mapToDto(saved);
    }

    private boolean matchesDepartment(String bannerDept, String userDept) {
        if (bannerDept == null || bannerDept.trim().isEmpty() || "ALL".equalsIgnoreCase(bannerDept.trim())) {
            return true;
        }
        if (userDept == null || userDept.trim().isEmpty()) {
            return false;
        }
        return bannerDept.trim().equalsIgnoreCase(userDept.trim());
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime expiryDate) {
        if (startDate == null || expiryDate == null) {
            throw new IllegalArgumentException("Start date and expiry date are required.");
        }
        if (!startDate.isBefore(expiryDate)) {
            throw new IllegalArgumentException("Start date must be strictly before expiry date.");
        }
    }

    private String sanitizeText(String text) {
        if (text == null) return null;
        // Strip entire <script>...</script> and <style>...</style> blocks along with their contents
        String withoutScripts = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll("");
        // Strip remaining HTML tags while preserving text, quotes, and unicode emojis for Flutter
        String cleaned = HTML_TAG_PATTERN.matcher(withoutScripts).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String sanitizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String trimmed = url.trim();
        if (trimmed.toLowerCase().startsWith("javascript:") ||
            trimmed.toLowerCase().startsWith("data:") ||
            trimmed.toLowerCase().startsWith("vbscript:")) {
            throw new IllegalArgumentException("Invalid URL protocol. Only HTTP and HTTPS URLs are permitted.");
        }
        if (!VALID_HTTP_URL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid URL format. Must be a valid HTTP or HTTPS address.");
        }
        return trimmed;
    }

    private String cleanDepartment(String dept) {
        if (dept == null || dept.trim().isEmpty() || "ALL".equalsIgnoreCase(dept.trim())) {
            return null;
        }
        return dept.trim();
    }

    private BannerDto mapToDto(Banner b) {
        LocalDateTime now = LocalDateTime.now();
        boolean isActive = Boolean.TRUE.equals(b.getIsActive());
        boolean isExpired = b.getExpiryDate() != null && now.isAfter(b.getExpiryDate());
        boolean isScheduled = b.getStartDate() != null && now.isBefore(b.getStartDate());
        boolean isCurrentlyActive = isActive && !isExpired && !isScheduled;

        return BannerDto.builder()
                .id(b.getId())
                .title(b.getTitle())
                .subtitle(b.getSubtitle())
                .category(b.getCategory())
                .imageUrl(b.getImageUrl())
                .actionUrl(b.getActionUrl())
                .actionButtonText(b.getActionButtonText())
                .backgroundGradient(b.getBackgroundGradient())
                .targetDepartment(b.getTargetDepartment())
                .startDate(b.getStartDate())
                .expiryDate(b.getExpiryDate())
                .displayOrder(b.getDisplayOrder())
                .isActive(b.getIsActive())
                .createdById(b.getCreatedById())
                .createdByEmail(b.getCreatedByEmail())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .version(b.getVersion())
                .isCurrentlyActive(isCurrentlyActive)
                .isExpired(isExpired)
                .isScheduled(isScheduled)
                .build();
    }
}
