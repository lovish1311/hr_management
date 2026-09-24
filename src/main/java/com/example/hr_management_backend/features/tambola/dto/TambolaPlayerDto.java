package com.example.hr_management_backend.features.tambola.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TambolaPlayerDto {
    private Long playerId;
    private Long employeeId;
    private String employeeName;
    private LocalDateTime joinedAt;
}
