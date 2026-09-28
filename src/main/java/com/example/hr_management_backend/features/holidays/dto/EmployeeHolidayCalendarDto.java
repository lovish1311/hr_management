package com.example.hr_management_backend.features.holidays.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeHolidayCalendarDto {
    private Integer year;
    private Double restrictedHolidayQuota;
    private Double restrictedHolidayUsed;
    private Double restrictedHolidayRemaining;
    private List<HolidayDto> holidays;
}
