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

    @Autowired
    private com.example.hr_management_backend.features.attendance.service.AttendanceService attendanceService;

    @Autowired
    private com.example.hr_management_backend.features.attendance.repository.AttendanceRepository attendanceRepository;

    @Autowired
    private com.example.hr_management_backend.features.employees.repository.EmployeeRepository employeeRepository;

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

    @Test
    @DisplayName("SCENARIO 5: Rejecting pending, approved leaves and short leaves reverts leave balance to positive")
    public void testRejectionRevertsLeaveBalanceAndShortLeave() {
        Long empId = 2L;
        Long approverId = 101L;
        int year = 2026;

        // 1. Initial balance check
        LeaveBalance initialBal = leaveService.getOrCreateLeaveBalance(empId, year);
        double initialCasualRemaining = initialBal.getCasualLeaveRemaining();

        // 2. Submit a pending casual leave (2 days)
        LeaveRequest casualReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 9, 10))
                .endDate(LocalDate.of(2026, 9, 11))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Casual Leave Rejection Test")
                .build();
        LeaveRequest submittedCasual = leaveService.applyForLeave(casualReq);

        // Effective remaining balance should decrease by 2
        LeaveBalance pendingBal = leaveService.getOrCreateLeaveBalance(empId, year);
        assertEquals(initialCasualRemaining - 2.0, pendingBal.getCasualLeaveRemaining(),
                "Effective remaining balance MUST decrease by 2.0 while request is PENDING");

        // 3. REJECT the pending casual leave request
        LeaveRequest rejectedCasual = leaveService.updateStatus(submittedCasual.getId(), "REJECTED", "Rejected by manager", approverId);
        assertEquals("REJECTED", rejectedCasual.getStatus());

        // Effective remaining balance MUST revert back to initial casual remaining!
        LeaveBalance revertedBal = leaveService.getOrCreateLeaveBalance(empId, year);
        assertEquals(initialCasualRemaining, revertedBal.getCasualLeaveRemaining(),
                "Rejecting pending leave MUST revert the casual leave balance back to positive / original value");

        // 4. Test rejecting an APPROVED leave
        LeaveRequest approvedReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2026, 9, 15))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Approved then Rejected Test")
                .build();
        LeaveRequest appSubmitted = leaveService.applyForLeave(approvedReq);
        leaveService.updateStatus(appSubmitted.getId(), "APPROVED", null, approverId);

        // Now reject the approved leave
        leaveService.updateStatus(appSubmitted.getId(), "REJECTED", "Cancelled after approval", approverId);

        LeaveBalance postApprovedRejectionBal = leaveService.getOrCreateLeaveBalance(empId, year);
        assertEquals(initialCasualRemaining, postApprovedRejectionBal.getCasualLeaveRemaining(),
                "Rejecting an approved leave MUST credit back the used balance and restore available balance");

        // 5. Test Short Leave (Short Break) Rejection
        LeaveRequest shortLeaveReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("SHORT_BREAK")
                .startDate(LocalDate.of(2026, 9, 20))
                .endDate(LocalDate.of(2026, 9, 20))
                .reason("Short break request")
                .build();
        LeaveRequest submittedShort = leaveService.applyForLeave(shortLeaveReq);
        assertEquals("PENDING", submittedShort.getStatus());

        // Reject short leave
        LeaveRequest rejectedShort = leaveService.updateStatus(submittedShort.getId(), "REJECTED", "Too busy today", approverId);
        assertEquals("REJECTED", rejectedShort.getStatus());
    }

    @Test
    @DisplayName("SCENARIO 6: Verify isTimeBased field classification and exact duration-based hourly accumulation")
    public void testIsTimeBasedFieldAndHourlyDuration() {
        Long empId = 3L;

        // 1. Submit Short Break with start and end times (e.g. 10:00 to 11:30 = 1.5 hours)
        LeaveRequest shortBreak = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("SHORT_BREAK")
                .startDate(LocalDate.of(2026, 9, 25))
                .endDate(LocalDate.of(2026, 9, 25))
                .startTime(java.time.LocalTime.of(10, 0))
                .endTime(java.time.LocalTime.of(11, 30))
                .reason("Doctor Appointment 1.5 hrs")
                .build();

        LeaveRequest submitted = leaveService.applyForLeave(shortBreak);
        assertTrue(submitted.getIsTimeBased(), "isTimeBased field MUST be automatically set to true for SHORT_BREAK");

        // 2. Submit Early Out (e.g. 16:00 to 18:00 = 2.0 hours)
        LeaveRequest earlyOut = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("EARLY_OUT")
                .startDate(LocalDate.of(2026, 9, 26))
                .endDate(LocalDate.of(2026, 9, 26))
                .startTime(java.time.LocalTime.of(16, 0))
                .endTime(java.time.LocalTime.of(18, 0))
                .reason("Personal Early Out 2.0 hrs")
                .build();

        LeaveRequest submittedEarly = leaveService.applyForLeave(earlyOut);
        assertTrue(submittedEarly.getIsTimeBased(), "isTimeBased field MUST be automatically set to true for EARLY_OUT");

        // 3. Regular Leave (Casual) must have isTimeBased = false
        LeaveRequest casual = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 9, 27))
                .endDate(LocalDate.of(2026, 9, 27))
                .reason("Casual Leave")
                .build();

        LeaveRequest submittedCasual = leaveService.applyForLeave(casual);
        assertFalse(submittedCasual.getIsTimeBased(), "isTimeBased field MUST be false for regular Casual leave");
    }

    @Test
    @DisplayName("SCENARIO 7: Attendance status auto-sync and re-evaluation upon permission approval")
    public void testAttendanceAutoSyncOnPermissionApproval() {
        LocalDate testDate = LocalDate.of(2026, 9, 4);

        // 0. Ensure employee entity exists for relational lookup in AttendanceService
        com.example.hr_management_backend.features.employees.model.Employee emp =
                employeeRepository.findByEmail("lovish.test@example.com").orElseGet(() -> employeeRepository.save(
                        com.example.hr_management_backend.features.employees.model.Employee.builder()
                                .firstName("Lovish")
                                .lastName("Kumar")
                                .email("lovish.test@example.com")
                                .employeeCode("EMP002")
                                .role("EMPLOYEE")
                                .build()
                ));
        Long targetEmpId = emp.getId();
        leaveService.getOrCreateLeaveBalance(targetEmpId, 2026);

        // 1. Create an attendance record marked LATE due to 09:45 AM check-in
        com.example.hr_management_backend.features.attendance.model.Attendance att =
                new com.example.hr_management_backend.features.attendance.model.Attendance(
                        null, targetEmpId, testDate,
                        java.time.LocalTime.of(9, 45),
                        java.time.LocalTime.of(18, 0),
                        "LATE"
                );
        attendanceRepository.save(att);

        // Verify initial status is LATE
        com.example.hr_management_backend.features.attendance.model.Attendance initialAtt =
                attendanceRepository.findByEmployeeIdAndDate(targetEmpId, testDate).orElseThrow();
        assertEquals("LATE", initialAtt.getStatus(), "Initial attendance status MUST be LATE");

        // 2. Submit Late Arrival permission request approved until 10:30 AM
        LeaveRequest lateReq = LeaveRequest.builder()
                .employeeId(targetEmpId)
                .leaveType("LATE_ARRIVAL")
                .startDate(testDate)
                .endDate(testDate)
                .startTime(java.time.LocalTime.of(9, 0))
                .endTime(java.time.LocalTime.of(10, 30))
                .reason("Late due to traffic")
                .build();
        LeaveRequest submittedLate = leaveService.applyForLeave(lateReq);

        // Approve Late Arrival permission
        leaveService.updateStatus(submittedLate.getId(), "APPROVED", null, 101L);

        // Trigger sync manually for test assertion
        attendanceService.syncLeaveToAttendance(targetEmpId, testDate, testDate, "LATE_ARRIVAL");

        // Verify attendance status is retroactively updated from LATE to PRESENT!
        com.example.hr_management_backend.features.attendance.model.Attendance updatedAtt =
                attendanceRepository.findByEmployeeIdAndDate(targetEmpId, testDate).orElseThrow();
        assertEquals("PRESENT", updatedAtt.getStatus(),
                "Attendance status MUST be retroactively updated from LATE to PRESENT upon Late Arrival permission approval");
    }
}
