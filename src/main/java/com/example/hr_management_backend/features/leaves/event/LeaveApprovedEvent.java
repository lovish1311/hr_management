package com.example.hr_management_backend.features.leaves.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class LeaveApprovedEvent {
    private final Long employeeId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String leaveType;
}
