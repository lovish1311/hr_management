package com.example.hr_management_backend;

import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LeavePolicyTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Test
    @DisplayName("SCENARIO 1: Employee applies for leaves and Deduct-on-Submit balance validation is enforced")
    public void testEmployeeDeductOnSubmit() {
        Long empId = 2L; // Lovish Kumar
        int year = 2026;

        // 1. Fetch initial balance
        LeaveBalance initialBal = leaveService.getOrCreateLeaveBalance(empId, year);
        double initialCasualRemaining = initialBal.getCasualLeaveRemaining();

        // 2. Employee applies for 2 days of Casual Leave
        LeaveRequest casualReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 2))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Test Casual Leave")
                .build();

        LeaveRequest submittedCasual = leaveService.applyForLeave(casualReq);
        assertEquals("PENDING", submittedCasual.getStatus(), "Leave request status MUST be PENDING upon submission");
        assertEquals(2.0, submittedCasual.getTotalDays(), "Total days must be calculated as 2.0 days");

        // 3. Verify Deduct-on-Submit: sum of pending leaves is subtracted from quota when attempting over-application
        // If remaining is initialCasualRemaining, and pending is 2.0 days, applying for 99 days MUST fail
        LeaveRequest excessiveReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 30))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Excessive Request")
                .build();

        assertThrows(IllegalStateException.class, () -> leaveService.applyForLeave(excessiveReq),
                "Should throw IllegalStateException because pending leaves deduct from remaining available balance");
    }

    @Test
    @DisplayName("SCENARIO 2: Admin applies leave on behalf of employee (Direct Approval & Used Update)")
    public void testAdminApplyOnBehalf() {
        Long empId = 2L;
        int year = 2026;

        LeaveBalance initialBal = leaveService.getOrCreateLeaveBalance(empId, year);
        double initialCasualUsed = initialBal.getCasualLeaveUsed();

        LeaveRequest req = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 1))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Applied by HR Admin")
                .build();

        LeaveRequest approvedReq = leaveService.applyOnBehalfByHr(req);
        assertEquals("APPROVED", approvedReq.getStatus(), "HR apply-on-behalf MUST directly mark status as APPROVED");

        LeaveBalance updatedBal = leaveService.getOrCreateLeaveBalance(empId, year);
        assertEquals(initialCasualUsed + 1.0, updatedBal.getCasualLeaveUsed(),
                "Casual leave used MUST immediately increment by 1.0 day upon HR approval");
    }

    @Test
    @DisplayName("SCENARIO 3 & 4: Admin approves/rejects leaves and withdraws approved leave")
    public void testApprovalRejectionAndWithdrawal() {
        Long empId = 2L;
        Long approverId = 101L;

        // Apply for Sick Leave (1 day)
        LeaveRequest sickReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("SICK")
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 10, 15))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Sick leave")
                .build();
        LeaveRequest submittedSick = leaveService.applyForLeave(sickReq);

        // Approve Sick Leave
        LeaveRequest approvedSick = leaveService.updateStatus(submittedSick.getId(), "APPROVED", null, approverId);
        assertEquals("APPROVED", approvedSick.getStatus());

        // Apply for Earned Leave (3 days)
        LeaveRequest earnedReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("EARNED")
                .startDate(LocalDate.of(2026, 10, 20))
                .endDate(LocalDate.of(2026, 10, 22))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Earned leave")
                .build();
        LeaveRequest submittedEarned = leaveService.applyForLeave(earnedReq);

        // Reject Earned Leave
        LeaveRequest rejectedEarned = leaveService.updateStatus(submittedEarned.getId(), "REJECTED", "Conflict", approverId);
        assertEquals("REJECTED", rejectedEarned.getStatus(), "Status MUST transition to REJECTED");

        // Withdraw Approved Sick Leave
        LeaveRequest withdrawnSick = leaveService.withdrawApprovedLeave(approvedSick.getId(), approverId);
        assertEquals("WITHDRAWN", withdrawnSick.getStatus(), "Status MUST transition to WITHDRAWN and credit back balance");
    }
}
