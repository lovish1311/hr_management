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
    private Double casualLeaveQuota = 12.0;

    @Builder.Default
    private Double casualLeaveUsed = 0.0;

    @Builder.Default
    private Double sickLeaveQuota = 10.0;

    @Builder.Default
    private Double sickLeaveUsed = 0.0;

    @Builder.Default
    private Double earnedLeaveQuota = 15.0;

    @Builder.Default
    private Double earnedLeaveUsed = 0.0;

    @Builder.Default
    private Double workFromHomeQuota = 0.0;

    @Builder.Default
    private Double workFromHomeUsed = 0.0;

    public double getCasualLeaveRemaining() {
        return (casualLeaveQuota != null ? casualLeaveQuota : 12.0) - (casualLeaveUsed != null ? casualLeaveUsed : 0.0);
    }

    public double getSickLeaveRemaining() {
        return (sickLeaveQuota != null ? sickLeaveQuota : 10.0) - (sickLeaveUsed != null ? sickLeaveUsed : 0.0);
    }

    public double getEarnedLeaveRemaining() {
        return (earnedLeaveQuota != null ? earnedLeaveQuota : 15.0) - (earnedLeaveUsed != null ? earnedLeaveUsed : 0.0);
    }

    public double getWorkFromHomeRemaining() {
        return (workFromHomeQuota != null ? workFromHomeQuota : 0.0) - (workFromHomeUsed != null ? workFromHomeUsed : 0.0);
    }
}
