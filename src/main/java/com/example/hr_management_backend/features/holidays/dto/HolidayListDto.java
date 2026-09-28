package com.example.hr_management_backend.features.holidays.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayListDto {
    private Long id;
    private String name;
    private Integer year;
    private String description;
    private String applicableGroup;
    private Boolean active;
    private Boolean published;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private int totalHolidays;
    private int generalHolidaysCount;
    private int restrictedHolidaysCount;

    private List<HolidayDto> holidays;
}
