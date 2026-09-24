package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionOverlapRule implements com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule {

    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public String getRuleName() {
        return "SessionOverlapRule";
    }

    @Override
    public int getOrder() {
        return 10; // High priority: check dates first
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        // Check overlapping requests for same employee
        if (request.getEmployeeId() != null) {
            List<LeaveRequest> existing = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(request.getEmployeeId());
            boolean overlaps = existing.stream().anyMatch(e -> {
                if (request.getId() != null && request.getId().equals(e.getId())) return false;
                String st = e.getStatus() != null ? e.getStatus().toUpperCase() : "";
                if ("REJECTED".equals(st) || "CANCELLED".equals(st) || "WITHDRAWN".equals(st)) return false;

                // Time-based permissions on different times can coexist, but full/half day overlaps check dates
                boolean isReqTime = Boolean.TRUE.equals(request.getIsTimeBased());
                boolean isExistingTime = Boolean.TRUE.equals(e.getIsTimeBased());

                if (!isReqTime && !isExistingTime) {
                    return !request.getStartDate().isAfter(e.getEndDate()) && !request.getEndDate().isBefore(e.getStartDate());
                }
                return false;
            });

            if (overlaps) {
                throw new IllegalStateException("You already have an active leave request overlapping with these dates.");
            }
        }
    }
}
