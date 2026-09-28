package com.example.hr_management_backend.features.holidays.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayDto {
    private Long id;
    private Long holidayListId;
    private String name;
    private LocalDate date;
    private String type; // GENERAL, RESTRICTED
    private String description;
    private Boolean active;

    // Computed contextual fields for the employee UI
    private String status; // HOLIDAY, NOT_APPLIED, PENDING, APPROVED, REJECTED
    private Long leaveRequestId;
}
