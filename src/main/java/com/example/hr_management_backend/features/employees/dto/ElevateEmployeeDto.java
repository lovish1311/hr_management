package com.example.hr_management_backend.features.employees.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElevateEmployeeDto {

    private String role; // Functional role: e.g. "HR", "MANAGER", "EMPLOYEE"
    private String systemRole; // Administrative tier: e.g. "NONE", "ADMIN", "SUPER_ADMIN"

    @Builder.Default
    private List<String> authorities = new ArrayList<>(); // e.g. ["PAYROLL_MANAGE", "LEAVE_APPROVE_ALL"]
}
