package com.example.hr_management_backend.features.leaves.policy;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;

/**
 * Pluggable Strategy interface for Leave Policy validation and deduction rules.
 * Adheres to Open-Closed Principle (SOLID) — new policy rules (Sandwich policy, AI rules,
 * custom quotas) can be added as Spring beans without altering core LeaveService logic.
 */
public interface LeavePolicyRule {

    /**
     * Human-readable rule identifier.
     */
    String getRuleName();

    /**
     * Execution order priority (lower runs earlier).
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Validates a leave request prior to saving.
     * Throws IllegalArgumentException or IllegalStateException if policy constraints fail.
     */
    void validate(LeaveRequest request, Employee employee);

    /**
     * Hook to compute or adjust total days / deduction amounts before saving.
     */
    default void computeDeduction(LeaveRequest request) {}

    /**
     * Hook triggered when a leave request transitions state (e.g. PENDING -> APPROVED / CANCELLED).
     */
    default void onStatusChange(LeaveRequest request, String oldStatus, String newStatus) {}
}
