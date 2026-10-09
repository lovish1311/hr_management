package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Service responsible for pro-rata leave accrual calculations for mid-year joiners.
 * 
 * Rules:
 * - Annual Quota is allocated proportionally based on eligible complete months in the joining year.
 * - Formula: (Annual Quota / 12.0) * Number of months working = Pro-rated quota.
 * - Standard HR 15th-Day Cutoff Rule:
 *     * Joining on or before the 15th: that month counts as an eligible worked month.
 *     * Joining after the 15th: eligible accrual starts from the next calendar month.
 * - Accrued quotas are rounded to the nearest 0.5 (e.g. 2.3 -> 2.5).
 * - Employees joining prior to the target year receive full annual quotas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveAccrualService {

    public static final double STANDARD_CASUAL_LEAVE_QUOTA = 6.0;
    public static final double STANDARD_SICK_LEAVE_QUOTA = 6.0;
    public static final double STANDARD_EARNED_LEAVE_QUOTA = 6.0;
    public static final double STANDARD_RESTRICTED_HOLIDAY_QUOTA = 2.0;

    /**
     * Calculates the number of eligible working months in the target year.
     * 
     * @param joiningDate the date employee joined the company
     * @param targetYear  the calendar year being evaluated
     * @return number of eligible months (0 to 12)
     */
    public int calculateAccrualMonthsWorked(LocalDate joiningDate, int targetYear) {
        if (joiningDate == null) {
            // Default to full year if joining date is not specified
            return 12;
        }

        int joinYear = joiningDate.getYear();

        if (joinYear < targetYear) {
            // Joined in a previous year -> full year entitlement
            return 12;
        }

        if (joinYear > targetYear) {
            // Joins in a future year -> zero entitlement for this year
            return 0;
        }

        // Joined in the target year: apply the 15th-day cutoff rule
        int startMonth;
        if (joiningDate.getDayOfMonth() <= 15) {
            startMonth = joiningDate.getMonthValue();
        } else {
            startMonth = joiningDate.getMonthValue() + 1;
        }

        if (startMonth > 12) {
            return 0;
        }

        return Math.max(0, 12 - startMonth + 1);
    }

    /**
     * Calculates pro-rated quota rounded to the nearest 0.5 day.
     * Formula: (Annual Quota / 12.0) * monthsWorked
     * Example: 2.3 days -> 2.5 days; 3.0 days -> 3.0 days
     */
    public double calculateProratedQuota(double annualQuota, int monthsWorked) {
        if (monthsWorked >= 12) {
            return annualQuota;
        }
        if (monthsWorked <= 0) {
            return 0.0;
        }

        double rawProrated = (annualQuota / 12.0) * monthsWorked;
        // Round to nearest 0.5
        return Math.round(rawProrated * 2.0) / 2.0;
    }

    /**
     * Applies pro-rata accrual calculations to a newly created or evaluated LeaveBalance.
     */
    public void applyAccrualToBalance(LeaveBalance balance, Employee employee, int targetYear, double configuredRhQuota) {
        LocalDate joiningDate = (employee != null) ? employee.getJoiningDate() : null;
        int monthsWorked = calculateAccrualMonthsWorked(joiningDate, targetYear);

        boolean isMidYear = (joiningDate != null && joiningDate.getYear() == targetYear && monthsWorked < 12);

        if (isMidYear) {
            double proratedCasual = calculateProratedQuota(STANDARD_CASUAL_LEAVE_QUOTA, monthsWorked);
            double proratedSick = calculateProratedQuota(STANDARD_SICK_LEAVE_QUOTA, monthsWorked);
            double proratedEarned = calculateProratedQuota(STANDARD_EARNED_LEAVE_QUOTA, monthsWorked);
            double proratedRh = calculateProratedQuota(configuredRhQuota, monthsWorked);

            balance.setCasualLeaveQuota(proratedCasual);
            balance.setSickLeaveQuota(proratedSick);
            balance.setEarnedLeaveQuota(proratedEarned);
            balance.setRestrictedHolidayQuota(proratedRh);

            balance.setIsProrated(true);
            balance.setAccrualMonthsWorked(monthsWorked);
            balance.setAccrualNotes(String.format("Pro-rated for %d eligible month(s) based on joining date %s (15th-day cutoff rule).",
                    monthsWorked, joiningDate));

            log.info("Applied pro-rated leave accrual for employeeId={}: months={}, CL={}, SL={}, EL={}, RH={}",
                    balance.getEmployeeId(), monthsWorked, proratedCasual, proratedSick, proratedEarned, proratedRh);
        } else {
            balance.setCasualLeaveQuota(STANDARD_CASUAL_LEAVE_QUOTA);
            balance.setSickLeaveQuota(STANDARD_SICK_LEAVE_QUOTA);
            balance.setEarnedLeaveQuota(STANDARD_EARNED_LEAVE_QUOTA);
            balance.setRestrictedHolidayQuota(configuredRhQuota);

            balance.setIsProrated(false);
            balance.setAccrualMonthsWorked(12);
            balance.setAccrualNotes("Full annual quota assigned.");
        }
    }
}
