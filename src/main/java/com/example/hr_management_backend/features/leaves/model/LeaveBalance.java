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

    public double getEarnedLeaveRemaining() {
        double quota = earnedLeaveQuota != null ? earnedLeaveQuota : 6.0;
        double used = earnedLeaveUsed != null ? earnedLeaveUsed : 0.0;
        double pending = earnedLeavePending != null ? earnedLeavePending : 0.0;
        return Math.max(0.0, quota - used - pending);
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
