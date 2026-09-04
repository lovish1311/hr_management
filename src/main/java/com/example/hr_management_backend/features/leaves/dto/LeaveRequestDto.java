package com.example.hr_management_backend.features.leaves.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeEmail;
    private String employeeDepartment;
    private String employeeDesignation;

    private LocalDate startDate;
    private LocalDate endDate;
    private String leaveType;
    private Double totalDays;
    private Boolean isTimeBased;
    private String startSession;
    private String endSession;
    private String reason;
    private String status;
    private String rejectionReason;
    private Long approvedBy;
    private String approverName;
    private LocalTime startTime;
    private LocalTime endTime;
    private String documentUrl;
    private String ccEmails;
    private String policySnapshot;
    private LocalDateTime createdAt;
}
