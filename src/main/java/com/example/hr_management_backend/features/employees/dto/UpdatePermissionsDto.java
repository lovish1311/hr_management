package com.example.hr_management_backend.features.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePermissionsDto {
    private Boolean isAttendanceTracked;
    private String lateArrivalAllowedUntil; // e.g. "12:00" or "12:00:00" or null
    private String earlyOutAllowedAfter;    // e.g. "17:00" or "17:00:00" or null
}
