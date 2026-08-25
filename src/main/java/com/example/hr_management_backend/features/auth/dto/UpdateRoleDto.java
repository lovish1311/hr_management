package com.example.hr_management_backend.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleDto {
    @NotBlank(message = "Role cannot be blank")
    private String role; // e.g. ROLE_SUPER_ADMIN, ROLE_HR, ROLE_MANAGER, ROLE_EMPLOYEE
}
