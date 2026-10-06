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

    @NotBlank(message = "System role is required")
    private String role; // e.g. "SUPER_ADMIN", "HR", "MANAGER", "EMPLOYEE"

    @Builder.Default
    private List<String> authorities = new ArrayList<>(); // e.g. ["PAYROLL_MANAGE", "LEAVE_APPROVE_ALL"]
}
