package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Validates that an employee's leave request does not conflict with existing active leaves.
 * 
 * Scalability & System Design Features:
 * 1. Half-day Granularity: Session 1 (Morning: 10:00 - 14:00) and Session 2 (Afternoon: 15:00 - 19:00)
 *    can coexist on the exact same date without conflict, allowing employees to apply two half-days.
 * 2. Multi-day Boundary Support: Multi-day leaves ending at Session 1 allow a subsequent leave starting
 *    at Session 2 on that boundary date, and vice versa.
 * 3. Intra-day Time-based Permissions: Short breaks/early outs on distinct non-overlapping time windows can coexist.
 * 4. Query Optimization: Leverages composite index (employeeId, startDate, endDate) to fetch only active overlapping
 *    records instead of loading the employee's entire multi-year leave history into memory.
 * 5. Terminal Status Filtering: Automatically ignores REJECTED, CANCELLED, and WITHDRAWN leaves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionOverlapRule implements LeavePolicyRule {

    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public String getRuleName() {
        return "SessionOverlapRule";
    }

    @Override
    public int getOrder() {
        return 10; // High priority: check dates and sessions first
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }
        if (request.getEmployeeId() == null) {
            return;
        }

        // Query Optimization: Only fetch active leaves intersecting [startDate, endDate] using composite DB index
        List<LeaveRequest> existingLeaves = leaveRequestRepository.findActiveLeavesInRange(
                request.getEmployeeId(), request.getStartDate(), request.getEndDate());

        for (LeaveRequest existing : existingLeaves) {
            // Ignore self when updating existing record
            if (request.getId() != null && request.getId().equals(existing.getId())) {
                continue;
            }

            // Exclude inactive terminal statuses
            String st = existing.getStatus() != null ? existing.getStatus().toUpperCase() : "";
            if ("REJECTED".equals(st) || "CANCELLED".equals(st) || "WITHDRAWN".equals(st)) {
                continue;
            }

            // Intra-day time-based permissions (e.g. short break, early out)
            boolean isReqTime = Boolean.TRUE.equals(request.getIsTimeBased());
            boolean isExtTime = Boolean.TRUE.equals(existing.getIsTimeBased());
            if (isReqTime && isExtTime) {
                if (checkTimeBasedOverlap(request, existing)) {
                    throw new IllegalStateException("You already have an active time permission overlapping with this time window on "
                            + request.getStartDate() + ".");
                }
                continue;
            }

            // Date boundary check
            if (request.getStartDate().isAfter(existing.getEndDate()) || request.getEndDate().isBefore(existing.getStartDate())) {
                continue;
            }

            // Check session-level overlap on overlapping calendar dates
            LocalDate overlapStart = request.getStartDate().isAfter(existing.getStartDate())
                    ? request.getStartDate() : existing.getStartDate();
            LocalDate overlapEnd = request.getEndDate().isBefore(existing.getEndDate())
                    ? request.getEndDate() : existing.getEndDate();

            for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                boolean reqS1 = occupiesSession1(request, date);
                boolean reqS2 = occupiesSession2(request, date);

                boolean extS1 = occupiesSession1(existing, date);
                boolean extS2 = occupiesSession2(existing, date);

                if ((reqS1 && extS1) || (reqS2 && extS2)) {
                    String conflictedSession = (reqS1 && extS1 && reqS2 && extS2) ? "Full Day"
                            : (reqS1 && extS1) ? "Session 1 (Morning)" : "Session 2 (Afternoon)";
                    throw new IllegalStateException(String.format(
                            "Leave conflict on %s: You already have an active request (%s) covering %s.",
                            date, existing.getLeaveType(), conflictedSession));
                }
            }
        }
    }

    private boolean checkTimeBasedOverlap(LeaveRequest req, LeaveRequest existing) {
        if (!req.getStartDate().equals(existing.getStartDate())) {
            return false;
        }
        if (req.getStartTime() != null && req.getEndTime() != null
                && existing.getStartTime() != null && existing.getEndTime() != null) {
            return req.getStartTime().isBefore(existing.getEndTime()) && req.getEndTime().isAfter(existing.getStartTime());
        }
        return true;
    }

    /**
     * Determines whether the given request occupies Session 1 (Morning: 10:00 - 14:00) on a specific date.
     */
    private boolean occupiesSession1(LeaveRequest req, LocalDate date) {
        if (Boolean.TRUE.equals(req.getIsTimeBased())) {
            if (req.getStartTime() == null) return true;
            return req.getStartTime().isBefore(LocalTime.of(14, 0));
        }

        // Date outside request bounds
        if (date.isBefore(req.getStartDate()) || date.isAfter(req.getEndDate())) {
            return false;
        }

        // Single day request: check if it is a half-day targeting Session 2 only
        if (req.getStartDate().equals(req.getEndDate())) {
            boolean hasS1 = isSession1(req.getStartSession()) || isSession1(req.getEndSession());
            boolean hasS2 = isSession2(req.getStartSession()) || isSession2(req.getEndSession());

            boolean isHalfDay = (req.getTotalDays() != null && req.getTotalDays() <= 0.5) || (hasS1 != hasS2);
            if (isHalfDay) {
                // If explicitly Session 2 only, does NOT occupy Session 1
                return !(hasS2 && !hasS1);
            }
            // Full day occupies both Session 1 and Session 2
            return true;
        }

        // Multi-day request: intermediate days always occupy Session 1
        if (date.isAfter(req.getStartDate()) && date.isBefore(req.getEndDate())) {
            return true;
        }

        // Start day of multi-day: occupies Session 1 unless starting afternoon (Session 2)
        if (date.equals(req.getStartDate())) {
            return !isSession2(req.getStartSession());
        }

        // End day of multi-day: always occupies Session 1
        if (date.equals(req.getEndDate())) {
            return true;
        }

        return true;
    }

    /**
     * Determines whether the given request occupies Session 2 (Afternoon: 15:00 - 19:00) on a specific date.
     */
    private boolean occupiesSession2(LeaveRequest req, LocalDate date) {
        if (Boolean.TRUE.equals(req.getIsTimeBased())) {
            if (req.getStartTime() == null) return true;
            return req.getEndTime() != null
                    ? req.getEndTime().isAfter(LocalTime.of(14, 0))
                    : !req.getStartTime().isBefore(LocalTime.of(14, 0));
        }

        // Date outside request bounds
        if (date.isBefore(req.getStartDate()) || date.isAfter(req.getEndDate())) {
            return false;
        }

        // Single day request: check if it is a half-day targeting Session 1 only
        if (req.getStartDate().equals(req.getEndDate())) {
            boolean hasS1 = isSession1(req.getStartSession()) || isSession1(req.getEndSession());
            boolean hasS2 = isSession2(req.getStartSession()) || isSession2(req.getEndSession());

            boolean isHalfDay = (req.getTotalDays() != null && req.getTotalDays() <= 0.5) || (hasS1 != hasS2);
            if (isHalfDay) {
                // Occupies Session 2 only if explicitly Session 2
                return (hasS2 && !hasS1);
            }
            // Full day occupies both Session 1 and Session 2
            return true;
        }

        // Multi-day request: intermediate days always occupy Session 2
        if (date.isAfter(req.getStartDate()) && date.isBefore(req.getEndDate())) {
            return true;
        }

        // Start day of multi-day: always occupies Session 2
        if (date.equals(req.getStartDate())) {
            return true;
        }

        // End day of multi-day: occupies Session 2 unless ending morning (Session 1)
        if (date.equals(req.getEndDate())) {
            return !isSession1(req.getEndSession());
        }

        return true;
    }

    private boolean isSession1(String session) {
        if (session == null) return false;
        String s = session.toUpperCase().replaceAll("[\\s_\\-]", "");
        return s.equals("SESSION1") || s.equals("S1") || s.equals("MORNING") || s.equals("1");
    }

    private boolean isSession2(String session) {
        if (session == null) return false;
        String s = session.toUpperCase().replaceAll("[\\s_\\-]", "");
        return s.equals("SESSION2") || s.equals("S2") || s.equals("AFTERNOON") || s.equals("2");
    }
}
