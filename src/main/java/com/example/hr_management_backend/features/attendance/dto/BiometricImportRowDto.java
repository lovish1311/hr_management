package com.example.hr_management_backend.features.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricImportRowDto {
    private String employeeName;
    private Long matchedEmployeeId;
    private String matchedEmployeeName;
    private LocalTime firstInTime;
    private LocalTime lastOutTime;
    private String status; // PRESENT, LATE, UNEXCUSED_ABSENT, ON_LEAVE
    private String statusLabel;
    private String notes;
    private boolean isMatched;
}
