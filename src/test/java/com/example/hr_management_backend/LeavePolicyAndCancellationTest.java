package com.example.hr_management_backend;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.model.EmployeeLeaveQuota;
import com.example.hr_management_backend.features.leaves.repository.EmployeeLeaveQuotaRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.model.HolidayList;
import com.example.hr_management_backend.features.holidays.repository.HolidayListRepository;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LeavePolicyAndCancellationTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeLeaveQuotaRepository employeeLeaveQuotaRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private HolidayListRepository holidayListRepository;

    private Employee testEmployee;
    private Employee hrManager;

    @BeforeEach
    void setUp() {
        leaveRequestRepository.deleteAll();
        
        testEmployee = employeeRepository.findByEmail("lovish.test@company.com").orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName("Lovish")
                    .lastName("Test")
                    .email("lovish.test@company.com")
                    .department("Engineering")
                    .role("EMPLOYEE")
                    .build();
            return employeeRepository.save(emp);
        });

        hrManager = employeeRepository.findByEmail("hr.test@company.com").orElseGet(() -> {
            Employee emp = Employee.builder()
                    .firstName("HR")
                    .lastName("Manager")
                    .email("hr.test@company.com")
                    .department("HR")
                    .role("HR")
                    .build();
            return employeeRepository.save(emp);
        });

        // Initialize fresh balance for testing
        LeaveBalance balance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        balance.setCasualLeaveQuota(6.0);
        balance.setCasualLeaveUsed(0.0);
        balance.setSickLeaveQuota(6.0);
        balance.setSickLeaveUsed(0.0);
        balance.setEarnedLeaveQuota(6.0);
        balance.setEarnedLeaveUsed(0.0);
        balance.setWorkFromHomeQuota(0.0);
        balance.setWorkFromHomeUsed(0.0);
        leaveBalanceRepository.save(balance);
    }

    @Test
    @DisplayName("Scenario 1: Employee applies for pending leave and cancels it before manager review — balance unaffected")
    void testCancelPendingLeaveRequest() {
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Personal work")
                .build();

        LeaveRequest saved = leaveService.applyForLeave(request);
        assertEquals("PENDING", saved.getStatus());
        assertEquals(2.0, saved.getTotalDays());

        // Cancel request
        LeaveRequest cancelled = leaveService.cancelLeaveRequest(saved.getId(), testEmployee.getId());
        assertEquals("CANCELLED", cancelled.getStatus());

        // Verify balance unaffected
        LeaveBalance balance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(6.0, balance.getCasualLeaveRemaining());
    }

    @Test
    @DisplayName("Scenario 2: Employee applies, Manager approves, then Employee initiates cancel — enters CANCEL_REQUESTED (no refund) until Manager approves")
    void testCancelApprovedLeaveRequestRefundsBalance() {
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("SICK")
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(6))
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Medical appointment")
                .build();

        LeaveRequest saved = leaveService.applyForLeave(request);

        // Manager approves
        LeaveRequest approved = leaveService.updateStatus(saved.getId(), "APPROVED", null, hrManager.getId());
        assertEquals("APPROVED", approved.getStatus());

        LeaveBalance balanceAfterApprove = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(2.0, balanceAfterApprove.getSickLeaveUsed());
        assertEquals(4.0, balanceAfterApprove.getSickLeaveRemaining());

        // 1. Employee self-initiates cancel on APPROVED leave -> enters CANCEL_REQUESTED
        LeaveRequest cancelRequested = leaveService.cancelLeaveRequest(saved.getId(), testEmployee.getId());
        assertEquals("CANCEL_REQUESTED", cancelRequested.getStatus(), "Employee cancel on APPROVED leave must require Manager review");

        // Balance MUST NOT be refunded yet while awaiting manager approval
        LeaveBalance balancePendingReview = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(2.0, balancePendingReview.getSickLeaveUsed(), "Days must remain deducted while in CANCEL_REQUESTED");

        // 2. Manager reviews and approves the cancellation request -> transitions to CANCELLED and refunds balance
        LeaveRequest finalCancelled = leaveService.cancelLeaveRequest(saved.getId(), hrManager.getId());
        assertEquals("CANCELLED", finalCancelled.getStatus());

        LeaveBalance balanceAfterCancel = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(0.0, balanceAfterCancel.getSickLeaveUsed(), "Used days must be refunded once Manager approves cancellation");
        assertEquals(6.0, balanceAfterCancel.getSickLeaveRemaining());
    }

    @Test
    @DisplayName("Scenario 3: Short leave and time-based permissions — permitted up to 2 hours with 0 days deduction")
    void testShortLeaveMonthlyQuotaPenalty() {
        LocalDate currentMonth = LocalDate.now();

        // Short Leave 1 (1 hour)
        LeaveRequest req1 = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("SHORT_BREAK")
                .startDate(currentMonth.withDayOfMonth(2))
                .endDate(currentMonth.withDayOfMonth(2))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .reason("Bank work")
                .build();
        LeaveRequest savedReq1 = leaveService.applyForLeave(req1);
        assertEquals(0.0, savedReq1.getTotalDays());

        // Short Leave exceeding 2 hours throws exception
        LeaveRequest reqExcess = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("SHORT_BREAK")
                .startDate(currentMonth.withDayOfMonth(10))
                .endDate(currentMonth.withDayOfMonth(10))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(13, 0)) // 3 hours > 2 hours
                .reason("Long errands")
                .build();

        assertThrows(IllegalStateException.class, () -> leaveService.applyForLeave(reqExcess));
    }

    @Test
    @DisplayName("Scenario 4: Two half-day leaves (Session 1 & Session 2) on the same date are both permitted to get published")
    void testAllowTwoHalfDaysOnSameDate() {
        LocalDate targetDate = LocalDate.now().plusDays(15);

        // 1. Apply Session 1 (Morning half day: 0.5 days)
        LeaveRequest s1Request = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(targetDate)
                .endDate(targetDate)
                .startSession("Session 1")
                .endSession("Session 1")
                .reason("Morning doctor appointment")
                .build();

        LeaveRequest s1Saved = leaveService.applyForLeave(s1Request);
        assertNotNull(s1Saved.getId());
        assertEquals("PENDING", s1Saved.getStatus());
        assertEquals(0.5, s1Saved.getTotalDays(), "Session 1 request must compute to 0.5 days");

        // 2. Apply Session 2 (Afternoon half day: 0.5 days) on the exact same date
        LeaveRequest s2Request = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(targetDate)
                .endDate(targetDate)
                .startSession("Session 2")
                .endSession("Session 2")
                .reason("Afternoon family event")
                .build();

        LeaveRequest s2Saved = leaveService.applyForLeave(s2Request);
        assertNotNull(s2Saved.getId());
        assertEquals("PENDING", s2Saved.getStatus());
        assertEquals(0.5, s2Saved.getTotalDays(), "Session 2 request must compute to 0.5 days");

        // 3. Attempting to apply a DUPLICATE Session 1 on the same date must fail
        LeaveRequest s1Duplicate = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(targetDate)
                .endDate(targetDate)
                .startSession("Session 1")
                .endSession("Session 1")
                .reason("Another morning leave")
                .build();

        IllegalStateException exDuplicate = assertThrows(IllegalStateException.class,
                () -> leaveService.applyForLeave(s1Duplicate),
                "Applying a duplicate Session 1 must be rejected by SessionOverlapRule");
        assertTrue(exDuplicate.getMessage().contains("Leave conflict") || exDuplicate.getMessage().contains("overlapping"),
                "Exception message must describe conflict: " + exDuplicate.getMessage());

        // 4. Attempting to apply a Full Day on that same date must also fail
        LeaveRequest fullDayRequest = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(targetDate)
                .endDate(targetDate)
                .startSession("Session 1")
                .endSession("Session 2")
                .reason("Full day leave conflict")
                .build();

        assertThrows(IllegalStateException.class,
                () -> leaveService.applyForLeave(fullDayRequest),
                "Applying a full-day leave over existing half-day leaves must be rejected");

        // 5. If Session 1 is cancelled, a new Session 1 must now be permitted
        leaveService.cancelLeaveRequest(s1Saved.getId(), testEmployee.getId());
        LeaveRequest s1Reapplied = leaveService.applyForLeave(s1Duplicate);
        assertNotNull(s1Reapplied.getId());
        assertEquals("PENDING", s1Reapplied.getStatus());
    }

    @Test
    @DisplayName("Scenario 5: HR applies RESTRICTED_HOLIDAY and COMP_OFF on behalf of employee — deducts correct quotas, not Casual Leave")
    void testApplyOnBehalfByHrRestrictedHolidayAndCompOff() {
        int year = LocalDate.now().getYear();
        LocalDate testDate = LocalDate.now().plusDays(20);

        // Seed active published restricted holiday on testDate for policy validation
        HolidayList hl = holidayListRepository.findAll().stream().findFirst().orElseGet(() ->
                holidayListRepository.save(HolidayList.builder().name("Test List").year(year).active(true).published(true).build()));
        hl.setPublished(true);
        hl.setActive(true);
        holidayListRepository.save(hl);

        holidayRepository.save(Holiday.builder()
                .holidayListId(hl.getId())
                .name("Festival Celebration")
                .date(testDate)
                .type("RESTRICTED")
                .active(true)
                .build());

        // Reset balances
        LeaveBalance balance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), year);
        balance.setCasualLeaveQuota(6.0);
        balance.setCasualLeaveUsed(0.0);
        balance.setRestrictedHolidayQuota(2.0);
        balance.setRestrictedHolidayUsed(0.0);
        leaveBalanceRepository.save(balance);

        // 1. HR logs a RESTRICTED_HOLIDAY on behalf of the employee
        LeaveRequest rhRequest = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("RESTRICTED_HOLIDAY")
                .startDate(testDate)
                .endDate(testDate)
                .reason("Festival celebration")
                .build();

        LeaveRequest rhSaved = leaveService.applyOnBehalfByHr(rhRequest);
        assertNotNull(rhSaved.getId());
        assertEquals("APPROVED", rhSaved.getStatus());
        assertEquals(1.0, rhSaved.getTotalDays());

        // Verify: Restricted Holiday quota deducted by 1.0; Casual Leave remains completely untouched (0.0 used)
        LeaveBalance balanceAfterRh = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), year);
        assertEquals(1.0, balanceAfterRh.getRestrictedHolidayUsed(), "Restricted holiday used must be incremented by 1.0");
        assertEquals(1.0, balanceAfterRh.getRestrictedHolidayRemaining(), "Restricted holiday remaining must be 1.0");
        assertEquals(0.0, balanceAfterRh.getCasualLeaveUsed(), "Casual leave used must strictly remain 0.0");
        assertEquals(6.0, balanceAfterRh.getCasualLeaveRemaining(), "Casual leave remaining must strictly remain 6.0");

        // 2. Set up dynamic EmployeeLeaveQuota for COMP_OFF (e.g. 2.0 days earned from weekend work)
        EmployeeLeaveQuota compOffQuota = employeeLeaveQuotaRepository
                .findByEmployeeIdAndYearAndLeaveType(testEmployee.getId(), year, "COMP_OFF")
                .orElseGet(() -> EmployeeLeaveQuota.builder()
                        .employeeId(testEmployee.getId())
                        .year(year)
                        .leaveType("COMP_OFF")
                        .quota(2.0)
                        .used(0.0)
                        .build());
        compOffQuota.setQuota(2.0);
        compOffQuota.setUsed(0.0);
        employeeLeaveQuotaRepository.save(compOffQuota);

        // 3. HR logs a COMP_OFF on behalf of the employee
        LocalDate compOffDate = LocalDate.now().plusDays(25);
        LeaveRequest compOffRequest = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("COMP_OFF")
                .startDate(compOffDate)
                .endDate(compOffDate)
                .reason("Compensatory off for Saturday deployment")
                .build();

        LeaveRequest compOffSaved = leaveService.applyOnBehalfByHr(compOffRequest);
        assertNotNull(compOffSaved.getId());
        assertEquals("APPROVED", compOffSaved.getStatus());
        assertEquals(1.0, compOffSaved.getTotalDays());

        // Verify: Dynamic EmployeeLeaveQuota used is 1.0, and Casual Leave is STILL untouched
        EmployeeLeaveQuota quotaAfter = employeeLeaveQuotaRepository
                .findByEmployeeIdAndYearAndLeaveType(testEmployee.getId(), year, "COMP_OFF")
                .orElseThrow();
        assertEquals(1.0, quotaAfter.getUsed(), "Comp-off dynamic quota used must be incremented to 1.0");
        assertEquals(1.0, quotaAfter.getRemaining(), "Comp-off dynamic quota remaining must be 1.0");

        LeaveBalance balanceAfterCompOff = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), year);
        assertEquals(0.0, balanceAfterCompOff.getCasualLeaveUsed(), "Casual leave used must still remain 0.0");
        assertEquals(6.0, balanceAfterCompOff.getCasualLeaveRemaining(), "Casual leave remaining must still remain 6.0");
    }
}
