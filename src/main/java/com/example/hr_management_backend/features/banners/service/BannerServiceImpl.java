package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.core.exception.ResourceNotFoundException;
import com.example.hr_management_backend.features.banners.dto.BannerDto;
import com.example.hr_management_backend.features.banners.dto.BannerReactionSummaryDto;
import com.example.hr_management_backend.features.banners.dto.CreateBannerRequest;
import com.example.hr_management_backend.features.banners.dto.UpdateBannerRequest;
import com.example.hr_management_backend.features.banners.model.*;
import com.example.hr_management_backend.features.banners.repository.BannerReactionCountProjection;
import com.example.hr_management_backend.features.banners.repository.BannerReactionRepository;
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
import java.util.*;
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
    private static final Pattern VALID_URL_PATTERN = Pattern.compile("^((https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+)|(/[a-zA-Z0-9_\\-][a-zA-Z0-9_\\-./?#&=%]*))?$", Pattern.CASE_INSENSITIVE);

    private final BannerRepository bannerRepository;
    private final BannerReactionRepository bannerReactionRepository;
    private final EmployeeRepository employeeRepository;
    private final com.example.hr_management_backend.features.notifications.service.PushNotificationService pushNotificationService;

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
                .bannerType(b.getBannerType())
                .celebrationType(b.getCelebrationType())
                .targetEmployeeId(b.getTargetEmployeeId())
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
    public List<BannerDto> getActiveBanners(String userDepartment, Long currentEmployeeId) {
        LocalDateTime now = LocalDateTime.now();
        List<Banner> candidates = self.getCandidateBanners();

        List<Banner> activeList = candidates.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .filter(b -> !now.isBefore(b.getStartDate()) && !now.isAfter(b.getExpiryDate()))
                .filter(b -> matchesDepartment(b.getTargetDepartment(), userDepartment))
                .toList();

        return mapToDtoBatch(activeList, currentEmployeeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerDto> getEngageFeed(BannerType bannerType, CelebrationType celebrationType, String userDepartment, Long currentEmployeeId) {
        LocalDateTime now = LocalDateTime.now();
        List<Banner> candidates = self.getCandidateBanners();

        List<Banner> filtered = candidates.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .filter(b -> !now.isBefore(b.getStartDate()) && !now.isAfter(b.getExpiryDate()))
                .filter(b -> matchesDepartment(b.getTargetDepartment(), userDepartment))
                .filter(b -> bannerType == null || b.getBannerType() == bannerType)
                .filter(b -> celebrationType == null || b.getCelebrationType() == celebrationType)
                .toList();

        return mapToDtoBatch(filtered, currentEmployeeId);
    }

    @Override
    @Transactional
    public BannerReactionSummaryDto toggleReaction(Long bannerId, BannerReactionType reactionType, Long employeeId) {
        return toggleReaction(bannerId, reactionType, employeeId, "TOGGLE");
    }

    @Override
    @Transactional
    public BannerReactionSummaryDto toggleReaction(Long bannerId, BannerReactionType reactionType, Long employeeId, String action) {
        if (bannerId == null || reactionType == null || employeeId == null) {
            throw new IllegalArgumentException("Banner ID, reaction type, and employee ID are required.");
        }

        // Verify banner exists
        if (!bannerRepository.existsById(bannerId)) {
            throw new ResourceNotFoundException("Banner not found with id: " + bannerId);
        }

        // Execute reaction mutation in an isolated transaction to absorb rapid double-tap collisions cleanly
        self.executeReactionMutation(bannerId, employeeId, reactionType, action);

        // Fetch authoritative reaction summary for this banner
        List<BannerReaction> reactions = bannerReactionRepository.findByBannerId(bannerId);

        Map<String, Long> reactionCounts = new LinkedHashMap<>();
        for (BannerReactionType type : BannerReactionType.values()) {
            reactionCounts.put(type.name(), 0L);
        }

        Set<String> userReactions = new LinkedHashSet<>();
        long totalReactions = 0L;

        for (BannerReaction r : reactions) {
            String typeName = r.getReactionType().name();
            reactionCounts.put(typeName, reactionCounts.getOrDefault(typeName, 0L) + 1);
            totalReactions++;
            if (employeeId.equals(r.getEmployeeId())) {
                userReactions.add(typeName);
            }
        }

        return BannerReactionSummaryDto.builder()
                .bannerId(bannerId)
                .reactionCounts(reactionCounts)
                .userReactions(userReactions)
                .totalReactions(totalReactions)
                .build();
    }

    /**
     * Executes the reaction mutation (ADD, REMOVE, or TOGGLE) in an isolated transaction.
     * Absorbs rapid double-tap collisions cleanly without poisoning the outer transaction.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void executeReactionMutation(Long bannerId, Long employeeId, BannerReactionType reactionType, String action) {
        String resolvedAction = (action != null && !action.isBlank()) ? action.trim().toUpperCase() : "TOGGLE";

        if ("REMOVE".equals(resolvedAction)) {
            bannerReactionRepository.deleteByBannerIdAndEmployeeIdAndReactionType(bannerId, employeeId, reactionType);
            log.info("Reaction REMOVED: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
            return;
        }

        if ("ADD".equals(resolvedAction)) {
            if (bannerReactionRepository.existsByBannerIdAndEmployeeIdAndReactionType(bannerId, employeeId, reactionType)) {
                log.info("Reaction ADD already exists (idempotent no-op): bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
                return;
            }
            try {
                BannerReaction reaction = BannerReaction.builder()
                        .bannerId(bannerId)
                        .employeeId(employeeId)
                        .reactionType(reactionType)
                        .createdAt(LocalDateTime.now())
                        .build();
                bannerReactionRepository.saveAndFlush(reaction);
                log.info("Reaction ADDED: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.warn("Concurrent duplicate reaction insertion absorbed cleanly: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
            }
            return;
        }

        // Default TOGGLE behavior:
        int deleted = bannerReactionRepository.deleteByBannerIdAndEmployeeIdAndReactionType(bannerId, employeeId, reactionType);
        if (deleted > 0) {
            log.info("Reaction toggled OFF: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
        } else {
            if (bannerReactionRepository.existsByBannerIdAndEmployeeIdAndReactionType(bannerId, employeeId, reactionType)) {
                log.info("Reaction already present upon toggle insert: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
                return;
            }
            try {
                BannerReaction reaction = BannerReaction.builder()
                        .bannerId(bannerId)
                        .employeeId(employeeId)
                        .reactionType(reactionType)
                        .createdAt(LocalDateTime.now())
                        .build();
                bannerReactionRepository.saveAndFlush(reaction);
                log.info("Reaction toggled ON: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.warn("Concurrent duplicate reaction insertion absorbed cleanly: bannerId={}, employeeId={}, type={}", bannerId, employeeId, reactionType);
            }
        }
    }

    /**
     * Backward-compatible delegation for executeToggle.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void executeToggle(Long bannerId, Long employeeId, BannerReactionType reactionType) {
        executeReactionMutation(bannerId, employeeId, reactionType, "TOGGLE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerDto> getAllBannersForManagement() {
        List<Banner> banners = bannerRepository.findAllByOrderByCreatedAtDesc();
        return mapToDtoBatch(banners, null);
    }

    @Override
    @Transactional(readOnly = true)
    public BannerDto getBannerById(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found with id: " + id));
        List<BannerDto> dtos = mapToDtoBatch(List.of(banner), null);
        return dtos.get(0);
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

        LocalDateTime now = LocalDateTime.now();
        boolean willBeActive = (request.getIsActive() == null || Boolean.TRUE.equals(request.getIsActive()))
                && !request.getStartDate().isAfter(now)
                && !request.getExpiryDate().isBefore(now);
        if (willBeActive) {
            checkActiveBannerCap();
        }

        Long actorEmpId = null;
        if (actorEmail != null) {
            actorEmpId = employeeRepository.findByEmail(actorEmail.trim().toLowerCase())
                    .map(Employee::getId)
                    .orElse(null);
        }

        BannerType bType = request.getBannerType() != null ? request.getBannerType() : BannerType.NEWS;
        BannerCategory category = resolveCategory(request.getCategory(), bType, request.getCelebrationType());

        Banner banner = Banner.builder()
                .title(cleanTitle)
                .subtitle(sanitizeText(request.getSubtitle()))
                .category(category)
                .bannerType(bType)
                .celebrationType(request.getCelebrationType())
                .targetEmployeeId(request.getTargetEmployeeId())
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
        log.info("Banner created successfully: id={}, title='{}', type={}, actor={}", saved.getId(), saved.getTitle(), saved.getBannerType(), actorEmail);

        // Dispatch dual-audience push notification asynchronously without blocking response
        try {
            dispatchBannerPushNotification(saved);
        } catch (Exception e) {
            log.error("[BannerServiceImpl] Push notification dispatch error for bannerId={}: {}", saved.getId(), e.getMessage());
        }

        return mapToDtoBatch(List.of(saved), actorEmpId).get(0);
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
        boolean wasActive = Boolean.TRUE.equals(banner.getIsActive())
                && !banner.getStartDate().isAfter(now)
                && !banner.getExpiryDate().isBefore(now);

        LocalDateTime effectiveStart = request.getStartDate();
        LocalDateTime effectiveExpiry = request.getExpiryDate();
        boolean newIsActive = request.getIsActive() != null ? Boolean.TRUE.equals(request.getIsActive()) : Boolean.TRUE.equals(banner.getIsActive());

        boolean willBeActive = newIsActive
                && !effectiveStart.isAfter(now)
                && !effectiveExpiry.isBefore(now);

        // Enforce active banner cap only when activating an inactive or scheduled/expired banner
        if (!wasActive && willBeActive) {
            checkActiveBannerCap();
        }

        BannerType bType = request.getBannerType() != null ? request.getBannerType() : banner.getBannerType();
        CelebrationType cType = request.getCelebrationType() != null ? request.getCelebrationType() : banner.getCelebrationType();
        BannerCategory category = resolveCategory(request.getCategory(), bType, cType);

        banner.setTitle(cleanTitle);
        banner.setSubtitle(sanitizeText(request.getSubtitle()));
        banner.setCategory(category);
        banner.setBannerType(bType);
        banner.setCelebrationType(cType);
        if (request.getTargetEmployeeId() != null) {
            banner.setTargetEmployeeId(request.getTargetEmployeeId());
        }
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
        return mapToDtoBatch(List.of(saved), null).get(0);
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
        if (newStatus && (banner.getStartDate() == null || !banner.getStartDate().isAfter(now))
                      && (banner.getExpiryDate() == null || !banner.getExpiryDate().isBefore(now))) {
            checkActiveBannerCap();
        }

        banner.setIsActive(newStatus);
        Banner saved = bannerRepository.save(banner);
        log.info("Banner status toggled to {}: id={}, actor={}", newStatus, id, actorEmail);
        return mapToDtoBatch(List.of(saved), null).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctDepartments() {
        return employeeRepository.findDistinctDepartments();
    }

    private void checkActiveBannerCap() {
        LocalDateTime now = LocalDateTime.now();
        long activeCount = bannerRepository.countByIsActiveTrueAndStartDateLessThanEqualAndExpiryDateGreaterThanEqual(now, now);
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

    private BannerCategory resolveCategory(BannerCategory requested, BannerType type, CelebrationType celebType) {
        if (requested != null) return requested;
        if (type == BannerType.CELEBRATION) {
            if (celebType == CelebrationType.BIRTHDAY) return BannerCategory.BIRTHDAY;
            return BannerCategory.CELEBRATION;
        }
        return BannerCategory.ANNOUNCEMENT;
    }

    private String sanitizeText(String text) {
        if (text == null) return null;
        String current = text;
        String previous;
        int iterations = 0;
        do {
            previous = current;
            current = NESTED_SCRIPT_PATTERN.matcher(current).replaceAll("<script>");
            current = SCRIPT_BLOCK_PATTERN.matcher(current).replaceAll("");
            current = DANGLING_SCRIPT_PATTERN.matcher(current).replaceAll("");
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
            trimmed.toLowerCase().startsWith("vbscript:") ||
            trimmed.toLowerCase().startsWith("file:") ||
            trimmed.startsWith("//")) {
            throw new IllegalArgumentException("Invalid URL protocol. Only HTTP, HTTPS, and internal application paths are permitted.");
        }
        if (!VALID_URL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid URL format. Must be a valid HTTP, HTTPS, or internal path address.");
        }
        return trimmed;
    }

    private String cleanDepartment(String dept) {
        if (dept == null || dept.trim().isEmpty() || "ALL".equalsIgnoreCase(dept.trim())) {
            return null;
        }
        return dept.trim();
    }

    /**
     * High-speed 2-Query Batch Reaction Pipeline ($O(1)$ database trips).
     * Populates live aggregated counts and caller-specific reactions across all banners in a single pass.
     */
    private List<BannerDto> mapToDtoBatch(List<Banner> banners, Long currentEmployeeId) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }

        List<Long> bannerIds = banners.stream().map(Banner::getId).toList();

        // Batch Query 1: Grouped counts per banner and reaction type
        List<BannerReactionCountProjection> countProjections = bannerReactionRepository.countReactionsByBannerIds(bannerIds);
        Map<Long, Map<String, Long>> countsByBanner = new HashMap<>();
        Map<Long, Long> totalReactionsByBanner = new HashMap<>();

        for (BannerReactionCountProjection p : countProjections) {
            countsByBanner.computeIfAbsent(p.getBannerId(), k -> {
                Map<String, Long> initial = new LinkedHashMap<>();
                for (BannerReactionType t : BannerReactionType.values()) {
                    initial.put(t.name(), 0L);
                }
                return initial;
            }).put(p.getReactionType().name(), p.getCount());

            totalReactionsByBanner.merge(p.getBannerId(), p.getCount(), Long::sum);
        }

        // Batch Query 2: Caller-specific reactions
        Map<Long, Set<String>> userReactionsByBanner = new HashMap<>();
        if (currentEmployeeId != null) {
            List<BannerReaction> callerReactions = bannerReactionRepository.findByBannerIdsAndEmployeeId(bannerIds, currentEmployeeId);
            for (BannerReaction r : callerReactions) {
                userReactionsByBanner.computeIfAbsent(r.getBannerId(), k -> new LinkedHashSet<>())
                        .add(r.getReactionType().name());
            }
        }

        // Target employee names lookup in batch
        List<Long> targetEmpIds = banners.stream()
                .map(Banner::getTargetEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> empNames = new HashMap<>();
        if (!targetEmpIds.isEmpty()) {
            List<Employee> emps = employeeRepository.findAllById(targetEmpIds);
            for (Employee e : emps) {
                String fullName = ((e.getFirstName() != null ? e.getFirstName() : "") + " " +
                        (e.getLastName() != null ? e.getLastName() : "")).trim();
                empNames.put(e.getId(), fullName.isEmpty() ? e.getEmail() : fullName);
            }
        }

        LocalDateTime now = LocalDateTime.now();

        return banners.stream().map(b -> {
            boolean isActive = Boolean.TRUE.equals(b.getIsActive());
            boolean isExpired = b.getExpiryDate() != null && now.isAfter(b.getExpiryDate());
            boolean isScheduled = b.getStartDate() != null && now.isBefore(b.getStartDate());
            boolean isCurrentlyActive = isActive && !isExpired && !isScheduled;

            Map<String, Long> reactionCounts = countsByBanner.computeIfAbsent(b.getId(), k -> {
                Map<String, Long> initial = new LinkedHashMap<>();
                for (BannerReactionType t : BannerReactionType.values()) {
                    initial.put(t.name(), 0L);
                }
                return initial;
            });
            Set<String> userReactions = userReactionsByBanner.getOrDefault(b.getId(), Set.of());
            long total = totalReactionsByBanner.getOrDefault(b.getId(), 0L);
            String targetEmpName = b.getTargetEmployeeId() != null ? empNames.get(b.getTargetEmployeeId()) : null;

            return BannerDto.builder()
                    .id(b.getId())
                    .title(b.getTitle())
                    .subtitle(b.getSubtitle())
                    .category(b.getCategory())
                    .bannerType(b.getBannerType())
                    .celebrationType(b.getCelebrationType())
                    .targetEmployeeId(b.getTargetEmployeeId())
                    .targetEmployeeName(targetEmpName)
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
                    .reactionCounts(reactionCounts)
                    .userReactions(userReactions)
                    .totalReactions(total)
                    .build();
        }).toList();
    }

    /**
     * Dispatches personalized dual-audience push notifications:
     * 1. Direct celebratory notification to target celebrant (if milestone)
     * 2. Call-to-action notification to team / department peers
     */
    private void dispatchBannerPushNotification(Banner banner) {
        if (banner == null || !Boolean.TRUE.equals(banner.getIsActive())) {
            return;
        }

        Map<String, String> data = Map.of(
                "bannerId", String.valueOf(banner.getId()),
                "bannerType", banner.getBannerType() != null ? banner.getBannerType().name() : "NEWS",
                "click_action", "FLUTTER_NOTIFICATION_CLICK"
        );

        if (banner.getBannerType() == BannerType.CELEBRATION && banner.getTargetEmployeeId() != null) {
            Long celebrantId = banner.getTargetEmployeeId();
            Optional<Employee> celebrantOpt = employeeRepository.findById(celebrantId);
            String celebrantName = celebrantOpt.map(e -> e.getFirstName() + " " + e.getLastName()).orElse("Colleague");

            boolean isBirthday = banner.getCelebrationType() == CelebrationType.BIRTHDAY;
            boolean isAnniversary = banner.getCelebrationType() == CelebrationType.ANNIVERSARY;

            // 1. Direct Celebrant Message
            String celebrantTitle = isBirthday
                    ? "🎂 Happy Birthday, " + celebrantOpt.map(Employee::getFirstName).orElse(celebrantName) + "!"
                    : isAnniversary
                    ? "🌟 Happy Work Anniversary, " + celebrantOpt.map(Employee::getFirstName).orElse(celebrantName) + "!"
                    : "🎉 Congratulations, " + celebrantOpt.map(Employee::getFirstName).orElse(celebrantName) + "!";

            String celebrantBody = isBirthday
                    ? "Wishing you an extraordinary year ahead filled with happiness and success from the entire team!"
                    : isAnniversary
                    ? "Thank you for being such an impactful part of our journey. Cheers to many more milestones together!"
                    : banner.getTitle();

            pushNotificationService.sendToEmployee(celebrantId, celebrantTitle, celebrantBody, data);

            // 2. Peer Broadcast Message
            String peerTitle = isBirthday
                    ? "🎂 Today is " + celebrantName + "'s Birthday!"
                    : isAnniversary
                    ? "🌟 Celebrating " + celebrantName + "'s Work Anniversary!"
                    : "🎉 Special Celebration for " + celebrantName + "!";

            String peerBody = "Drop your wishes and celebrate together in Engage Hub! 🥳";

            // If target department is specified, broadcast to department; otherwise broadcast to all peers
            List<Employee> allEmployees = employeeRepository.findAll();
            List<Long> peerIds = allEmployees.stream()
                    .map(Employee::getId)
                    .filter(id -> !id.equals(celebrantId))
                    .toList();

            if (!peerIds.isEmpty()) {
                pushNotificationService.sendToEmployees(peerIds, peerTitle, peerBody, data);
            }
        } else {
            // News / Company Announcement Broadcast
            String title = "📢 " + banner.getTitle();
            String body = (banner.getSubtitle() != null && !banner.getSubtitle().isBlank())
                    ? banner.getSubtitle()
                    : "Check out the latest company announcement in Engage Hub!";

            pushNotificationService.broadcastToAll(title, body, data);
        }
    }
}
