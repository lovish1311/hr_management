package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveAccrualService;
import com.example.hr_management_backend.features.leaves.service.LeaveCarryoverService;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LeaveCarryoverAndAccrualTest {

    @Autowired
    private LeaveAccrualService leaveAccrualService;

    @Autowired
    private LeaveCarryoverService leaveCarryoverService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();

        testEmployee = employeeRepository.findByEmail("carryover_test@company.com")
                .orElseGet(() -> employeeRepository.save(Employee.builder()
                        .email("carryover_test@company.com")
                        .firstName("Carryover")
                        .lastName("Tester")
                        .employeeCode("CARRY_TEST_01")
                        .role("EMPLOYEE")
                        .status("ACTIVE")
                        .joiningDate(LocalDate.of(2025, 1, 1))
                        .build()));
    }

    @Test
    @DisplayName("Accrual: July 1st joiner works 6 months and gets 50% pro-rated quota rounded to 0.5")
    void testAccrualJulyFirstJoiner() {
        LocalDate joiningDate = LocalDate.of(2026, 7, 1);
        int months = leaveAccrualService.calculateAccrualMonthsWorked(joiningDate, 2026);
        assertEquals(6, months, "July 1st joiner must have 6 working months in 2026");

        double prorated = leaveAccrualService.calculateProratedQuota(6.0, months);
        assertEquals(3.0, prorated, "6 months of 6.0 annual quota must equal 3.0 days");
    }

    @Test
    @DisplayName("Accrual: 15th-Day Cutoff Rule - July 15th gets 6 months, July 16th gets 5 months")
    void testAccrual15thDayCutoffRule() {
        LocalDate joiningJuly15 = LocalDate.of(2026, 7, 15);
        LocalDate joiningJuly16 = LocalDate.of(2026, 7, 16);

        int monthsJuly15 = leaveAccrualService.calculateAccrualMonthsWorked(joiningJuly15, 2026);
        int monthsJuly16 = leaveAccrualService.calculateAccrualMonthsWorked(joiningJuly16, 2026);

        assertEquals(6, monthsJuly15, "July 15th joiner qualifies for July (6 months)");
        assertEquals(5, monthsJuly16, "July 16th joiner does not qualify for July (5 months)");

        double quotaJuly16 = leaveAccrualService.calculateProratedQuota(6.0, monthsJuly16);
        // (6.0 / 12) * 5 = 2.5
        assertEquals(2.5, quotaJuly16, "5 months worked should yield 2.5 days");
    }

    @Test
    @DisplayName("Accrual: Rounding to nearest 0.5 (e.g., 2.3 days -> 2.5 days)")
    void testAccrualRoundingToNearestHalfDay() {
        // Test custom raw quota formula: 2.3 rounds to 2.5
        double rounded = Math.round(2.3 * 2.0) / 2.0;
        assertEquals(2.5, rounded);

        double rounded2 = Math.round(2.1 * 2.0) / 2.0;
        assertEquals(2.0, rounded2);

        double rounded3 = Math.round(2.8 * 2.0) / 2.0;
        assertEquals(3.0, rounded3);
    }

    @Test
    @DisplayName("Accrual: Prior year joiners get full quota and isProrated is false")
    void testAccrualPriorYearJoinerGetsFullQuota() {
        LocalDate priorJoinDate = LocalDate.of(2024, 5, 10);
        int months = leaveAccrualService.calculateAccrualMonthsWorked(priorJoinDate, 2026);
        assertEquals(12, months, "Prior year joiners must have 12 working months");

        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(2026)
                .build();

        Employee emp = Employee.builder()
                .joiningDate(priorJoinDate)
                .build();

        leaveAccrualService.applyAccrualToBalance(balance, emp, 2026, 2.0);

        assertEquals(6.0, balance.getCasualLeaveQuota());
        assertEquals(6.0, balance.getSickLeaveQuota());
        assertEquals(6.0, balance.getEarnedLeaveQuota());
        assertEquals(2.0, balance.getRestrictedHolidayQuota());
        assertFalse(balance.getIsProrated());
        assertEquals(12, balance.getAccrualMonthsWorked());
    }

    @Test
    @DisplayName("Carryover: Casual & Sick expire, Earned leaves carry over up to 15 days max")
    void testYearEndCarryoverProcessing() {
        int currentYear = LocalDate.now().getYear();
        int fromYear = currentYear;
        int toYear = currentYear + 1;

        // Create balance for current year with 20 unused Earned Leaves, 4 unused Casual, 5 unused Sick
        LeaveBalance prevBalance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(fromYear)
                .casualLeaveQuota(6.0)
                .casualLeaveUsed(2.0) // 4 remaining (should expire)
                .sickLeaveQuota(6.0)
                .sickLeaveUsed(1.0)   // 5 remaining (should expire)
                .earnedLeaveQuota(25.0)
                .earnedLeaveUsed(5.0) // 20 remaining (should cap at 15)
                .build();
        leaveBalanceRepository.save(prevBalance);

        // Run year-end carryover to upcoming year
        var result = leaveCarryoverService.processYearEndCarryover(fromYear, toYear);
        assertEquals("SUCCESS", result.get("status"));

        LeaveBalance newBalance = leaveBalanceRepository.findByEmployeeIdAndYear(testEmployee.getId(), toYear).orElseThrow();

        // Casual and Sick reset to fresh quotas
        assertEquals(6.0, newBalance.getCasualLeaveQuota());
        assertEquals(0.0, newBalance.getCasualLeaveUsed());
        assertEquals(6.0, newBalance.getSickLeaveQuota());
        assertEquals(0.0, newBalance.getSickLeaveUsed());

        // Earned leave carry forward is capped at 15.0 days
        assertEquals(15.0, newBalance.getCarriedForwardLeaveQuota());
        assertEquals(0.0, newBalance.getCarriedForwardLeaveUsed());
        assertEquals(LocalDate.of(toYear, 3, 31), newBalance.getCarriedForwardExpiryDate());
        assertFalse(newBalance.getCarriedForwardExpired());

        // Display check: Current Year: 6.0 days | Carried Forward: 15.0 days | Total: 21.0 days
        assertEquals(15.0, newBalance.getCarriedForwardLeaveRemaining());
        assertEquals(21.0, newBalance.getEarnedLeaveRemaining());
        assertTrue(newBalance.getEarnedLeaveBalanceDisplay().contains("Current Year: 6 days"));
        assertTrue(newBalance.getEarnedLeaveBalanceDisplay().contains("Carried Forward: 15 days"));
        assertTrue(newBalance.getEarnedLeaveBalanceDisplay().contains("Total: 21 days"));
    }

    @Test
    @DisplayName("Deduction Priority: Carried-forward balance is deducted FIRST, then current year quota")
    void testDeductionPrioritizesCarriedForwardFirst() {
        int year = LocalDate.now().getYear();
        LocalDate activeExpiry = LocalDate.now().plusMonths(2); // In active unexpired window

        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(0.0)
                .carriedForwardExpiryDate(activeExpiry)
                .carriedForwardExpired(false)
                .build();
        leaveBalanceRepository.save(balance);

        // Deduct 4 days of Earned Leave: should come entirely from carried forward (10 - 4 = 6)
        leaveService.deductEarnedLeaveWithPriority(balance, 4.0);
        assertEquals(4.0, balance.getCarriedForwardLeaveUsed());
        assertEquals(0.0, balance.getEarnedLeaveUsed());
        assertEquals(6.0, balance.getCarriedForwardLeaveRemaining());
        assertEquals(6.0, balance.getCurrentYearEarnedLeaveRemaining());
        assertEquals(12.0, balance.getEarnedLeaveRemaining());

        // Deduct another 8 days: 6 remaining from carried forward + 2 from current year
        leaveService.deductEarnedLeaveWithPriority(balance, 8.0);
        assertEquals(10.0, balance.getCarriedForwardLeaveUsed(), "Carried forward quota is completely exhausted (10.0 used)");
        assertEquals(0.0, balance.getCarriedForwardLeaveRemaining());
        assertEquals(2.0, balance.getEarnedLeaveUsed(), "2 days drawn from current year quota");
        assertEquals(4.0, balance.getCurrentYearEarnedLeaveRemaining());
        assertEquals(4.0, balance.getEarnedLeaveRemaining());
    }

    @Test
    @DisplayName("Refund Priority: Cancellation credits back carried-forward balance first")
    void testRefundPrioritizesCarriedForwardFirst() {
        int year = LocalDate.now().getYear();
        LocalDate activeExpiry = LocalDate.now().plusMonths(2); // In active unexpired window

        // Start with 10 carried used, 2 current used
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(2.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(10.0)
                .carriedForwardExpiryDate(activeExpiry)
                .carriedForwardExpired(false)
                .build();
        leaveBalanceRepository.save(balance);

        // Refund 5 days: should restore 5 days back to carried forward used (10 -> 5 used)
        leaveService.refundEarnedLeaveWithPriority(balance, 5.0);
        assertEquals(5.0, balance.getCarriedForwardLeaveUsed());
        assertEquals(2.0, balance.getEarnedLeaveUsed());
        assertEquals(5.0, balance.getCarriedForwardLeaveRemaining());

        // Refund remaining 7 days: restores all 5 carried forward + 2 to current year
        leaveService.refundEarnedLeaveWithPriority(balance, 7.0);
        assertEquals(0.0, balance.getCarriedForwardLeaveUsed());
        assertEquals(0.0, balance.getEarnedLeaveUsed());
        assertEquals(10.0, balance.getCarriedForwardLeaveRemaining());
        assertEquals(6.0, balance.getCurrentYearEarnedLeaveRemaining());
        assertEquals(16.0, balance.getEarnedLeaveRemaining());
    }

    @Test
    @DisplayName("Expiry: After March 31st, carried forward balance expires and cannot be availed")
    void testCarriedForwardLeavesExpireAfterMarch31() {
        int year = LocalDate.now().getYear();

        // Balance with expiry date in the past
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(2.0)
                .carriedForwardExpiryDate(LocalDate.now().minusDays(1)) // Expired yesterday
                .carriedForwardExpired(false)
                .build();
        leaveBalanceRepository.save(balance);

        // getCarriedForwardLeaveRemaining() automatically evaluates expiry
        assertEquals(0.0, balance.getCarriedForwardLeaveRemaining(), "Expired carried forward balance must return 0.0 remaining");
        assertEquals(6.0, balance.getEarnedLeaveRemaining(), "Total available must only equal current year remaining (6.0)");

        // Running processCarriedForwardExpiry sets carriedForwardExpired to true in database
        leaveCarryoverService.processCarriedForwardExpiry(year);
        LeaveBalance reloaded = leaveBalanceRepository.findById(balance.getId()).orElseThrow();
        assertTrue(reloaded.getCarriedForwardExpired());
    }

    @Test
    @DisplayName("Bug Fix 1: Pending leaves reserve carried-forward quota first, avoiding over-allocation")
    void testPendingEarnedLeavesDeductionPriorityAgainstCarriedForward() {
        int year = LocalDate.now().getYear();
        LocalDate activeExpiry = LocalDate.now().plusMonths(2);

        // Employee has 10 CF, 6 CY = 16 total.
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(0.0)
                .carriedForwardExpiryDate(activeExpiry)
                .carriedForwardExpired(false)
                .earnedLeavePending(8.0) // 8 days pending
                .build();

        // 8 days pending must reserve 8 days from 10 CF -> 2 CF remaining.
        // Current year quota remains untouched at 6 CY remaining.
        // Total available remaining must be 8.0 (16 - 8 = 8.0), NOT 10.0!
        assertEquals(2.0, balance.getCarriedForwardLeaveRemaining(), "8 pending days reserve against 10 carried -> 2 remaining");
        assertEquals(6.0, balance.getCurrentYearEarnedLeaveRemaining(), "Current year remains fully unreserved at 6.0");
        assertEquals(8.0, balance.getEarnedLeaveRemaining(), "Total available must be exactly 8.0 days");
        assertEquals("Current Year: 6 days | Carried Forward: 2 days | Total: 8 days", balance.getEarnedLeaveBalanceDisplay());
    }

    @Test
    @DisplayName("Bug Fix 1b: Pending leaves overflowing carried-forward quota deduct from current year")
    void testPendingEarnedLeavesOverflowIntoCurrentYear() {
        int year = LocalDate.now().getYear();
        LocalDate activeExpiry = LocalDate.now().plusMonths(2);

        // Employee has 10 CF, 6 CY = 16 total.
        // Applies for 12 days pending (10 from CF, 2 from CY).
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(0.0)
                .carriedForwardExpiryDate(activeExpiry)
                .carriedForwardExpired(false)
                .earnedLeavePending(12.0)
                .build();

        assertEquals(0.0, balance.getCarriedForwardLeaveRemaining(), "Carried forward is fully reserved by pending (0.0 remaining)");
        assertEquals(4.0, balance.getCurrentYearEarnedLeaveRemaining(), "Current year absorbs 2 days overflow -> 4.0 remaining");
        assertEquals(4.0, balance.getEarnedLeaveRemaining(), "Total remaining is 4.0 days");
        assertEquals("Current Year: 4 days | Carried Forward: 0 days | Total: 4 days", balance.getEarnedLeaveBalanceDisplay());
    }

    @Test
    @DisplayName("Edge Case 2: Leave dates strictly after March 31st cannot consume carried-forward balance")
    void testLeaveAppliedForDatesAfterMarch31CannotUseCarriedForward() {
        int year = LocalDate.now().getYear();
        LocalDate expiryDate = LocalDate.of(year, 3, 31);

        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(0.0)
                .carriedForwardExpiryDate(expiryDate)
                .carriedForwardExpired(false)
                .build();
        leaveBalanceRepository.save(balance);

        // Leave dates in May (after March 31st)
        LocalDate mayStart = LocalDate.of(year, 5, 11);
        LocalDate mayEnd = LocalDate.of(year, 5, 13); // 3 days

        leaveService.deductEarnedLeaveWithPriority(balance, 3.0, mayStart, mayEnd);

        // Carried forward must NOT be touched because leave is in May!
        assertEquals(0.0, balance.getCarriedForwardLeaveUsed(), "Carried forward must not be used for May leave dates");
        assertEquals(3.0, balance.getEarnedLeaveUsed(), "3 days must be deducted strictly from current year used");
        assertEquals(3.0, balance.getCurrentYearEarnedLeaveRemaining());
    }

    @Test
    @DisplayName("Edge Case 2b: Leave after March 31st fails if current year quota is insufficient (even if CF is large)")
    void testLeaveAfterMarch31FailsIfCurrentYearInsufficient() {
        int year = LocalDate.now().getYear();
        LocalDate expiryDate = LocalDate.of(year, 3, 31);

        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(2.0) // Only 2 current year days
                .earnedLeaveUsed(0.0)
                .carriedForwardLeaveQuota(10.0) // 10 CF days
                .carriedForwardLeaveUsed(0.0)
                .carriedForwardExpiryDate(expiryDate)
                .carriedForwardExpired(false)
                .build();
        leaveBalanceRepository.save(balance);

        LocalDate mayStart = LocalDate.of(year, 5, 11);
        LocalDate mayEnd = LocalDate.of(year, 5, 14); // 4 days

        // Trying to deduct 4 days in May when CY only has 2 days must fail
        assertThrows(IllegalStateException.class, () ->
                leaveService.deductEarnedLeaveWithPriority(balance, 4.0, mayStart, mayEnd));
    }

    @Test
    @DisplayName("Refund LIFO: Expired carried-forward balance refunds to current year first to prevent loss")
    void testRefundExpiredCarriedForwardProtectsCurrentYear() {
        int year = LocalDate.now().getYear();

        // Expired carried forward balance with 10 CF used and 2 CY used
        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .earnedLeaveQuota(6.0)
                .earnedLeaveUsed(2.0)
                .carriedForwardLeaveQuota(10.0)
                .carriedForwardLeaveUsed(10.0)
                .carriedForwardExpiryDate(LocalDate.now().minusDays(1)) // Expired
                .carriedForwardExpired(true)
                .build();
        leaveBalanceRepository.save(balance);

        // Refund 2 days after expiry -> must restore current year used (2 -> 0), not expired CF!
        leaveService.refundEarnedLeaveWithPriority(balance, 2.0);
        assertEquals(0.0, balance.getEarnedLeaveUsed(), "Current year used must be refunded back to 0.0");
        assertEquals(10.0, balance.getCarriedForwardLeaveUsed(), "Expired carried forward used is untouched");
        assertEquals(6.0, balance.getCurrentYearEarnedLeaveRemaining());
    }

    @Test
    @DisplayName("Bug Fix 3: Cancellation of Restricted Holiday restores restricted holiday balance, not casual")
    void testCancellationOfRestrictedHolidayDoesNotDeductCasual() {
        int year = LocalDate.now().getYear();

        LeaveBalance balance = LeaveBalance.builder()
                .employeeId(testEmployee.getId())
                .year(year)
                .casualLeaveQuota(6.0)
                .casualLeaveUsed(1.0)
                .restrictedHolidayQuota(2.0)
                .restrictedHolidayUsed(1.0)
                .build();
        leaveBalanceRepository.save(balance);

        com.example.hr_management_backend.features.leaves.model.LeaveRequest req = com.example.hr_management_backend.features.leaves.model.LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("RESTRICTED_HOLIDAY")
                .startDate(LocalDate.of(year, 4, 14))
                .endDate(LocalDate.of(year, 4, 14))
                .totalDays(1.0)
                .status("APPROVED")
                .build();
        req = leaveRequestRepository.save(req);

        leaveService.cancelLeaveRequest(req.getId(), testEmployee.getId());

        LeaveBalance reloaded = leaveBalanceRepository.findByEmployeeIdAndYear(testEmployee.getId(), year).orElseThrow();
        assertEquals(0.0, reloaded.getRestrictedHolidayUsed(), "Restricted holiday used must be refunded to 0.0");
        assertEquals(1.0, reloaded.getCasualLeaveUsed(), "Casual leave used must remain unchanged at 1.0");
    }
}
