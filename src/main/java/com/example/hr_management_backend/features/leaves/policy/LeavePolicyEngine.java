package com.example.hr_management_backend.features.leaves.policy;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeavePolicyEngine {

    private final List<LeavePolicyRule> policyRules;

    /**
     * Runs all registered policy rules to validate a leave request.
     */
    public void validateRequest(LeaveRequest request, Employee employee) {
        policyRules.stream()
                .sorted(Comparator.comparingInt(LeavePolicyRule::getOrder))
                .forEach(rule -> {
                    log.debug("Executing LeavePolicyRule: {}", rule.getRuleName());
                    rule.validate(request, employee);
                });
    }

    /**
     * Executes deduction computation across policy rules.
     */
    public void computeDeductions(LeaveRequest request) {
        policyRules.stream()
                .sorted(Comparator.comparingInt(LeavePolicyRule::getOrder))
                .forEach(rule -> rule.computeDeduction(request));
    }

    /**
     * Notifies policy rules when status transitions occur (e.g. APPROVED, REJECTED, CANCELLED).
     */
    public void handleStatusChange(LeaveRequest request, String oldStatus, String newStatus) {
        policyRules.stream()
                .sorted(Comparator.comparingInt(LeavePolicyRule::getOrder))
                .forEach(rule -> rule.onStatusChange(request, oldStatus, newStatus));
    }
}
