package com.example.hr_management_backend.features.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceCalendarDayDto {
    private LocalDate date;
    private String status; // PRESENT, LATE, PAID_LEAVE, LOP_LEAVE, UNEXCUSED_ABSENT, HOLIDAY, WEEKEND, UPCOMING
    private String statusLabel;
    private String leaveType;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private String notes;
    private boolean isWeekend;
    private boolean isHoliday;
    private Long leaveRequestId;
    private Integer totalWorkingMinutes;
}
