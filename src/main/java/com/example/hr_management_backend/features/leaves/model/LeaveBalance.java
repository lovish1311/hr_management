package com.example.hr_management_backend.features.leaves.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employeeId", "\"year\""})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Builder.Default
    private Double casualLeaveQuota = 6.0;

    @Builder.Default
    private Double casualLeaveUsed = 0.0;

    @Builder.Default
    private Double sickLeaveQuota = 6.0;

    @Builder.Default
    private Double sickLeaveUsed = 0.0;

    @Builder.Default
    private Double earnedLeaveQuota = 6.0;

    @Builder.Default
    private Double earnedLeaveUsed = 0.0;

    @Builder.Default
    private Double workFromHomeQuota = 0.0;

    @Builder.Default
    private Double workFromHomeUsed = 0.0;

    @Builder.Default
    private Double restrictedHolidayQuota = 2.0;

    @Builder.Default
    private Double restrictedHolidayUsed = 0.0;

    // --- Carried Forward / Year-End Processing Fields ---
    @Builder.Default
    private Double carriedForwardLeaveQuota = 0.0;

    @Builder.Default
    private Double carriedForwardLeaveUsed = 0.0;

    private java.time.LocalDate carriedForwardExpiryDate;

    @Builder.Default
    private Boolean carriedForwardExpired = false;

    // --- Accrual & Pro-Rata Fields for Mid-Year Joiners ---
    @Builder.Default
    private Boolean isProrated = false;

    private Integer accrualMonthsWorked;

    @Column(length = 500)
    private String accrualNotes;

    @Transient
    @Builder.Default
    private Double casualLeavePending = 0.0;

    @Transient
    @Builder.Default
    private Double sickLeavePending = 0.0;

    @Transient
    @Builder.Default
    private Double earnedLeavePending = 0.0;

    @Transient
    @Builder.Default
    private Double workFromHomePending = 0.0;

    @Transient
    @Builder.Default
    private Double restrictedHolidayPending = 0.0;

    public double getCasualLeaveRemaining() {
        double quota = casualLeaveQuota != null ? casualLeaveQuota : 6.0;
        double used = casualLeaveUsed != null ? casualLeaveUsed : 0.0;
        double pending = casualLeavePending != null ? casualLeavePending : 0.0;
        return Math.max(0.0, quota - used - pending);
    }
    public double getSickLeaveRemaining() {
        double quota = sickLeaveQuota != null ? sickLeaveQuota : 6.0;
        double used = sickLeaveUsed != null ? sickLeaveUsed : 0.0;
        double pending = sickLeavePending != null ? sickLeavePending : 0.0;
        return Math.max(0.0, quota - used - pending);
    }

    /**
     * Resolves the effective expiry date for carried-forward leaves.
     * Defaults to March 31st of the balance year if null.
     */
    public java.time.LocalDate getEffectiveCarriedForwardExpiryDate() {
        if (carriedForwardExpiryDate != null) {
            return carriedForwardExpiryDate;
        }
        return java.time.LocalDate.of(year != null ? year : java.time.LocalDate.now().getYear(), 3, 31);
    }

    /**
     * Returns true if carried-forward leaves have lapsed (explicitly flagged or passed expiry date).
     */
    public boolean isCarriedForwardCurrentlyExpired() {
        if (Boolean.TRUE.equals(carriedForwardExpired)) {
            return true;
        }
        return java.time.LocalDate.now().isAfter(getEffectiveCarriedForwardExpiryDate());
    }

    /**
     * Returns the unconsumed, non-expired carried forward balance (ignoring pending leaves).
     */
    public double getActiveCarriedForwardBalance() {
        if (isCarriedForwardCurrentlyExpired()) {
            return 0.0;
        }
        double quota = carriedForwardLeaveQuota != null ? carriedForwardLeaveQuota : 0.0;
        double used = carriedForwardLeaveUsed != null ? carriedForwardLeaveUsed : 0.0;
        return Math.max(0.0, quota - used);
    }

    /**
     * Unconsumed carried forward leave remaining after allocating pending requests with priority (FIFO).
     */
    public double getCarriedForwardLeaveRemaining() {
        double activeCarried = getActiveCarriedForwardBalance();
        if (activeCarried <= 0.0) {
            return 0.0;
        }
        double pending = earnedLeavePending != null ? earnedLeavePending : 0.0;
        double pendingOnCarried = Math.min(activeCarried, pending);
        return Math.max(0.0, activeCarried - pendingOnCarried);
    }

    /**
     * Remaining current year earned leaves. Pending leaves only deduct from current year
     * after the active carried-forward balance has been fully reserved.
     */
    public double getCurrentYearEarnedLeaveRemaining() {
        double quota = earnedLeaveQuota != null ? earnedLeaveQuota : 6.0;
        double used = earnedLeaveUsed != null ? earnedLeaveUsed : 0.0;
        double pending = earnedLeavePending != null ? earnedLeavePending : 0.0;

        double activeCarried = getActiveCarriedForwardBalance();
        double remainingPendingForCurrentYear = Math.max(0.0, pending - activeCarried);

        return Math.max(0.0, quota - used - remainingPendingForCurrentYear);
    }

    /**
     * Total available earned leave balance (Current Year Quota Remaining + Active Carried Forward Remaining).
     */
    public double getEarnedLeaveRemaining() {
        return getCurrentYearEarnedLeaveRemaining() + getCarriedForwardLeaveRemaining();
    }

    public double getTotalEarnedLeaveRemaining() {
        return getEarnedLeaveRemaining();
    }

    public String getEarnedLeaveBalanceDisplay() {
        return String.format("Current Year: %s | Carried Forward: %s | Total: %s",
                formatDays(getCurrentYearEarnedLeaveRemaining()),
                formatDays(getCarriedForwardLeaveRemaining()),
                formatDays(getEarnedLeaveRemaining()));
    }

    private String formatDays(double days) {
        if (days == (long) days) {
            return String.format("%d days", (long) days);
        } else {
            return String.format("%.1f days", days);
        }
    }

    public double getWorkFromHomeRemaining() {
        // Baseline starts at 0.0; tracks negative (-1, -2, -3...) when approved/used
        double used = workFromHomeUsed != null ? workFromHomeUsed : 0.0;
        return -used;
    }

    public double getRestrictedHolidayRemaining() {
        double quota = restrictedHolidayQuota != null ? restrictedHolidayQuota : 2.0;
        double used = restrictedHolidayUsed != null ? restrictedHolidayUsed : 0.0;
        double pending = restrictedHolidayPending != null ? restrictedHolidayPending : 0.0;
        return Math.max(0.0, quota - used - pending);
    }
}
