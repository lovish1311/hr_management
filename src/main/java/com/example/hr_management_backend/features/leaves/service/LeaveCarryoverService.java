package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service managing Leave Carryover (Year-End Processing) and Expiry of carried-forward leaves.
 *
 * Rules:
 * - On January 1st (or manual trigger), process all active employees' leave balances:
 *     * Casual & Sick leaves EXPIRE (reset to fresh quota).
 *     * Earned leaves CARRY FORWARD with a cap of up to 15.0 days maximum.
 *     * Carried forward balance is tracked separately with an expiry date of March 31st of the new year.
 * - On March 31st / April 1st, carried-forward leaves expire and are marked as expired.
 * - Hybrid Model: Scheduled cron jobs execute at midnight, while lazy evaluations ensure
 *   immediate real-time consistency whenever balance records are accessed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveCarryoverService {

    public static final double MAX_EARNED_LEAVE_CARRYOVER_DAYS = 15.0;

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveAccrualService leaveAccrualService;
    private final SettingsService settingsService;

    /**
     * Scheduled Job: Runs automatically on January 1st at midnight (00:00:00).
     */
    @Scheduled(cron = "0 0 0 1 1 ?")
    public void scheduledYearEndProcessing() {
        int toYear = LocalDate.now().getYear();
        int fromYear = toYear - 1;
        log.info("[LeaveCarryoverService] Automatic scheduled Year-End processing triggered for {} -> {}", fromYear, toYear);
        processYearEndCarryover(fromYear, toYear);
    }

    /**
     * Scheduled Job: Runs automatically on April 1st at midnight (00:00:00) to expire unconsumed carried-forward leaves.
     */
    @Scheduled(cron = "0 0 0 1 4 ?")
    public void scheduledCarriedForwardExpiryProcessing() {
        int currentYear = LocalDate.now().getYear();
        log.info("[LeaveCarryoverService] Automatic scheduled carried-forward leave expiry triggered for year {}", currentYear);
        processCarriedForwardExpiry(currentYear);
    }

    /**
     * Executes Year-End carryover processing from fromYear to toYear for all employees.
     */
    @Transactional
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public Map<String, Object> processYearEndCarryover(int fromYear, int toYear) {
        log.info("Starting Year-End Carryover processing: {} -> {}", fromYear, toYear);

        List<Employee> employees = employeeRepository.findAll();
        Map<Long, LeaveBalance> prevBalances = leaveBalanceRepository.findByYear(fromYear).stream()
                .collect(Collectors.toMap(LeaveBalance::getEmployeeId, b -> b, (b1, b2) -> b1));

        Map<Long, LeaveBalance> targetBalances = leaveBalanceRepository.findByYear(toYear).stream()
                .collect(Collectors.toMap(LeaveBalance::getEmployeeId, b -> b, (b1, b2) -> b1));

        double defaultRhQuota = 2.0;
        try {
            String setting = settingsService.getSetting("restricted_holiday_allowance");
            if (setting != null && !setting.isBlank()) {
                defaultRhQuota = Double.parseDouble(setting);
            }
        } catch (Exception ignored) {}

        List<LeaveBalance> toSave = new ArrayList<>();
        int processedCount = 0;
        double totalDaysCarriedOver = 0.0;

        for (Employee emp : employees) {
            LeaveBalance prevBalance = prevBalances.get(emp.getId());
            LeaveBalance targetBalance = targetBalances.get(emp.getId());

            double unusedEarned = 0.0;
            if (prevBalance != null) {
                double quota = prevBalance.getEarnedLeaveQuota() != null ? prevBalance.getEarnedLeaveQuota() : 6.0;
                double used = prevBalance.getEarnedLeaveUsed() != null ? prevBalance.getEarnedLeaveUsed() : 0.0;
                unusedEarned = Math.max(0.0, quota - used);
            }

            // Cap carried-forward days to a maximum of 15.0 days
            double carriedForwardDays = Math.min(MAX_EARNED_LEAVE_CARRYOVER_DAYS, unusedEarned);

            LocalDate expiryDate = LocalDate.of(toYear, 3, 31);
            boolean isExpired = LocalDate.now().isAfter(expiryDate);

            if (targetBalance == null) {
                targetBalance = LeaveBalance.builder()
                        .employeeId(emp.getId())
                        .year(toYear)
                        .casualLeaveUsed(0.0)
                        .sickLeaveUsed(0.0)
                        .earnedLeaveUsed(0.0)
                        .workFromHomeQuota(0.0)
                        .workFromHomeUsed(0.0)
                        .restrictedHolidayUsed(0.0)
                        .carriedForwardLeaveQuota(carriedForwardDays)
                        .carriedForwardLeaveUsed(0.0)
                        .carriedForwardExpiryDate(expiryDate)
                        .carriedForwardExpired(isExpired)
                        .build();

                leaveAccrualService.applyAccrualToBalance(targetBalance, emp, toYear, defaultRhQuota);
            } else {
                targetBalance.setCarriedForwardLeaveQuota(carriedForwardDays);
                targetBalance.setCarriedForwardLeaveUsed(0.0);
                targetBalance.setCarriedForwardExpiryDate(expiryDate);
                targetBalance.setCarriedForwardExpired(isExpired);
                leaveAccrualService.applyAccrualToBalance(targetBalance, emp, toYear, defaultRhQuota);
            }

            toSave.add(targetBalance);
            processedCount++;
            totalDaysCarriedOver += carriedForwardDays;
        }

        if (!toSave.isEmpty()) {
            leaveBalanceRepository.saveAll(toSave);
        }

        log.info("Year-End Carryover completed: {} employees processed, total carried-over days = {}",
                processedCount, totalDaysCarriedOver);

        return Map.of(
                "status", "SUCCESS",
                "fromYear", fromYear,
                "toYear", toYear,
                "employeesProcessed", processedCount,
                "totalCarriedOverDays", totalDaysCarriedOver,
                "expiryDate", LocalDate.of(toYear, 3, 31).toString()
        );
    }

    /**
     * Executes expiry of carried-forward balances for a specific year whose expiry date has passed.
     */
    @Transactional
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public Map<String, Object> processCarriedForwardExpiry(int year) {
        log.info("Starting carried-forward leave expiry processing for year {}", year);

        List<LeaveBalance> balances = leaveBalanceRepository.findByYear(year);
        LocalDate today = LocalDate.now();
        List<LeaveBalance> toUpdate = new ArrayList<>();
        double totalExpiredDays = 0.0;
        int count = 0;

        for (LeaveBalance b : balances) {
            LocalDate effectiveExpiry = b.getEffectiveCarriedForwardExpiryDate();
            if (!Boolean.TRUE.equals(b.getCarriedForwardExpired())
                    && !today.isBefore(effectiveExpiry)) {

                double quota = b.getCarriedForwardLeaveQuota() != null ? b.getCarriedForwardLeaveQuota() : 0.0;
                double used = b.getCarriedForwardLeaveUsed() != null ? b.getCarriedForwardLeaveUsed() : 0.0;
                double unconsumed = Math.max(0.0, quota - used);

                b.setCarriedForwardExpiryDate(effectiveExpiry);
                b.setCarriedForwardExpired(true);
                toUpdate.add(b);
                totalExpiredDays += unconsumed;
                count++;
            }
        }

        if (!toUpdate.isEmpty()) {
            leaveBalanceRepository.saveAll(toUpdate);
        }

        log.info("Carried-forward expiry completed for year {}: {} balances expired, total days lapsed = {}",
                year, count, totalExpiredDays);

        return Map.of(
                "status", "SUCCESS",
                "year", year,
                "balancesExpired", count,
                "totalDaysLapsed", totalExpiredDays
        );
    }

    /**
     * Lazy check and self-healing: ensures that any individual LeaveBalance has its carryover and expiry evaluated
     * on real-time retrieval even if a scheduled job was missed.
     */
    public void checkAndApplyLazyCarryoverAndExpiry(LeaveBalance balance, Employee employee, int year) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = balance.getEffectiveCarriedForwardExpiryDate();

        // 1. Lazy Expiry Check
        if (!Boolean.TRUE.equals(balance.getCarriedForwardExpired())
                && today.isAfter(expiryDate)) {
            balance.setCarriedForwardExpiryDate(expiryDate);
            balance.setCarriedForwardExpired(true);
            leaveBalanceRepository.save(balance);
            log.info("Lazy expiry applied: marked carried-forward leaves as expired for employeeId={} in year {}",
                    balance.getEmployeeId(), year);
        }

        // 2. Lazy Carryover Check (if carried-forward quota is 0.0, not marked expired, and previous year has unused balance)
        if ((balance.getCarriedForwardLeaveQuota() == null || balance.getCarriedForwardLeaveQuota() == 0.0)
                && !Boolean.TRUE.equals(balance.getCarriedForwardExpired())
                && year > 2020) {

            var prevOpt = leaveBalanceRepository.findByEmployeeIdAndYear(balance.getEmployeeId(), year - 1);
            if (prevOpt.isPresent()) {
                LeaveBalance prev = prevOpt.get();
                double unusedEarned = Math.max(0.0,
                        (prev.getEarnedLeaveQuota() != null ? prev.getEarnedLeaveQuota() : 6.0)
                                - (prev.getEarnedLeaveUsed() != null ? prev.getEarnedLeaveUsed() : 0.0));

                if (unusedEarned > 0.0) {
                    double carryover = Math.min(MAX_EARNED_LEAVE_CARRYOVER_DAYS, unusedEarned);
                    LocalDate expiry = LocalDate.of(year, 3, 31);
                    boolean isExpired = today.isAfter(expiry);

                    balance.setCarriedForwardLeaveQuota(carryover);
                    balance.setCarriedForwardLeaveUsed(0.0);
                    balance.setCarriedForwardExpiryDate(expiry);
                    balance.setCarriedForwardExpired(isExpired);

                    leaveBalanceRepository.save(balance);
                    log.info("Lazy carryover applied for employeeId={}: carried {} days from year {} to {}",
                            balance.getEmployeeId(), carryover, year - 1, year);
                }
            }
        }
    }
}
