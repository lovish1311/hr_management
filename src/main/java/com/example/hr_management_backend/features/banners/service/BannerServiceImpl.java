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
    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile("<(script|style)[^>]*>.*?(</(script|style)>|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NESTED_SCRIPT_PATTERN = Pattern.compile("<\\s*sc?r?i?p?t?<\\s*/?\\s*script[^>]*>[a-z]*>?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*(script|style)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGLING_SCRIPT_PATTERN = Pattern.compile("<\\s*(script|style).*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern VALID_HTTP_URL = Pattern.compile("^(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+)?$", Pattern.CASE_INSENSITIVE);

    private final BannerRepository bannerRepository;
    private final EmployeeRepository employeeRepository;

    @Lazy
    @Autowired
    private BannerServiceImpl self;

    /**
     * Cache active candidate pool (where expiryDate >= NOW).
     * Returns detached unmodifiable snapshot instances to prevent concurrent thread mutation of cached entities.
     * Intra-class invocation via @Lazy self ensures Spring AOP Caffeine caching proxy intercept.
     */
    @Cacheable(value = "banner_candidates", key = "'all'")
    public List<Banner> getCandidateBanners() {
        List<Banner> candidates = bannerRepository.findActiveCandidates(LocalDateTime.now());
        return List.copyOf(candidates.stream()
                .map(this::cloneBannerSnapshot)
                .toList());
    }

    private Banner cloneBannerSnapshot(Banner b) {
        return Banner.builder()
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
                .build();
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

        String cleanTitle = sanitizeText(request.getTitle());
        if (cleanTitle == null || cleanTitle.isBlank()) {
            throw new IllegalArgumentException("Banner title cannot be empty or contain only HTML tags.");
        }

        boolean willBeActive = (request.getIsActive() == null || Boolean.TRUE.equals(request.getIsActive()))
                && !request.getExpiryDate().isBefore(LocalDateTime.now());
        if (willBeActive) {
            checkActiveBannerCap();
        }

        Long actorEmpId = null;
        if (actorEmail != null) {
            actorEmpId = employeeRepository.findByEmail(actorEmail.trim().toLowerCase())
                    .map(Employee::getId)
                    .orElse(null);
        }

        Banner banner = Banner.builder()
                .title(cleanTitle)
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

        String cleanTitle = sanitizeText(request.getTitle());
        if (cleanTitle == null || cleanTitle.isBlank()) {
            throw new IllegalArgumentException("Banner title cannot be empty or contain only HTML tags.");
        }

        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));

        if (request.getVersion() != null && !request.getVersion().equals(banner.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Banner.class, id);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean wasActive = Boolean.TRUE.equals(banner.getIsActive()) && !banner.getExpiryDate().isBefore(now);
        boolean willBeActive = (request.getIsActive() != null ? Boolean.TRUE.equals(request.getIsActive()) : Boolean.TRUE.equals(banner.getIsActive()))
                && !request.getExpiryDate().isBefore(now);

        // Enforce active banner cap only when activating an inactive or expired banner
        if (!wasActive && willBeActive) {
            checkActiveBannerCap();
        }

        banner.setTitle(cleanTitle);
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
        LocalDateTime now = LocalDateTime.now();
        if (newStatus && (banner.getExpiryDate() == null || !banner.getExpiryDate().isBefore(now))) {
            checkActiveBannerCap();
        }

        banner.setIsActive(newStatus);
        Banner saved = bannerRepository.save(banner);
        log.info("Banner status toggled to {}: id={}, actor={}", newStatus, id, actorEmail);
        return mapToDto(saved);
    }

    private void checkActiveBannerCap() {
        long activeCount = bannerRepository.countByIsActiveTrueAndExpiryDateGreaterThanEqual(LocalDateTime.now());
        if (activeCount >= MAX_ACTIVE_BANNERS_LIMIT) {
            throw new IllegalStateException("Maximum limit of " + MAX_ACTIVE_BANNERS_LIMIT +
                    " active banners reached. Please expire or deactivate existing banners before activating new ones.");
        }
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
        String current = text;
        String previous;
        int iterations = 0;
        do {
            previous = current;
            // 1. Unfold nested script trickery like <scr<script>ipt> into standard <script>
            current = NESTED_SCRIPT_PATTERN.matcher(current).replaceAll("<script>");
            // 2. Strip entire script/style blocks along with their code contents
            current = SCRIPT_BLOCK_PATTERN.matcher(current).replaceAll("");
            // 3. Strip any unclosed dangling script/style tag to the end of string
            current = DANGLING_SCRIPT_PATTERN.matcher(current).replaceAll("");
            // 4. Strip any standalone script/style opening or closing tags
            current = SCRIPT_TAG_PATTERN.matcher(current).replaceAll("");
            iterations++;
        } while (!current.equals(previous) && iterations < 5);

        String cleaned = HTML_TAG_PATTERN.matcher(current).replaceAll("").trim();
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
