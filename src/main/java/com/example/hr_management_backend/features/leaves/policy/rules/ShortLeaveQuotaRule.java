package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortLeaveQuotaRule implements LeavePolicyRule {

    private final LeaveRequestRepository leaveRequestRepository;

    public static final int MAX_FREE_SHORT_LEAVES_PER_MONTH = 2;

    @Override
    public String getRuleName() {
        return "ShortLeaveQuotaRule";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "";
        boolean isTimeBased = type.contains("SHORT") || type.contains("EARLY") || type.contains("LATE");

        if (!isTimeBased) {
            return;
        }

        // Enforce max 2 hours (120 minutes) duration for intra-day permissions
        if (request.getStartTime() != null && request.getEndTime() != null) {
            long minutes = java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            if (minutes <= 0) {
                throw new IllegalArgumentException("End time must be strictly after start time.");
            }
            if (minutes > 120) {
                throw new IllegalStateException("Intra-day permission duration cannot exceed 2 hours (120 minutes). Requested: " + minutes + " minutes.");
            }
        }

        // Time-based permissions are logged for HR transparency without automated salary/leave deductions
        request.setTotalDays(0.0);
    }

    @Override
    public void computeDeduction(LeaveRequest request) {
        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "";
        boolean isTimeBased = type.contains("SHORT") || type.contains("EARLY") || type.contains("LATE");
        if (isTimeBased) {
            // Strictly zero automated deduction for intra-day permissions
            request.setTotalDays(0.0);
        }
    }
}
