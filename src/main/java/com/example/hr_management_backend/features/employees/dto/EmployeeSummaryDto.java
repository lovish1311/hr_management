package com.example.hr_management_backend.features.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSummaryDto {
    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String designation;
    private String role;
    private String status;
    private String phoneNumber;
    private java.time.LocalDate joiningDate;
    private Boolean isAttendanceTracked;
    private String departmentCategory;
    private java.time.LocalTime lateArrivalAllowedUntil;
    private java.time.LocalTime earlyOutAllowedAfter;
    private Long managerId;
    private String managerName;
    private String todayAttendanceStatus; // e.g. PRESENT, LATE, ABSENT, ON_LEAVE, EXEMPT
    private Integer leaveBalance;
}
