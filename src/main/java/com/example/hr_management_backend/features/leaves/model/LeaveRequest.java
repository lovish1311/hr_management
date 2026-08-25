package com.example.hr_management_backend.features.leaves.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leave_emp", columnList = "employeeId"),
    @Index(name = "idx_leave_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder.Default
    private String leaveType = "CASUAL"; // CASUAL, SICK, EARNED, UNPAID

    @Builder.Default
    private Integer totalDays = 1;


    private String reason;

    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    private String rejectionReason;

    private Long approvedBy; // Employee ID of approving manager/HR

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

