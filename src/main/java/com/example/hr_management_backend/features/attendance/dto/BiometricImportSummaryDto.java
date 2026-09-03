package com.example.hr_management_backend.features.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricImportSummaryDto {
    private LocalDate targetDate;
    private int totalProcessed;
    private int presentCount;
    private int lateCount;
    private int absentCount;
    private int unmatchedCount;
    private List<BiometricImportRowDto> rows;
}
