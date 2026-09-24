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

        // Count short leaves applied in the same month
        if (request.getEmployeeId() != null && request.getStartDate() != null) {
            LocalDate start = request.getStartDate();
            List<LeaveRequest> allRequests = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(request.getEmployeeId());

            long shortLeavesThisMonth = allRequests.stream()
                    .filter(r -> {
                        if (request.getId() != null && request.getId().equals(r.getId())) return false;
                        String st = r.getStatus() != null ? r.getStatus().toUpperCase() : "";
                        if ("REJECTED".equals(st) || "CANCELLED".equals(st) || "WITHDRAWN".equals(st)) return false;

                        String rType = r.getLeaveType() != null ? r.getLeaveType().toUpperCase() : "";
                        boolean rTime = Boolean.TRUE.equals(r.getIsTimeBased()) || rType.contains("SHORT") || rType.contains("EARLY") || rType.contains("LATE");
                        return rTime && r.getStartDate() != null
                                && r.getStartDate().getYear() == start.getYear()
                                && r.getStartDate().getMonth() == start.getMonth();
                    }).count();

            if (shortLeavesThisMonth >= MAX_FREE_SHORT_LEAVES_PER_MONTH) {
                log.info("EmployeeId={} applying short leave #{}. Exceeded free monthly limit ({}). 0.5 Casual Leave deduction penalty triggered.",
                        request.getEmployeeId(), shortLeavesThisMonth + 1, MAX_FREE_SHORT_LEAVES_PER_MONTH);
                // Flag deduction on request
                request.setReason(request.getReason() + " [Quota Exceeded: 0.5 Day Penalty Applied]");
                request.setTotalDays(0.5);
            }
        }
    }

    @Override
    public void computeDeduction(LeaveRequest request) {
        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "";
        boolean isTimeBased = type.contains("SHORT") || type.contains("EARLY") || type.contains("LATE");
        if (!isTimeBased) return;

        if (request.getEmployeeId() != null && request.getStartDate() != null) {
            LocalDate start = request.getStartDate();
            List<LeaveRequest> allRequests = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(request.getEmployeeId());

            long shortLeavesThisMonth = allRequests.stream()
                    .filter(r -> {
                        if (request.getId() != null && request.getId().equals(r.getId())) return false;
                        String st = r.getStatus() != null ? r.getStatus().toUpperCase() : "";
                        if ("REJECTED".equals(st) || "CANCELLED".equals(st) || "WITHDRAWN".equals(st)) return false;

                        String rType = r.getLeaveType() != null ? r.getLeaveType().toUpperCase() : "";
                        boolean rTime = Boolean.TRUE.equals(r.getIsTimeBased()) || rType.contains("SHORT") || rType.contains("EARLY") || rType.contains("LATE");
                        return rTime && r.getStartDate() != null
                                && r.getStartDate().getYear() == start.getYear()
                                && r.getStartDate().getMonth() == start.getMonth();
                    }).count();

            if (shortLeavesThisMonth >= MAX_FREE_SHORT_LEAVES_PER_MONTH) {
                request.setTotalDays(0.5);
            }
        }
    }
}
