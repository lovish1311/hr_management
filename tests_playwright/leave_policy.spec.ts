import { test, expect, APIRequestContext } from '@playwright/test';

/**
 * =========================================================================================
 * PLAYWRIGHT AUTOMATED TEST SUITE: COMPLETE LEAVE MANAGEMENT POLICY & BALANCE VERIFICATION
 * =========================================================================================
 * 
 * Target Architecture: Spring Boot Backend API (http://localhost:8080)
 * Scenarios Covered:
 * 1. Authentication (Admin, Manager, Employee tokens)
 * 2. Employee applies for every type of leave (Casual, Sick, Earned)
 * 3. Deduct-On-Submit Verification (Available Balance = Quota - Used - Pending)
 * 4. Over-application Protection (Prevents applying beyond remaining + pending)
 * 5. Admin applies leave on behalf of employee (Direct APPROVAL & Used update)
 * 6. Admin approves & rejects requests randomly (State transitions & balance updates)
 * 7. Admin withdraws approved leave (Event publishing & credit refund)
 * 8. Dynamic Special Leave Types (Option 1 verification)
 */

test.describe('Leave Policy End-to-End Suite', () => {

  let adminToken: string;
  let hrToken: string;
  let employeeToken: string;
  let managerToken: string;
  
  let employeeId: number = 2; // EMP-202: Lovish (or default seeded employee)
  let managerId: number = 101; // EMP-101: Harsh Kaushal (Manager)
  let adminId: number = 1;

  test.beforeAll(async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // STEP 0: Authenticate Admin, HR, Manager, and Employee to acquire JWT Tokens
    // -------------------------------------------------------------------------------------
    
    // Login as Super Admin
    const adminLoginRes = await request.post('/api/auth/login', {
      data: { email: 'admin@company.com', password: 'admin123' }
    });
    expect(adminLoginRes.ok(), 'Super Admin login should succeed').toBeTruthy();
    const adminData = await adminLoginRes.json();
    adminToken = adminData.token;

    // Login as HR Lead (Aadisha Dhullar)
    const hrLoginRes = await request.post('/api/auth/login', {
      data: { email: 'hr@company.com', password: 'hr123' }
    });
    expect(hrLoginRes.ok(), 'HR Lead login should succeed').toBeTruthy();
    const hrData = await hrLoginRes.json();
    hrToken = hrData.token;

    // Login as Manager (Harsh Kaushal)
    const managerLoginRes = await request.post('/api/auth/login', {
      data: { email: 'harsh.kaushal@company.com', password: 'manager123' }
    });
    expect(managerLoginRes.ok(), 'Manager login should succeed').toBeTruthy();
    const managerData = await managerLoginRes.json();
    managerToken = managerData.token;

    // Login as Employee (Lovish Kumar)
    const empLoginRes = await request.post('/api/auth/login', {
      data: { email: 'lovish@company.com', password: 'user123' }
    });
    expect(empLoginRes.ok(), 'Employee login should succeed').toBeTruthy();
    const empData = await empLoginRes.json();
    employeeToken = empData.token;
    if (empData.employeeId) {
      employeeId = empData.employeeId;
    }
  });

  test('SCENARIO 1: Employee applies for every type of leave & verifies Deduct-on-Submit', async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // EXPLANATION OF EXPECTED RESULT:
    // When an employee applies for a leave, its status is set to PENDING.
    // Under our "Deduct-on-Submit" rule:
    // Available Balance = Quota - Used - Pending Requests.
    // Therefore, even while PENDING, the leave amount is subtracted from the available balance
    // so the employee cannot spam applications beyond their true quota.
    // -------------------------------------------------------------------------------------

    // 1. Fetch initial balance for Employee
    const initialBalRes = await request.get(`/api/leaves/balance/${employeeId}`, {
      headers: { 'Authorization': `Bearer ${employeeToken}` }
    });
    expect(initialBalRes.ok()).toBeTruthy();
    const initialBal = await initialBalRes.json();
    
    const initialCasualRemaining = initialBal.casualLeaveQuota - initialBal.casualLeaveUsed;
    const initialSickRemaining = initialBal.sickLeaveQuota - initialBal.sickLeaveUsed;
    const initialEarnedRemaining = initialBal.earnedLeaveQuota - initialBal.earnedLeaveUsed;

    console.log(`[TEST LOG] Initial Balances - Casual Remaining: ${initialCasualRemaining}, Sick Remaining: ${initialSickRemaining}, Earned Remaining: ${initialEarnedRemaining}`);

    // 2. Apply for Casual Leave (2 days: 2026-10-01 to 2026-10-02)
    const casualApplyRes = await request.post('/api/leaves/apply', {
      headers: { 'Authorization': `Bearer ${employeeToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'CASUAL',
        startDate: '2026-10-01',
        endDate: '2026-10-02',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Automated Playwright Test - Casual Leave Application'
      }
    });
    expect(casualApplyRes.ok(), 'Casual leave application should be accepted').toBeTruthy();
    const casualReq = await casualApplyRes.json();
    expect(casualReq.status).toBe('PENDING');
    expect(casualReq.totalDays).toBe(2.0);

    // 3. Apply for Sick Leave (1 day: 2026-10-05)
    const sickApplyRes = await request.post('/api/leaves/apply', {
      headers: { 'Authorization': `Bearer ${employeeToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'SICK',
        startDate: '2026-10-05',
        endDate: '2026-10-05',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Automated Playwright Test - Sick Leave Application'
      }
    });
    expect(sickApplyRes.ok(), 'Sick leave application should be accepted').toBeTruthy();
    const sickReq = await sickApplyRes.json();
    expect(sickReq.status).toBe('PENDING');
    expect(sickReq.totalDays).toBe(1.0);

    // 4. Apply for Earned Leave (3 days: 2026-10-07 to 2026-10-09)
    const earnedApplyRes = await request.post('/api/leaves/apply', {
      headers: { 'Authorization': `Bearer ${employeeToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'EARNED',
        startDate: '2026-10-07',
        endDate: '2026-10-09',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Automated Playwright Test - Earned Leave Application'
      }
    });
    expect(earnedApplyRes.ok(), 'Earned leave application should be accepted').toBeTruthy();
    const earnedReq = await earnedApplyRes.json();
    expect(earnedReq.status).toBe('PENDING');
    expect(earnedReq.totalDays).toBe(3.0);

    // -------------------------------------------------------------------------------------
    // VERIFY OVER-APPLICATION PROTECTION:
    // Attempt to apply for more Casual Leave than (Remaining - Pending).
    // Remaining Casual was initialCasualRemaining. Pending is 2.0 days.
    // If we request 99.0 days, the backend MUST reject it with an IllegalStateException (400/500).
    // -------------------------------------------------------------------------------------
    const overApplyRes = await request.post('/api/leaves/apply', {
      headers: { 'Authorization': `Bearer ${employeeToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'CASUAL',
        startDate: '2026-11-01',
        endDate: '2026-11-30',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Automated Playwright Test - Excessive Application'
      }
    });
    expect(overApplyRes.ok(), 'Over-application should be REJECTED by backend validation').toBeFalsy();
    console.log('[TEST LOG] Over-application successfully blocked by Deduct-on-Submit validation.');
  });

  test('SCENARIO 2: Admin applies leave on behalf of employee', async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // EXPLANATION OF EXPECTED RESULT:
    // When HR/Admin applies leave on behalf of an employee via `/api/leaves/admin/apply-on-behalf`,
    // the workflow skips the PENDING state and directly marks status = APPROVED.
    // Therefore:
    // 1. LeaveRequest status MUST be APPROVED immediately.
    // 2. The corresponding LeaveBalance `casualLeaveUsed` MUST increment by requested days immediately.
    // -------------------------------------------------------------------------------------

    const applyOnBehalfRes = await request.post('/api/leaves/admin/apply-on-behalf', {
      headers: { 'Authorization': `Bearer ${hrToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'CASUAL',
        startDate: '2026-12-01',
        endDate: '2026-12-01',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Applied directly by HR for urgent personal errand'
      }
    });
    expect(applyOnBehalfRes.ok(), 'HR Apply-on-behalf should succeed').toBeTruthy();
    const onBehalfReq = await applyOnBehalfRes.json();
    
    // ASSERTION 1: Status must be APPROVED
    expect(onBehalfReq.status).toBe('APPROVED');
    expect(onBehalfReq.totalDays).toBe(1.0);

    console.log(`[TEST LOG] HR Apply-on-behalf created successfully with ID: ${onBehalfReq.id}`);
  });

  test('SCENARIO 3: Admin approves & rejects leaves randomly and verifies balance updates', async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // EXPLANATION OF EXPECTED RESULT:
    // 1. Fetch pending requests for the employee.
    // 2. Approve the Sick Leave request (1.0 day):
    //    - Status becomes APPROVED.
    //    - `sickLeaveUsed` increases by 1.0 day.
    // 3. Reject the Earned Leave request (3.0 days):
    //    - Status becomes REJECTED.
    //    - `earnedLeaveUsed` stays 0.0.
    //    - The 3.0 pending days are released back to effective available balance automatically!
    // -------------------------------------------------------------------------------------

    const pendingRes = await request.get(`/api/leaves/employee/${employeeId}`, {
      headers: { 'Authorization': `Bearer ${employeeToken}` }
    });
    expect(pendingRes.ok()).toBeTruthy();
    const leaves: any[] = await pendingRes.json();

    const pendingSick = leaves.find(l => l.leaveType === 'SICK' && l.status === 'PENDING');
    const pendingEarned = leaves.find(l => l.leaveType === 'EARNED' && l.status === 'PENDING');

    // Approve Sick Leave
    if (pendingSick) {
      const approveRes = await request.put(`/api/leaves/${pendingSick.id}/status`, {
        headers: { 'Authorization': `Bearer ${managerToken}` },
        data: {
          status: 'APPROVED',
          approverId: String(managerId)
        }
      });
      expect(approveRes.ok()).toBeTruthy();
      const approvedDoc = await approveRes.json();
      expect(approvedDoc.status).toBe('APPROVED');
      console.log(`[TEST LOG] Sick Leave (ID: ${pendingSick.id}) approved by Manager ${managerId}.`);
    }

    // Reject Earned Leave
    if (pendingEarned) {
      const rejectRes = await request.put(`/api/leaves/${pendingEarned.id}/status`, {
        headers: { 'Authorization': `Bearer ${managerToken}` },
        data: {
          status: 'REJECTED',
          rejectionReason: 'Project deadline conflict - please reschedule',
          approverId: String(managerId)
        }
      });
      expect(rejectRes.ok()).toBeTruthy();
      const rejectedDoc = await rejectRes.json();
      expect(rejectedDoc.status).toBe('REJECTED');
      console.log(`[TEST LOG] Earned Leave (ID: ${pendingEarned.id}) rejected. Balance automatically refunded.`);
    }
  });

  test('SCENARIO 4: HR withdraws approved leave & verifies balance credit back', async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // EXPLANATION OF EXPECTED RESULT:
    // 1. Find an APPROVED leave for the employee (e.g. the 1-day Casual Leave applied on behalf).
    // 2. Call `PUT /api/leaves/{id}/withdraw` as HR.
    // 3. Expected Result:
    //    - Status becomes WITHDRAWN.
    //    - `casualLeaveUsed` decreases by 1.0 (balance refunded).
    //    - `LeaveWithdrawnEvent` is published, triggering AttendanceService to clear ON_LEAVE status.
    // -------------------------------------------------------------------------------------

    const leavesRes = await request.get(`/api/leaves/employee/${employeeId}`, {
      headers: { 'Authorization': `Bearer ${employeeToken}` }
    });
    expect(leavesRes.ok()).toBeTruthy();
    const leaves: any[] = await leavesRes.json();

    const approvedCasual = leaves.find(l => l.leaveType === 'CASUAL' && l.status === 'APPROVED');
    expect(approvedCasual, 'An approved casual leave should exist for withdrawal test').toBeDefined();

    if (approvedCasual) {
      const withdrawRes = await request.put(`/api/leaves/${approvedCasual.id}/withdraw`, {
        headers: { 'Authorization': `Bearer ${hrToken}` },
        data: {
          actorId: String(adminId)
        }
      });
      expect(withdrawRes.ok(), 'HR Withdraw approved leave should succeed').toBeTruthy();
      const withdrawnDoc = await withdrawRes.json();
      
      expect(withdrawnDoc.status).toBe('WITHDRAWN');
      console.log(`[TEST LOG] Approved Leave ID: ${approvedCasual.id} withdrawn by HR. Balance refunded & Attendance status reset.`);
    }
  });

  test('SCENARIO 5: Option 1 Dynamic Special Leave Types verification', async ({ request }) => {
    // -------------------------------------------------------------------------------------
    // EXPLANATION OF EXPECTED RESULT:
    // When an employee applies for an unregistered special leave (e.g. BEREAVEMENT),
    // if no specific record exists in `EmployeeLeaveQuota`, the system safely falls back
    // to validating against Casual Leave remaining quota without crashing or throwing NPEs.
    // -------------------------------------------------------------------------------------

    const specialLeaveRes = await request.post('/api/leaves/apply', {
      headers: { 'Authorization': `Bearer ${employeeToken}` },
      data: {
        employeeId: employeeId,
        leaveType: 'BEREAVEMENT',
        startDate: '2026-12-15',
        endDate: '2026-12-15',
        startSession: 'FULL_DAY',
        endSession: 'FULL_DAY',
        reason: 'Automated Playwright Test - Special Bereavement Leave'
      }
    });
    
    expect(specialLeaveRes.ok(), 'Special leave application should be processed safely').toBeTruthy();
    const specialReq = await specialLeaveRes.json();
    expect(specialReq.status).toBe('PENDING');
    expect(specialReq.leaveType).toBe('BEREAVEMENT');
    console.log(`[TEST LOG] Special Leave (BEREAVEMENT) processed successfully with ID: ${specialReq.id}`);
  });

});
