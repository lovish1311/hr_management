package com.example.hr_management_backend.features.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDetailDto {
    private Long id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private String designation;
    private String role;
    private LocalDate joiningDate;
    private String employmentType;
    private String status;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Long managerId;
    private String managerName;
}
