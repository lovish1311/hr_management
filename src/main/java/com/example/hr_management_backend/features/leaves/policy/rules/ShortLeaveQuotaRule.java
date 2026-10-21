package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortLeaveQuotaRule implements LeavePolicyRule {

    private final LeaveRequestRepository leaveRequestRepository;

    public static final int MAX_FREE_SHORT_LEAVES_PER_MONTH = 2;
    public static final int MAX_MINUTES_PER_PASS = 120;
    public static final int MAX_DAILY_SHORT_BREAK_MINUTES = 120;
    public static final int MAX_DAILY_COMBINED_MINUTES = 180;

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

        // 1. Mandatory Timestamps Validation (Defeats Null Injection)
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("Start time and end time are required for intra-day permissions (Short Break, Early Out, Late Arrival).");
        }

        // 2. Compute duration with Cross-Midnight / Night Shift support
        long incomingMinutes = computeDurationMinutes(request.getStartTime(), request.getEndTime());
        if (incomingMinutes <= 0) {
            throw new IllegalArgumentException("Permission duration must be strictly greater than 0 minutes.");
        }
        if (incomingMinutes > MAX_MINUTES_PER_PASS) {
            throw new IllegalStateException("Single intra-day permission duration cannot exceed 2 hours (120 minutes). Requested: " + incomingMinutes + " minutes.");
        }

        // 3. Option 2 Enforcement: Same-day limits (120m per category, 180m combined cross-category)
        if (request.getEmployeeId() != null && request.getStartDate() != null) {
            List<LeaveRequest> sameDayLeaves = leaveRequestRepository.findActiveLeavesInRange(
                    request.getEmployeeId(), request.getStartDate(), request.getStartDate());

            long sameCategoryMinsToday = 0;
            long totalCombinedMinsToday = 0;
            boolean isReqShort = type.contains("SHORT");

            for (LeaveRequest existing : sameDayLeaves) {
                if (request.getId() != null && request.getId().equals(existing.getId())) continue;
                String st = existing.getStatus() != null ? existing.getStatus().toUpperCase() : "";
                if ("REJECTED".equals(st) || "CANCELLED".equals(st) || "WITHDRAWN".equals(st)) continue;

                if (Boolean.TRUE.equals(existing.getIsTimeBased()) && existing.getStartTime() != null && existing.getEndTime() != null) {
                    long mins = computeDurationMinutes(existing.getStartTime(), existing.getEndTime());
                    totalCombinedMinsToday += mins;
                    String extType = existing.getLeaveType() != null ? existing.getLeaveType().toUpperCase() : "";
                    if (isReqShort && extType.contains("SHORT")) {
                        sameCategoryMinsToday += mins;
                    }
                }
            }

            if ((sameCategoryMinsToday + incomingMinutes) > MAX_DAILY_SHORT_BREAK_MINUTES) {
                throw new IllegalStateException("Short break duration cannot exceed 120 minutes total on the same day.");
            }
            if ((totalCombinedMinsToday + incomingMinutes) > MAX_DAILY_COMBINED_MINUTES) {
                throw new IllegalStateException("Combined intra-day permissions cannot exceed 180 minutes on the same day.");
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

    /**
     * Calculates duration in minutes with seamless cross-midnight / night shift bridging.
     */
    private long computeDurationMinutes(LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return 0;
        }
        if (end.isAfter(start)) {
            return Duration.between(start, end).toMinutes();
        }
        // Cross-midnight shift (e.g. 23:30 to 01:00)
        long toMidnight = Duration.between(start, LocalTime.MAX).toMinutes() + 1;
        long fromMidnight = Duration.between(LocalTime.MIN, end).toMinutes();
        return toMidnight + fromMidnight;
    }
}
