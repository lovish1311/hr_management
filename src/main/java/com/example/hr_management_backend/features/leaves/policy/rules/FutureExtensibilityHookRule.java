package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder rule demonstrating open extensibility for Sandwich Leave Policy,
 * AI-driven fraud detection, or custom enterprise compliance rules.
 */
@Component
@Slf4j
public class FutureExtensibilityHookRule implements LeavePolicyRule {

    @Override
    public String getRuleName() {
        return "FutureExtensibilityHookRule";
    }

    @Override
    public int getOrder() {
        return 999; // Runs last
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        // Open hook for Sandwich policy or AI rules integration in future
        log.debug("Extensibility hook evaluated for request ID={}", request.getId());
    }
}
