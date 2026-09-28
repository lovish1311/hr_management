package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HolidayLeavePolicyRule implements LeavePolicyRule {

    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public String getRuleName() {
        return "HolidayLeavePolicyRule";
    }

    @Override
    public int getOrder() {
        // Runs before quota validation to quickly reject redundant or invalid holiday requests
        return 15;
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            return;
        }

        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase().trim() : "CASUAL";

        if (type.contains("RESTRICTED")) {
            // Restricted Holiday must be single-day
            if (!request.getStartDate().equals(request.getEndDate())) {
                throw new IllegalArgumentException("A Restricted Holiday can only be applied for a single specific date.");
            }

            // Verify that an active, published Restricted Holiday exists on this date
            Optional<Holiday> rhOpt = holidayRepository.findActiveRestrictedHolidayOnDate(request.getStartDate());
            if (rhOpt.isEmpty()) {
                throw new IllegalArgumentException("No active restricted holiday is configured on " + request.getStartDate());
            }

            // Prevent duplicate applications for the same restricted holiday date
            List<LeaveRequest> existing = leaveRequestRepository.findOverlappingLeaves(
                    request.getEmployeeId(), request.getStartDate(), request.getEndDate());

            for (LeaveRequest lr : existing) {
                if (lr.getId() != null && lr.getId().equals(request.getId())) continue;
                String lrType = lr.getLeaveType() != null ? lr.getLeaveType().toUpperCase() : "";
                String lrStatus = lr.getStatus() != null ? lr.getStatus().toUpperCase() : "";
                if (lrType.contains("RESTRICTED") && ("PENDING".equals(lrStatus) || "APPROVED".equals(lrStatus))) {
                    throw new IllegalStateException("You already have a " + lrStatus.toLowerCase() +
                            " Restricted Holiday application for " + request.getStartDate());
                }
            }
        } else {
            // General Holiday Validation: single day leaves on official General Holidays should not be applied
            if (request.getStartDate().equals(request.getEndDate())) {
                List<Holiday> generalHolidays = holidayRepository.findActiveGeneralHolidayOnDate(request.getStartDate());
                if (!generalHolidays.isEmpty()) {
                    Holiday gh = generalHolidays.get(0);
                    throw new IllegalArgumentException(request.getStartDate() + " is already an official company holiday (" +
                            gh.getName() + "). Leave application is not required.");
                }
            }
        }
    }
}
