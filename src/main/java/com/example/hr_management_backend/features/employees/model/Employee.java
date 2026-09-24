package com.example.hr_management_backend.features.employees.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_employee_code", columnList = "employeeCode"),
    @Index(name = "idx_employee_dept", columnList = "department"),
    @Index(name = "idx_employee_manager", columnList = "manager_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String department;

    private String designation;

    private String role; // e.g. EMPLOYEE, MANAGER, HR, SUPER_ADMIN

    @Column(unique = true)
    private String employeeCode;

    private LocalDate joiningDate;

    @Builder.Default
    private String employmentType = "FULL_TIME"; // FULL_TIME, PART_TIME, CONTRACT, INTERN

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, PROBATION, ON_LEAVE, TERMINATED

    private String phoneNumber;

    private LocalDate dateOfBirth;

    private String gender;

    private String address;

    private String emergencyContactName;

    private String emergencyContactPhone;

    private String biometricName; // Biometric machine alias (e.g. "AbhishekG", "ishu Saini", "Lovish")

    private java.time.LocalTime lateArrivalAllowedUntil; // HR Overpower Standing Exemption e.g. 12:00 PM

    private java.time.LocalTime earlyOutAllowedAfter; // HR Overpower Standing Exemption e.g. 05:00 PM

    @Builder.Default
    private Boolean isAttendanceTracked = true; // HR toggle to enable/disable attendance tracking

    @Builder.Default
    @Column(name = "has_tambola_access")
    private Boolean hasTambolaAccess = false; // Admin grant for Tambola game management

    private String departmentCategory; // Engineering, Sales, Design, Marketing, General

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager; // Assigned manager or leave approver
}

