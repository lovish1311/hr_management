package com.example.hr_management_backend.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormatterTest {

    @Test
    void testFormatRole() {
        assertEquals("Super Admin", TextFormatter.formatRole("ROLE_SUPER_ADMIN"));
        assertEquals("HR Admin", TextFormatter.formatRole("ROLE_HR_ADMIN"));
        assertEquals("HR", TextFormatter.formatRole("HR"));
        assertEquals("Manager", TextFormatter.formatRole("ROLE_MANAGER"));
        assertEquals("Employee", TextFormatter.formatRole("ROLE_EMPLOYEE"));
        assertEquals("UI UX Designer", TextFormatter.formatRole("UI_UX_DESIGNER"));
        assertEquals("Employee", TextFormatter.formatRole(null));
        assertEquals("Employee", TextFormatter.formatRole(""));
    }

    @Test
    void testFormatStatus() {
        assertEquals("Work From Home", TextFormatter.formatStatus("WORK_FROM_HOME"));
        assertEquals("On Leave", TextFormatter.formatStatus("ON_LEAVE"));
        assertEquals("Notice Period", TextFormatter.formatStatus("NOTICE_PERIOD"));
        assertEquals("Pending Activation", TextFormatter.formatStatus("PENDING_ACTIVATION"));
        assertEquals("Active", TextFormatter.formatStatus("ACTIVE"));
        assertEquals("Active", TextFormatter.formatStatus(null));
    }

    @Test
    void testFormatLeaveType() {
        assertEquals("Casual Leave", TextFormatter.formatLeaveType("CASUAL"));
        assertEquals("Casual Leave", TextFormatter.formatLeaveType("CASUAL_LEAVE"));
        assertEquals("Sick Leave", TextFormatter.formatLeaveType("SICK"));
        assertEquals("Restricted Holiday", TextFormatter.formatLeaveType("RESTRICTED_HOLIDAY"));
        assertEquals("Paid Leave", TextFormatter.formatLeaveType("PAID"));
        assertEquals("Casual Leave", TextFormatter.formatLeaveType(null));
    }

    @Test
    void testMemoizationCache() {
        String first = TextFormatter.formatRole("ROLE_SUPER_ADMIN");
        String second = TextFormatter.formatRole("ROLE_SUPER_ADMIN");
        assertEquals("Super Admin", first);
        assertEquals(first, second);
    }
}
