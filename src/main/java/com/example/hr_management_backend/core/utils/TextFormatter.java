package com.example.hr_management_backend.core.utils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade, thread-safe, memoized text and enum transformation utility.
 * Transforms raw IAM security tokens, database enum keys, and status codes into
 * clean, human-readable representations for client consumption while ensuring
 * near-zero CPU and memory overhead via ConcurrentHashMap memoization.
 */
public final class TextFormatter {

    private static final Map<String, String> ROLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> STATUS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> LEAVE_TYPE_CACHE = new ConcurrentHashMap<>();

    private static final Set<String> ACRONYMS = Set.of(
            "HR", "UI", "UX", "ID", "IT", "BI", "QA", "AI", "ML", "CEO", "CTO", "CFO", "VP"
    );

    private TextFormatter() {
        // Prevent instantiation
    }

    /**
     * Converts technical role names to clean, Title Case representation.
     * Example: "ROLE_SUPER_ADMIN" -> "Super Admin", "ROLE_HR_ADMIN" -> "HR Admin"
     */
    public static String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "Employee";
        }
        return ROLE_CACHE.computeIfAbsent(role, TextFormatter::computeRoleFormat);
    }

    /**
     * Converts technical status enums to human-friendly Title Case representation.
     * Example: "WORK_FROM_HOME" -> "Work From Home", "PENDING_ACTIVATION" -> "Pending Activation"
     */
    public static String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Active";
        }
        return STATUS_CACHE.computeIfAbsent(status, TextFormatter::computeGeneralEnumFormat);
    }

    /**
     * Converts technical leave type tokens into clean user-facing labels.
     * Example: "CASUAL" -> "Casual Leave", "RESTRICTED_HOLIDAY" -> "Restricted Holiday"
     */
    public static String formatLeaveType(String leaveType) {
        if (leaveType == null || leaveType.isBlank()) {
            return "Casual Leave";
        }
        return LEAVE_TYPE_CACHE.computeIfAbsent(leaveType, TextFormatter::computeLeaveTypeFormat);
    }

    private static String computeRoleFormat(String raw) {
        String cleaned = raw.trim();
        if (cleaned.toUpperCase().startsWith("ROLE_")) {
            cleaned = cleaned.substring(5);
        }
        return computeGeneralEnumFormat(cleaned);
    }

    private static String computeGeneralEnumFormat(String raw) {
        String cleaned = raw.trim().replace('-', '_');
        String[] parts = cleaned.split("_+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            String upper = part.toUpperCase();
            if (ACRONYMS.contains(upper)) {
                sb.append(upper);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }

            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String computeLeaveTypeFormat(String raw) {
        String base = computeGeneralEnumFormat(raw);
        if (!base.toLowerCase().contains("leave") && !base.toLowerCase().contains("holiday")) {
            return base + " Leave";
        }
        return base;
    }
}
