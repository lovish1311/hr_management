package com.example.hr_management_backend.features.holidays;

import com.example.hr_management_backend.features.attendance.dto.AttendanceCalendarDayDto;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.holidays.dto.EmployeeHolidayCalendarDto;
import com.example.hr_management_backend.features.holidays.dto.HolidayDto;
import com.example.hr_management_backend.features.holidays.dto.HolidayListDto;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.model.HolidayList;
import com.example.hr_management_backend.features.holidays.repository.HolidayListRepository;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import com.example.hr_management_backend.features.holidays.service.HolidayService;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class HolidaySystemIntegrationTest {

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private HolidayListRepository holidayListRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceService attendanceService;

    private Employee testEmployee;
    private Employee testManager;

    @BeforeEach
    void setUp() {
        leaveRequestRepository.deleteAll();
        holidayRepository.deleteAll();
        holidayListRepository.deleteAll();

        testManager = employeeRepository.findByEmail("manager.holiday.test@company.com").orElseGet(() -> {
            Employee mgr = Employee.builder()
                    .firstName("Sarah")
                    .lastName("Manager")
                    .email("manager.holiday.test@company.com")
                    .department("HR")
                    .role("MANAGER")
                    .build();
            return employeeRepository.save(mgr);
        });

        testEmployee = employeeRepository.findByEmail("employee.holiday.test@company.com").orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName("John")
                    .lastName("Employee")
                    .email("employee.holiday.test@company.com")
                    .department("Engineering")
                    .role("EMPLOYEE")
                    .manager(testManager)
                    .build();
            return employeeRepository.save(emp);
        });

        LeaveBalance balance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), 2026);
        balance.setCasualLeaveUsed(0.0);
        balance.setSickLeaveUsed(0.0);
        balance.setEarnedLeaveUsed(0.0);
        balance.setRestrictedHolidayQuota(2.0);
        balance.setRestrictedHolidayUsed(0.0);
        balance.setRestrictedHolidayPending(0.0);
        leaveBalanceRepository.save(balance);
    }

    @Test
    @DisplayName("Year Isolation: Multiple years should remain completely independent")
    void testYearIsolation() {
        // Create 2026 Holiday List
        HolidayList list2026 = holidayService.createHolidayList(HolidayList.builder()
                .name("Official Holidays 2026")
                .year(2026)
                .description("Holidays for 2026")
                .build(), testManager.getId());

        // Create 2027 Holiday List
        HolidayList list2027 = holidayService.createHolidayList(HolidayList.builder()
                .name("Official Holidays 2027")
                .year(2027)
                .description("Holidays for 2027")
                .build(), testManager.getId());

        // Add 15 August 2026
        holidayService.addHoliday(list2026.getId(), Holiday.builder()
                .name("Independence Day 2026")
                .date(LocalDate.of(2026, 8, 15))
                .type("GENERAL")
                .build());

        // Add 15 August 2027
        holidayService.addHoliday(list2027.getId(), Holiday.builder()
                .name("Independence Day 2027")
                .date(LocalDate.of(2027, 8, 15))
                .type("GENERAL")
                .build());

        List<HolidayListDto> lists2026 = holidayService.getHolidayListsByYear(2026);
        assertEquals(1, lists2026.size());
        assertEquals("Official Holidays 2026", lists2026.get(0).getName());
        assertEquals(1, lists2026.get(0).getTotalHolidays());
        assertEquals(LocalDate.of(2026, 8, 15), lists2026.get(0).getHolidays().get(0).getDate());

        List<HolidayListDto> lists2027 = holidayService.getHolidayListsByYear(2027);
        assertEquals(1, lists2027.size());
        assertEquals("Official Holidays 2027", lists2027.get(0).getName());
        assertEquals(LocalDate.of(2027, 8, 15), lists2027.get(0).getHolidays().get(0).getDate());
    }

    @Test
    @DisplayName("Duplicate Protection: Adding a holiday on an already configured date in the same list must fail")
    void testDuplicateProtection() {
        HolidayList list = holidayService.createHolidayList(HolidayList.builder()
                .name("National Holidays 2026")
                .year(2026)
                .build(), testManager.getId());

        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Republic Day")
                .date(LocalDate.of(2026, 1, 26))
                .type("GENERAL")
                .build());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            holidayService.addHoliday(list.getId(), Holiday.builder()
                    .name("Duplicate Republic Day")
                    .date(LocalDate.of(2026, 1, 26))
                    .type("GENERAL")
                    .build());
        });

        assertTrue(ex.getMessage().contains("already configured for date"));
    }

    @Test
    @DisplayName("General Holiday: Prevents redundant leave applications and reflects in attendance calendar")
    void testGeneralHolidayAttendanceAndLeaveRules() {
        HolidayList list = holidayService.createHolidayList(HolidayList.builder()
                .name("2026 Public Holidays")
                .year(2026)
                .build(), testManager.getId());

        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Independence Day")
                .date(LocalDate.of(2026, 8, 15))
                .type("GENERAL")
                .build());

        // Publish the list
        holidayService.togglePublishHolidayList(list.getId(), true);

        // Employee attempts to apply for Casual Leave on 2026-08-15
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            leaveService.applyForLeave(LeaveRequest.builder()
                    .employeeId(testEmployee.getId())
                    .startDate(LocalDate.of(2026, 8, 15))
                    .endDate(LocalDate.of(2026, 8, 15))
                    .leaveType("CASUAL")
                    .reason("Visiting family")
                    .build());
        });

        assertTrue(ex.getMessage().contains("already an official company holiday"));

        // Verify Attendance calendar recognizes it as HOLIDAY
        List<AttendanceCalendarDayDto> augustSummary = attendanceService.getMonthlyCalendarSummary(testEmployee.getId(), 2026, 8);
        AttendanceCalendarDayDto aug15 = augustSummary.stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2026, 8, 15)))
                .findFirst()
                .orElse(null);

        assertNotNull(aug15);
        assertTrue(aug15.isHoliday());
        // Aug 15 2026 happens to be Saturday. Let's test a weekday holiday as well: 2026-10-02 (Gandhi Jayanti, Friday)
        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Gandhi Jayanti")
                .date(LocalDate.of(2026, 10, 2))
                .type("GENERAL")
                .build());

        List<AttendanceCalendarDayDto> octSummary = attendanceService.getMonthlyCalendarSummary(testEmployee.getId(), 2026, 10);
        AttendanceCalendarDayDto oct2 = octSummary.stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2026, 10, 2)))
                .findFirst()
                .orElse(null);

        assertNotNull(oct2);
        assertTrue(oct2.isHoliday());
        assertEquals("HOLIDAY", oct2.getStatus());
        assertTrue(oct2.getStatusLabel().contains("Gandhi Jayanti (Holiday)"));
    }

    @Test
    @DisplayName("Restricted Holiday: Full lifecycle including apply, approve, quota limit, and duplicate prevention")
    void testRestrictedHolidayLifecycle() {
        HolidayList list = holidayService.createHolidayList(HolidayList.builder()
                .name("Festival Holidays 2026")
                .year(2026)
                .build(), testManager.getId());

        // Add 3 Restricted Holidays
        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Diwali")
                .date(LocalDate.of(2026, 10, 20))
                .type("RESTRICTED")
                .build());

        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Govardhan Puja")
                .date(LocalDate.of(2026, 10, 21))
                .type("RESTRICTED")
                .build());

        holidayService.addHoliday(list.getId(), Holiday.builder()
                .name("Bhai Dooj")
                .date(LocalDate.of(2026, 10, 22))
                .type("RESTRICTED")
                .build());

        holidayService.togglePublishHolidayList(list.getId(), true);

        // 1. Initial Employee Calendar View
        EmployeeHolidayCalendarDto calendar = holidayService.getEmployeeHolidayCalendar(2026, testEmployee.getId());
        assertEquals(2.0, calendar.getRestrictedHolidayQuota());
        assertEquals(0.0, calendar.getRestrictedHolidayUsed());
        assertEquals(2.0, calendar.getRestrictedHolidayRemaining());

        HolidayDto diwaliDto = calendar.getHolidays().stream()
                .filter(h -> h.getName().equals("Diwali"))
                .findFirst().orElseThrow();
        assertEquals("NOT_APPLIED", diwaliDto.getStatus());

        // 2. Apply for Diwali (RH #1)
        LeaveRequest rh1 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .startDate(LocalDate.of(2026, 10, 20))
                .endDate(LocalDate.of(2026, 10, 20))
                .leaveType("RESTRICTED_HOLIDAY")
                .reason("Diwali celebration")
                .build());

        assertNotNull(rh1.getId());
        assertEquals("PENDING", rh1.getStatus());

        // Verify Calendar shows PENDING
        calendar = holidayService.getEmployeeHolidayCalendar(2026, testEmployee.getId());
        diwaliDto = calendar.getHolidays().stream().filter(h -> h.getName().equals("Diwali")).findFirst().orElseThrow();
        assertEquals("PENDING", diwaliDto.getStatus());
        assertEquals(rh1.getId(), diwaliDto.getLeaveRequestId());
        // Remaining should deduct pending: quota 2.0 - pending 1.0 = remaining 1.0
        assertEquals(1.0, calendar.getRestrictedHolidayRemaining());

        // 3. Duplicate Application on same RH date should fail
        IllegalStateException dupEx = assertThrows(IllegalStateException.class, () -> {
            leaveService.applyForLeave(LeaveRequest.builder()
                    .employeeId(testEmployee.getId())
                    .startDate(LocalDate.of(2026, 10, 20))
                    .endDate(LocalDate.of(2026, 10, 20))
                    .leaveType("RESTRICTED_HOLIDAY")
                    .reason("Trying again")
                    .build());
        });
        assertTrue(dupEx.getMessage().contains("conflict") || dupEx.getMessage().contains("already"));

        // 4. Manager Approves Diwali
        leaveService.updateStatus(rh1.getId(), "APPROVED", null, testManager.getId());

        calendar = holidayService.getEmployeeHolidayCalendar(2026, testEmployee.getId());
        diwaliDto = calendar.getHolidays().stream().filter(h -> h.getName().equals("Diwali")).findFirst().orElseThrow();
        assertEquals("APPROVED", diwaliDto.getStatus());
        assertEquals(1.0, calendar.getRestrictedHolidayUsed());
        assertEquals(1.0, calendar.getRestrictedHolidayRemaining());

        // 5. Apply and Approve Govardhan Puja (RH #2)
        LeaveRequest rh2 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .startDate(LocalDate.of(2026, 10, 21))
                .endDate(LocalDate.of(2026, 10, 21))
                .leaveType("RESTRICTED_HOLIDAY")
                .reason("Govardhan Puja")
                .build());
        leaveService.updateStatus(rh2.getId(), "APPROVED", null, testManager.getId());

        calendar = holidayService.getEmployeeHolidayCalendar(2026, testEmployee.getId());
        assertEquals(2.0, calendar.getRestrictedHolidayUsed());
        assertEquals(0.0, calendar.getRestrictedHolidayRemaining());

        // 6. Attempt 3rd RH application (exceeds limit of 2) -> Must be rejected by server!
        IllegalStateException limitEx = assertThrows(IllegalStateException.class, () -> {
            leaveService.applyForLeave(LeaveRequest.builder()
                    .employeeId(testEmployee.getId())
                    .startDate(LocalDate.of(2026, 10, 22))
                    .endDate(LocalDate.of(2026, 10, 22))
                    .leaveType("RESTRICTED_HOLIDAY")
                    .reason("Bhai Dooj 3rd RH attempt")
                    .build());
        });
        assertTrue(limitEx.getMessage().contains("Insufficient") || limitEx.getMessage().contains("Effective Available: 0.0"));

        // 7. Manager rejects previously approved rh2 -> Balance is refunded!
        leaveService.updateStatus(rh2.getId(), "REJECTED", "Critical project deadline", testManager.getId());

        calendar = holidayService.getEmployeeHolidayCalendar(2026, testEmployee.getId());
        assertEquals(1.0, calendar.getRestrictedHolidayUsed());
        assertEquals(1.0, calendar.getRestrictedHolidayRemaining());

        // Now Bhai Dooj application can succeed since allowance is restored!
        LeaveRequest rh3 = leaveService.applyForLeave(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .startDate(LocalDate.of(2026, 10, 22))
                .endDate(LocalDate.of(2026, 10, 22))
                .leaveType("RESTRICTED_HOLIDAY")
                .reason("Bhai Dooj after refund")
                .build());
        assertNotNull(rh3.getId());
        assertEquals("PENDING", rh3.getStatus());
    }
}
