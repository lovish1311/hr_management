package com.example.hr_management_backend.features.employees.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignManagerDto {
    @NotNull(message = "Manager ID cannot be null")
    private Long managerId;
}
