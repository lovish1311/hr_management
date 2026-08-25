package com.example.hr_management_backend.features.leaves.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employeeId", "year"})
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

    @Column(nullable = false)
    private Integer year;

    @Builder.Default
    private Integer casualLeaveQuota = 12;

    @Builder.Default
    private Integer casualLeaveUsed = 0;

    @Builder.Default
    private Integer sickLeaveQuota = 10;

    @Builder.Default
    private Integer sickLeaveUsed = 0;

    @Builder.Default
    private Integer earnedLeaveQuota = 15;

    @Builder.Default
    private Integer earnedLeaveUsed = 0;

    @Builder.Default
    private Integer workFromHomeQuota = 0;

    @Builder.Default
    private Integer workFromHomeUsed = 0;

    public int getCasualLeaveRemaining() {
        return casualLeaveQuota - casualLeaveUsed;
    }

    public int getSickLeaveRemaining() {
        return sickLeaveQuota - sickLeaveUsed;
    }

    public int getEarnedLeaveRemaining() {
        return earnedLeaveQuota - earnedLeaveUsed;
    }

    public int getWorkFromHomeRemaining() {
        return workFromHomeQuota - workFromHomeUsed; // Will naturally go into negatives
    }
}
