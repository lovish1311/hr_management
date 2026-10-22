package com.example.hr_management_backend.features.banners.service;

import com.example.hr_management_backend.features.banners.model.Banner;
import com.example.hr_management_backend.features.banners.model.BannerCategory;
import com.example.hr_management_backend.features.banners.model.BannerType;
import com.example.hr_management_backend.features.banners.model.CelebrationType;
import com.example.hr_management_backend.features.banners.repository.BannerReactionRepository;
import com.example.hr_management_backend.features.banners.repository.BannerRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BannerLifecycleService {

    private final BannerRepository bannerRepository;
    private final BannerReactionRepository bannerReactionRepository;
    private final EmployeeRepository employeeRepository;
    private final CacheManager cacheManager;

    /**
     * Daily Midnight Cron pinned to Asia/Kolkata timezone.
     * Generates automated celebration banners and purges expired milestone posts.
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void executeMidnightLifecycle() {
        LocalDate today = LocalDate.now();
        log.info("Executing Midnight Banner Lifecycle for date: {}", today);
        generateMilestonesForDate(today);
        purgeExpiredCelebrations(today);
        evictBannerCache();
    }

    /**
     * Generates Birthday and Work Anniversary banners for active employees on the given date.
     * Can be invoked programmatically by tests or admin triggers.
     */
    @Transactional
    public void generateMilestonesForDate(LocalDate today) {
        List<Employee> activeEmployees = employeeRepository.findActiveEmployeesForMilestones(today);

        for (Employee emp : activeEmployees) {
            // 1. Birthday Check
            if (emp.getDateOfBirth() != null && isMilestoneDateMatch(emp.getDateOfBirth(), today)) {
                scheduleBirthdayBanner(emp, today);
            }

            // 2. Work Anniversary Check (Must have completed at least 1 full year)
            if (emp.getJoiningDate() != null && emp.getJoiningDate().isBefore(today)) {
                int years = today.getYear() - emp.getJoiningDate().getYear();
                if (years >= 1 && isMilestoneDateMatch(emp.getJoiningDate(), today)) {
                    scheduleAnniversaryBanner(emp, today, years);
                }
            }
        }
    }

    private void scheduleBirthdayBanner(Employee emp, LocalDate today) {
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        boolean alreadyExists = bannerRepository.existsByTargetEmployeeIdAndCelebrationTypeAndStartDateLessThanEqualAndExpiryDateGreaterThanEqual(
                emp.getId(),
                CelebrationType.BIRTHDAY,
                endOfDay,
                startOfDay
        );

        if (alreadyExists) {
            log.info("Birthday celebration banner already exists for employee id={}, skipping automated generation.", emp.getId());
            return;
        }

        Banner banner = Banner.builder()
                .title("Happy Birthday, " + emp.getFirstName() + "!")
                .subtitle("Wishing you a fantastic year ahead filled with joy, health, and success.")
                .category(BannerCategory.BIRTHDAY)
                .bannerType(BannerType.CELEBRATION)
                .celebrationType(CelebrationType.BIRTHDAY)
                .targetEmployeeId(emp.getId())
                .startDate(startOfDay)
                .expiryDate(endOfDay)
                .displayOrder(0)
                .isActive(true)
                .createdByEmail("system.milestone@company.internal")
                .backgroundGradient("linear-gradient(135deg, #FF6B6B 0%, #FFE66D 100%)")
                .build();

        bannerRepository.save(banner);
        log.info("Automated Birthday banner created: employeeId={}, name='{} {}'", emp.getId(), emp.getFirstName(), emp.getLastName());
    }

    private void scheduleAnniversaryBanner(Employee emp, LocalDate today, int years) {
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        boolean alreadyExists = bannerRepository.existsByTargetEmployeeIdAndCelebrationTypeAndStartDateLessThanEqualAndExpiryDateGreaterThanEqual(
                emp.getId(),
                CelebrationType.ANNIVERSARY,
                endOfDay,
                startOfDay
        );

        if (alreadyExists) {
            log.info("Work anniversary celebration banner already exists for employee id={}, skipping automated generation.", emp.getId());
            return;
        }

        String yearLabel = years + " " + (years == 1 ? "Year" : "Years");
        Banner banner = Banner.builder()
                .title("Happy " + getOrdinal(years) + " Work Anniversary, " + emp.getFirstName() + "!")
                .subtitle("Celebrating " + yearLabel + " of dedication, innovation, and impact with our team. Thank you!")
                .category(BannerCategory.CELEBRATION)
                .bannerType(BannerType.CELEBRATION)
                .celebrationType(CelebrationType.ANNIVERSARY)
                .targetEmployeeId(emp.getId())
                .startDate(startOfDay)
                .expiryDate(endOfDay)
                .displayOrder(0)
                .isActive(true)
                .createdByEmail("system.milestone@company.internal")
                .backgroundGradient("linear-gradient(135deg, #4E54C8 0%, #8F94FB 100%)")
                .build();

        bannerRepository.save(banner);
        log.info("Automated Anniversary banner created: employeeId={}, years={}, name='{} {}'", emp.getId(), years, emp.getFirstName(), emp.getLastName());
    }

    /**
     * Purges expired automated celebration banners in batch to keep the database lean and prevent storage creep.
     */
    @Transactional
    public void purgeExpiredCelebrations(LocalDate today) {
        LocalDateTime cutoff = today.atStartOfDay();
        List<Banner> expiredCelebrations = bannerRepository.findByBannerTypeAndExpiryDateBefore(BannerType.CELEBRATION, cutoff);

        if (!expiredCelebrations.isEmpty()) {
            List<Long> bannerIds = expiredCelebrations.stream().map(Banner::getId).toList();
            bannerReactionRepository.deleteByBannerIdIn(bannerIds);
            bannerRepository.deleteAllInBatch(expiredCelebrations);
            log.info("Purged {} expired automated celebration banners in batch prior to {}", expiredCelebrations.size(), cutoff);
        }
    }

    private void evictBannerCache() {
        if (cacheManager != null) {
            Cache cache = cacheManager.getCache("banner_candidates");
            if (cache != null) {
                cache.clear();
                log.info("banner_candidates cache evicted successfully.");
            }
        }
    }

    /**
     * Matches month and day, with leap-year handling (Feb 29 birthdays/anniversaries celebrated on Feb 28 on non-leap years).
     */
    private boolean isMilestoneDateMatch(LocalDate eventDate, LocalDate today) {
        if (eventDate.getMonthValue() == today.getMonthValue() && eventDate.getDayOfMonth() == today.getDayOfMonth()) {
            return true;
        }
        // Leap-year Feb 29 fallback for non-leap years
        if (!today.isLeapYear() && today.getMonthValue() == 2 && today.getDayOfMonth() == 28) {
            return eventDate.getMonthValue() == 2 && eventDate.getDayOfMonth() == 29;
        }
        return false;
    }

    private String getOrdinal(int n) {
        if (n >= 11 && n <= 13) return n + "th";
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
