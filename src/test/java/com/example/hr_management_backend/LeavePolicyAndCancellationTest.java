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

        // Initialize fresh balance for testing
        LeaveBalance balance = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        balance.setCasualLeaveUsed(0.0);
        balance.setSickLeaveUsed(0.0);
        balance.setEarnedLeaveUsed(0.0);
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
        assertEquals(12.0, balance.getCasualLeaveRemaining());
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
        LeaveRequest approved = leaveService.updateStatus(saved.getId(), "APPROVED", null, 1L);
        assertEquals("APPROVED", approved.getStatus());

        LeaveBalance balanceAfterApprove = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(2.0, balanceAfterApprove.getSickLeaveUsed());
        assertEquals(8.0, balanceAfterApprove.getSickLeaveRemaining());

        // Cancel approved leave
        LeaveRequest cancelled = leaveService.cancelLeaveRequest(saved.getId(), testEmployee.getId());
        assertEquals("CANCELLED", cancelled.getStatus());

        LeaveBalance balanceAfterCancel = leaveService.getOrCreateLeaveBalance(testEmployee.getId(), LocalDate.now().getYear());
        assertEquals(0.0, balanceAfterCancel.getSickLeaveUsed());
        assertEquals(10.0, balanceAfterCancel.getSickLeaveRemaining());
    }

    @Test
    @DisplayName("Scenario 3: Short leave quota enforcement — 3rd short leave in a month flags quota penalty")
    void testShortLeaveMonthlyQuotaPenalty() {
        LocalDate currentMonth = LocalDate.now();

        // Short Leave 1
        LeaveRequest req1 = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("SHORT_BREAK")
                .startDate(currentMonth.withDayOfMonth(2))
                .endDate(currentMonth.withDayOfMonth(2))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .reason("Bank work")
                .build();
        leaveService.applyForLeave(req1);

        // Short Leave 2
        LeaveRequest req2 = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("EARLY_OUT")
                .startDate(currentMonth.withDayOfMonth(5))
                .endDate(currentMonth.withDayOfMonth(5))
                .startTime(LocalTime.of(16, 0))
                .endTime(LocalTime.of(17, 0))
                .reason("Doctor visit")
                .build();
        leaveService.applyForLeave(req2);

        // Short Leave 3 (Quota Exceeded!)
        LeaveRequest req3 = LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("SHORT_LEAVE")
                .startDate(currentMonth.withDayOfMonth(10))
                .endDate(currentMonth.withDayOfMonth(10))
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(12, 0))
                .reason("Courier receipt")
                .build();
        LeaveRequest savedReq3 = leaveService.applyForLeave(req3);

        assertTrue(savedReq3.getReason().contains("Quota Exceeded"));
    }
}
