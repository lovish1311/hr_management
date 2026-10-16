package com.example.hr_management_backend;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
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
    private EmployeeRepository employeeRepository;

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
    @DisplayName("Scenario 2: Employee applies, Manager approves, then Employee cancels — used balance is refunded")
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

        // Cancel approved leave
        LeaveRequest cancelled = leaveService.cancelLeaveRequest(saved.getId(), testEmployee.getId());
        assertEquals("CANCELLED", cancelled.getStatus());

        LeaveBalance balanceAfterCancel = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(0.0, balanceAfterCancel.getSickLeaveUsed());
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
}
