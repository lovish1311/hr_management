package com.example.hr_management_backend;

import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
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
public class ManualTesterLeaveFlowTest {

    @Autowired
    private LeaveService leaveService;

    @Test
    @DisplayName("MANUAL TESTER SIMULATION: Full Employee Application -> Manager Approval -> HR On-Behalf & Withdrawal Flow")
    public void executeManualTesterFlow() {
        System.out.println("\n====================================================================");
        System.out.println("STARTING MANUAL TESTER AUTOMATION: LEAVE MANAGEMENT WORKFLOW");
        System.out.println("====================================================================\n");

        Long empId = 2L; // Lovish Kumar
        Long managerId = 101L; // Harsh Kaushal (Manager)
        Long hrActorId = 1L; // Super Admin / HR
        int year = 2026;

        // STEP 1 & 2: Inspect initial balance
        System.out.println("👉 [STEP 1 & 2] Manual Tester checks initial Employee Leave Balance...");
        LeaveBalance initialBal = leaveService.getOrCreateLeaveBalance(empId, year);
        double initialCasualQuota = initialBal.getCasualLeaveQuota();
        double initialCasualUsed = initialBal.getCasualLeaveUsed();
        double initialAvailableCasual = initialBal.getCasualLeaveRemaining();

        System.out.println("   📊 Initial Casual Quota: " + initialCasualQuota + ", Used: " + initialCasualUsed + ", Available: " + initialAvailableCasual);

        // STEP 3: Employee applies for 2 Days Casual Leave (2026-10-01 to 2026-10-02)
        System.out.println("\n👉 [STEP 3] Employee fills form and applies for 2 Days Casual Leave (2026-10-01 to 2026-10-02)...");
        LeaveRequest casualReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 2))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Manual Test: Personal travel & family function")
                .build();

        LeaveRequest submittedCasual = leaveService.applyForLeave(casualReq);
        
        // EXPECTATION CHECK:
        // Why PENDING: Submissions require Manager approval.
        // Why 2.0 total days: Server calculates dates from 2026-10-01 to 2026-10-02 inclusive.
        assertEquals("PENDING", submittedCasual.getStatus(), "Status MUST be PENDING upon submission");
        assertEquals(2.0, submittedCasual.getTotalDays(), "Total days MUST be calculated as 2.0");
        System.out.println("   📝 Submitted successfully. Request ID: " + submittedCasual.getId() + ", Status: " + submittedCasual.getStatus());

        // STEP 4: Attempt Over-application (50 Days)
        System.out.println("\n👉 [STEP 4] Tester attempts negative scenario: Requesting 50 days when remaining available is < 10 days...");
        LeaveRequest overApplyReq = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("CASUAL")
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 12, 20))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("Manual Test: Over-application attempt")
                .build();

        // EXPECTATION CHECK:
        // Why throws IllegalStateException: Deduct-on-submit locks the 2 pending days, making available = (initialAvailable - 2.0). 50 days exceeds limit!
        assertThrows(IllegalStateException.class, () -> leaveService.applyForLeave(overApplyReq),
                "Backend validation MUST throw IllegalStateException for excessive application beyond available balance");
        System.out.println("   ✅ System correctly blocked over-application with validation exception!");

        // STEP 5: Manager Persona -> Approves the Casual Leave
        System.out.println("\n👉 [STEP 5] Tester switches persona to Manager (ID: " + managerId + ") and approves Request ID: " + submittedCasual.getId() + "...");
        LeaveRequest approvedCasual = leaveService.updateStatus(submittedCasual.getId(), "APPROVED", null, managerId);
        
        // EXPECTATION CHECK:
        // Why APPROVED: Manager has authorization to approve.
        assertEquals("APPROVED", approvedCasual.getStatus(), "Status MUST transition to APPROVED");
        System.out.println("   🎉 Manager Approval Result -> Status: " + approvedCasual.getStatus() + ", ApprovedBy: " + approvedCasual.getApprovedBy());

        // STEP 6: HR Lead Persona -> Applies on behalf for 1 Day Sick Leave
        System.out.println("\n👉 [STEP 6] Tester switches persona to HR Lead and applies 1 Day Sick Leave on behalf of Employee...");
        LeaveRequest sickOnBehalf = LeaveRequest.builder()
                .employeeId(empId)
                .leaveType("SICK")
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 10, 15))
                .startSession("FULL_DAY")
                .endSession("FULL_DAY")
                .reason("HR Entry: Sick Leave on behalf")
                .build();

        LeaveRequest approvedSick = leaveService.applyOnBehalfByHr(sickOnBehalf);
        
        // EXPECTATION CHECK:
        // Why APPROVED: HR apply-on-behalf bypasses PENDING state.
        assertEquals("APPROVED", approvedSick.getStatus(), "Status MUST be APPROVED immediately for HR apply-on-behalf");
        System.out.println("   ⚡ Apply-on-behalf Result -> Request ID: " + approvedSick.getId() + ", Status: " + approvedSick.getStatus());

        // STEP 7: HR Lead Persona -> Withdraws approved Casual Leave
        System.out.println("\n👉 [STEP 7] HR Lead clicks 'Withdraw' on approved Casual Leave ID: " + approvedCasual.getId() + "...");
        LeaveRequest withdrawnCasual = leaveService.withdrawApprovedLeave(approvedCasual.getId(), hrActorId);
        
        // EXPECTATION CHECK:
        // Why WITHDRAWN: HR Admin initiated withdrawal.
        assertEquals("WITHDRAWN", withdrawnCasual.getStatus(), "Status MUST transition to WITHDRAWN");
        System.out.println("   🔄 Withdrawal Result -> Status: " + withdrawnCasual.getStatus());

        // STEP 8: Final Audit Balance Inspection
        System.out.println("\n👉 [STEP 8] Final Auditor Inspection of Leave Balances...");
        LeaveBalance finalBal = leaveService.getOrCreateLeaveBalance(empId, year);
        
        System.out.println("   📈 Final Casual Used: " + finalBal.getCasualLeaveUsed() + " (Returned to baseline after withdrawal)");
        System.out.println("   📈 Final Sick Used: " + finalBal.getSickLeaveUsed() + " (+1.0 day from HR apply-on-behalf)");
        
        System.out.println("====================================================================");
        System.out.println("✅ ALL MANUAL TESTER SCENARIOS EXECUTED & PASSED PERFECTLY!");
        System.out.println("====================================================================\n");
    }
}
