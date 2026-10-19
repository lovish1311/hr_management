# Enterprise End-to-End Test Suite: 70 Verbal Real-Life Test Scenarios

This document defines the 70 comprehensive verbal test scenarios across the core enterprise HR pillars:
* **Feature 1: Leave Management Engine** (Scenarios 1 to 25)
* **Feature 2: Attendance Management & Biometric Engine** (Scenarios 26 to 48)
* **Feature 3: Statutory Payroll & Salary Engine** (Scenarios 49 to 70)

---

## Pillar 1: Leave Management Engine (25 Scenarios)

### 1. Casual Leave Standard Deduction
* **Scenario**: Employee with 10 days of Casual Leave (CL) balance applies for 2 consecutive weekdays (Tuesday & Wednesday).
* **Expected Result**: System approves or routes for approval, decrements CL balance to 8.0, and flags those two days as `PAID_LEAVE` in attendance.

### 2. Weekend Exclusion in Multi-Day Leave
* **Scenario**: Employee applies for leave from Friday to the following Monday (4 calendar days).
* **Expected Result**: System identifies Saturday and Sunday as non-working days; deducts exactly 2 days from the leave balance rather than 4 days.

### 3. Single-Day First Half Day Leave
* **Scenario**: Employee applies for a Half-Day Leave for the first half of Thursday (morning slot).
* **Expected Result**: Balance decreases by exactly 0.5 days; attendance record is marked as `HALF_DAY_LEAVE`, expecting punch-in for the second half.

### 4. Single-Day Second Half Day Leave
* **Scenario**: Employee works the morning shift and applies for a Half-Day Leave for the second half of Friday.
* **Expected Result**: Balance decreases by 0.5 days; attendance record reflects `HALF_DAY_LEAVE`; morning punch is retained without penalty.

### 5. Short Break Intra-Day Application
* **Scenario**: Employee requests a 1.5-hour short personal break between 11:30 AM and 01:00 PM.
* **Expected Result**: No deduction from full-day leave quotas; creates a short break permission; attendance engine waives departure during that duration.

### 6. Pre-Approved Late Arrival Permission
* **Scenario**: Employee anticipates heavy traffic or a doctor visit and applies for an approved late arrival until 11:30 AM.
* **Expected Result**: Approved request grants an arrival exemption until 11:30 AM; checking in at 11:15 AM retains status `PRESENT` instead of `LATE`.

### 7. Early Departure Permission
* **Scenario**: Employee has family commitments and requests an approved early departure at 05:00 PM (shift ends at 07:00 PM).
* **Expected Result**: Punching out at 05:05 PM is treated as compliant; status remains `PRESENT (Approved Early Out)` without late or early-out flags.

### 8. Work From Home (WFH) Leave Request
* **Scenario**: Employee requests 3 days of Work From Home for remote sprint delivery.
* **Expected Result**: Full-day paid leave balances remain untouched; attendance automatically marks the calendar days as `PRESENT (WFH)`.

### 9. Insufficient Balance Loss-of-Pay (LOP) Transition
* **Scenario**: Employee has 0 CL remaining and requests 3 days of unplanned personal leave.
* **Expected Result**: System automatically categorizes the leave as `UNPAID_LEAVE` / `LOSS_OF_PAY`; attendance tags days as `LOP_LEAVE` and alerts payroll.

### 10. Split Leave Allocation (Paid + LOP)
* **Scenario**: Employee has 1 day of Paid Leave left but requests a 3-day absence.
* **Expected Result**: Engine consumes the remaining 1 paid day and converts the remaining 2 days to `LOSS_OF_PAY` without crashing.

### 11. Overlapping Date Collision Prevention
* **Scenario**: Employee with an approved leave on October 15 attempts to submit another leave request covering October 14 through October 16.
* **Expected Result**: Validation engine rejects submission with an explicit date-collision conflict error; balance is preserved.

### 12. Negative Date Range Inversion Rejection
* **Scenario**: Employee enters an end date (e.g., Oct 10) that is strictly before the start date (e.g., Oct 14).
* **Expected Result**: Form and backend reject request with `Invalid date range` validation error.

### 13. Direct Manager Multi-Tier Approval Chain
* **Scenario**: Employee submits a 4-day medical leave. Their direct manager logs in to their dashboard.
* **Expected Result**: Manager sees pending request in Team Approvals; upon manager approval, status transitions to `APPROVED` and balance deducts.

### 14. Manager Rejection and Quota Reversion
* **Scenario**: Employee submits a leave request which temporarily puts days in `PENDING` state. Manager rejects the request.
* **Expected Result**: Request state transitions to `REJECTED`; reserved quota immediately reverts back to available balance; employee receives email.

### 15. Employee Self-Withdrawal of Pending Leave
* **Scenario**: Employee submits a leave for next month, then changes plans while it is still pending manager review.
* **Expected Result**: Employee clicks `Withdraw Request`; system cancels request, restores reserved balance, and marks status as `WITHDRAWN`.

### 16. Cancellation of Future Approved Leave
* **Scenario**: Employee has an already approved leave scheduled for next week, but decides to work instead.
* **Expected Result**: Employee submits cancellation; manager or system approves cancellation; attendance restores working day status and refunds balance.

### 17. Document Attachment for Sick Leave Exceeding 2 Days
* **Scenario**: Employee applies for 3 consecutive days of Sick Leave.
* **Expected Result**: System prompts for medical certificate attachment; prevents submission without supporting document if required by policy.

### 18. Maximum Consecutive Leave Threshold Warning
* **Scenario**: Employee requests 15 consecutive working days off.
* **Expected Result**: System triggers an extended absence advisory alert and requires secondary HR review before finalization.

### 19. Annual Quota Carry-Forward Rollover
* **Scenario**: Fiscal year closes. Employee has 4 unconsumed Earned Leaves with policy allowing up to 8 carried forward.
* **Expected Result**: System adds 4 carried-over days to next year's opening balance and resets Casual Leaves to opening entitlement.

### 20. Encashment of Excess Earned Leave
* **Scenario**: Employee eligible for leave encashment requests liquidation of 5 accrued Earned Leaves.
* **Expected Result**: Leave engine reduces Earned Leave balance by 5 and generates an encashment credit transaction passed into Payroll inputs.

### 21. Sandwich Leave Rule Policy Enforcement
* **Scenario**: Company policy enforces sandwich rule: employee takes Friday off and Monday off across a weekend.
* **Expected Result**: If sandwich rule is active in company settings, weekend days (Sat/Sun) are counted as leave days; if inactive, only 2 days deducted.

### 22. Concurrent Double-Submission Race Condition Guard
* **Scenario**: Employee rapidly double-clicks `Submit Request` on an application consuming their last remaining leave day.
* **Expected Result**: Transactional locking or DB idempotency ensures only 1 application succeeds; second attempt throws duplicate or insufficient balance error.

### 23. Historical Past Leave Regularization by Admin
* **Scenario**: Admin retroactively regularizes an unexcused absence that occurred 2 weeks ago due to emergency hospital admission.
* **Expected Result**: Admin converts absence to Sick Leave; attendance status updates from `UNEXCUSED_ABSENT` to `PAID_LEAVE`; payroll recalculates LOP.

### 24. Public Holiday Overlap Exclusion
* **Scenario**: Employee takes leave from Wednesday to Friday, where Thursday is an official declared Public Holiday (e.g., Diwali).
* **Expected Result**: System identifies Thursday as `HOLIDAY`; deducts only 2 days of leave balance (Wednesday and Friday).

### 25. Leave Sync Resilience on Attendance Record Absence
* **Scenario**: A new employee joins and takes leave on day 2 before any daily attendance rows were initialized.
* **Expected Result**: Leave engine safely upserts an attendance row stamped `ON_LEAVE` without throwing `NullPointerException`.

---

## Pillar 2: Attendance Management & Biometric Engine (23 Scenarios)

### 26. Punctual Morning Check-In (On Time)
* **Scenario**: Shift starts at 10:00 AM with a 15-min grace period (cutoff 10:15 AM). Employee punches in at 09:45 AM.
* **Expected Result**: Attendance recorded with `checkInTime = 09:45`; status marked as `PRESENT`.

### 27. Grace Period Boundary Punch
* **Scenario**: Employee punches in at exactly 10:15:00 AM.
* **Expected Result**: 10:15:00 is within the grace cutoff; status marked as `PRESENT`.

### 28. Late Arrival After Grace Period
* **Scenario**: Employee punches in at 10:22 AM without an approved late waiver.
* **Expected Result**: System detects 7 minutes past grace; status marked as `LATE`; status label displays `"Late Check-In (10:22 AM)"`.

### 29. Check-In Idempotency (Morning Punch Preservation)
* **Scenario**: Employee punches in at 09:30 AM, then taps the check-in button or machine again at 11:00 AM.
* **Expected Result**: Morning punch time (09:30 AM) is permanently preserved; 11:00 AM does not overwrite check-in time.

### 30. Progressive Check-Out Timestamp Capture
* **Scenario**: Employee who checked in at 09:30 AM punches out at 07:00 PM.
* **Expected Result**: `checkOutTime` set to 19:00; `totalWorkingMinutes` calculated as 570 minutes (9h 30m); status remains `PRESENT`.

### 31. Under-4-Hours Half-Day Classification
* **Scenario**: Employee checks in at 10:00 AM and checks out early at 01:30 PM (total working time 210 minutes).
* **Expected Result**: System evaluates duration < 240 minutes; status automatically reclassifies to `HALF_DAY`.

### 32. Over-8-Hours Full-Day Duration Compliance
* **Scenario**: Employee checks in at 10:00 AM and checks out at 07:15 PM (total working time 555 minutes).
* **Expected Result**: System confirms duration >= 480 minutes; full day compliance achieved; status remains `PRESENT`.

### 33. Check-Out Attempt Before Check-In
* **Scenario**: An employee attempts to call the check-out endpoint or trigger an evening punch without any prior check-in for the day.
* **Expected Result**: System blocks operation with `IllegalStateException: Cannot check out before checking in`.

### 34. Check-In Blocked by Approved Full-Day Leave
* **Scenario**: Employee is marked on approved full-day Casual Leave. They accidentally tap Check-In in the app.
* **Expected Result**: System rejects punch with `Cannot check in: Employee is marked on approved leave`.

### 35. Half-Day Leave Second-Half Punch In
* **Scenario**: Employee took an approved First-Half Leave. They punch in at 02:00 PM for the second half.
* **Expected Result**: System accepts second-half punch; status remains `HALF_DAY_LEAVE` with `checkInTime = 14:00`.

### 36. Biometric Excel Multi-Punch Ingestion
* **Scenario**: Admin uploads raw biometric machine Excel export containing 28 employees with multiple in/out punch columns.
* **Expected Result**: Pure JVM in-memory parser extracts timestamps in sub-100ms without temporary files or Python dependencies.

### 37. Multi-Row In-Batch Biometric Deduplication
* **Scenario**: Raw machine export lists Employee Karan Kapoor twice: Row 1 has morning punches (09:30 AM, 01:00 PM), Row 2 has afternoon punches (02:00 PM, 07:00 PM).
* **Expected Result**: Engine aggregates punches across rows; earliest punch (09:30 AM) becomes checkIn, latest punch (07:00 PM) becomes checkOut; saves 1 consolidated record.

### 38. Database Constraint Duplicate Rejection
* **Scenario**: Two concurrent API punch requests with identical `(employeeId, date)` reach the database at the same millisecond.
* **Expected Result**: `uk_attendance_emp_date` unique constraint catches the collision; database integrity violation is handled gracefully without orphan duplicates.

### 39. IDOR Attack Prevention on Punch Endpoints
* **Scenario**: Employee ID 5 sends a request to `/api/v1/attendance/check-in?employeeId=8` using their own JWT token.
* **Expected Result**: `AttendanceSecurity.isSelf` detects caller ID does not match target ID 8; returns `403 Forbidden`.

### 40. RBAC Protection on Super Admin Clear-All Action
* **Scenario**: An unauthorized user or standard employee attempts `DELETE /api/v1/attendance/clear-all`.
* **Expected Result**: Spring Security rejects call with `403 Forbidden`; only `SUPER_ADMIN` role can execute database wipe.

### 41. Weekend Automatic Non-Working Designation
* **Scenario**: System generates the monthly attendance calendar for an employee across October.
* **Expected Result**: All Saturdays and Sundays are automatically flagged with `isWeekend = true` and status `WEEKEND`; unpunched weekend days incur zero penalties.

### 42. Public Holiday Attendance Suppression
* **Scenario**: An official holiday occurs on October 2.
* **Expected Result**: Day is stamped `HOLIDAY`; employees without punches are not flagged as unexcused absent.

### 43. Unexcused Absence LOP Tagging for Past Working Days
* **Scenario**: A past Tuesday was a regular working day, but employee neither punched nor submitted an approved leave.
* **Expected Result**: Calendar engine stamps day as `UNEXCUSED_ABSENT` with status label `"Absent (LOP)"`.

### 44. Future Days Display as Upcoming
* **Scenario**: Employee reviews calendar view on October 19.
* **Expected Result**: Days from October 20 to October 31 display as `UPCOMING` with label `"Upcoming Working Day"` without marking absences.

### 45. Biometric Upload Future Date Guard
* **Scenario**: Admin accidentally selects next month's date when uploading a biometric log.
* **Expected Result**: Backend validation halts processing with `IllegalArgumentException: Cannot import biometric attendance for future dates`.

### 46. Custom Shift Timings for Flexi-Time Employees
* **Scenario**: Employee has custom shift settings (allowed until 11:00 AM).
* **Expected Result**: Punching in at 10:45 AM uses the employee's custom grace cutoff and records `PRESENT (Late Exemption Granted)`.

### 47. Automated Leave Event Withdrawal Attendance Reset
* **Scenario**: Employee had an approved leave on October 18, then withdraws it.
* **Expected Result**: `LeaveEventListener` triggers `removeLeaveFromAttendance`, clearing the `ON_LEAVE` status and re-evaluating true punch state.

### 48. Biometric Employee Name Fallback Matching
* **Scenario**: Biometric machine prints first name and partial initial (e.g. "Karan K.").
* **Expected Result**: Engine checks `biometricName`, then full name, then first name prefix token to reliably link punches to Employee ID.

---

## Pillar 3: Statutory Payroll & Salary Engine (22 Scenarios)

### 49. Base Gross Salary Pro-Rata for Clean Month
* **Scenario**: Employee with ₹60,000 monthly gross salary worked all 30 days of the month with zero absences.
* **Expected Result**: Earned basic and allowances equal 100% of CTC monthly gross; zero Loss-of-Pay deductions.

### 50. Loss-of-Pay (LOP) Exact Day Deduction
* **Scenario**: Employee with Basic Salary of ₹30,000 has 2 days of unexcused absence (`UNEXCUSED_ABSENT`).
* **Expected Result**: Per-day basic deduction = ₹30,000 / 30 = ₹1,000 per day. LOP deduction = ₹2,000.

### 51. Half-Day Leave 0.5 LOP Deduction
* **Scenario**: Employee has 1 unexcused half-day (`HALF_DAY` or `HALF_DAY_LEAVE`) with Basic Salary ₹30,000.
* **Expected Result**: LOP days = 0.5; exact basic deduction = ₹500; net taxable earnings adjust accordingly.

### 52. Weekend Days Excluded from LOP Penalties
* **Scenario**: Employee worked Monday through Friday, with no punches logged on Saturday or Sunday.
* **Expected Result**: Payroll engine verifies both Saturday and Sunday as rest days; LOP count remains 0.

### 53. Statutory Provident Fund (PF) Employee Deduction (12%)
* **Scenario**: Employee has earned basic salary of ₹15,000.
* **Expected Result**: Employee PF = 12% of ₹15,000 = ₹1,800. Net pay reflects exact ₹1,800 deduction.

### 54. Statutory PF Wage Ceiling Capping
* **Scenario**: Employee has high basic salary of ₹80,000, company follows statutory capping on ₹15,000 wage ceiling.
* **Expected Result**: Employee PF deduction is capped at ₹1,800 rather than 12% of ₹80,000 (₹9,600).

### 55. Employer PF Contribution Split (EPF + EPS)
* **Scenario**: Processing employer payroll contribution for basic salary of ₹15,000.
* **Expected Result**: Employer PF calculates 12% (₹1,800 total), split into 8.33% EPS (₹1,250) and 3.67% EPF (₹550).

### 56. Employee State Insurance (ESIC) Deduction Threshold
* **Scenario**: Employee gross salary is ₹18,000 (below the ₹21,000 statutory limit).
* **Expected Result**: Employee ESI (0.75%) = ₹135; Employer ESI (3.25%) = ₹585; deductions applied to slip.

### 57. ESIC Exemption for Salaries Exceeding ₹21,000
* **Scenario**: Employee gross salary is ₹35,000 (exceeds ₹21,000 statutory wage threshold).
* **Expected Result**: ESI employee and employer contributions evaluate to ₹0.00.

### 58. Professional Tax (PT) State Slab Application
* **Scenario**: Employee in Maharashtra/Karnataka/Telangana with monthly gross exceeding ₹15,000.
* **Expected Result**: Standard monthly PT of ₹200 deducted; February deduction correctly calculates ₹300 if applicable.

### 59. New Tax Regime Standard Deduction (₹75,000)
* **Scenario**: Computing annual income tax under New Regime (Finance Act 2024/2025).
* **Expected Result**: Standard deduction of ₹75,000 is applied against gross annual taxable income before slab calculation.

### 60. Section 87A Tax Rebate for Income Up to ₹7 Lakhs
* **Scenario**: Employee annual taxable income under New Tax Regime is ₹6,80,000.
* **Expected Result**: Tax before rebate is calculated, but Section 87A rebate wipes out net tax liability; monthly TDS = ₹0.

### 61. Multi-Slab Tax Calculation for High Earner
* **Scenario**: Employee has annual taxable income of ₹15,00,000 under New Tax Regime.
* **Expected Result**: Slabs applied: 0-3L (0%), 3-7L (5%), 7-10L (10%), 10-12L (15%), 12-15L (20%); 4% cess added; monthly TDS divided by 12.

### 62. One-Time Performance Bonus Addition
* **Scenario**: HR inputs a ₹25,000 quarterly performance bonus in payroll inputs for October.
* **Expected Result**: Gross earnings increase by ₹25,000; taxable earnings and net pay reflect bonus accurately.

### 63. Retroactive Salary Arrear Ingestion
* **Scenario**: Employee had an increment of ₹5,000/month effective from August, finalized in October.
* **Expected Result**: Arrears of ₹10,000 (Aug + Sep) added to October gross earnings under specific arrears column.

### 64. Miscellaneous Voluntary Deductions (Loan / Advance)
* **Scenario**: Employee has a monthly company laptop loan deduction of ₹3,000.
* **Expected Result**: ₹3,000 subtracted under Other Deductions; net salary equals Earnings minus Deductions.

### 65. Draft Payroll Batch Generation
* **Scenario**: HR triggers payroll calculation for October 2026.
* **Expected Result**: Records created in `DRAFT` status; individual breakdown visible for internal HR review.

### 66. Transition from Draft to Verified State
* **Scenario**: HR completes review of the draft payroll batch and clicks `Verify Batch`.
* **Expected Result**: Records transition to `VERIFIED`; ready for finance leadership sign-off.

### 67. Payroll Publishing and Input Lock Enforcement
* **Scenario**: Admin approves and clicks `Publish Payroll`.
* **Expected Result**: State transitions to `PUBLISHED`; `inputsLocked` set to `true`; employees immediately see payslips in mobile app.

### 68. Mutation Rejection on Locked Payroll Inputs
* **Scenario**: User attempts to update bonus or LOP adjustments for October after payroll has been published.
* **Expected Result**: Service rejects mutation with `IllegalStateException: Payroll inputs for this month are locked`.

### 69. High-Fidelity Payslip PDF Generation
* **Scenario**: Employee taps `Download Payslip` in mobile app for October 2026.
* **Expected Result**: Native PDF generated containing company logo, employee code, UAN, PAN, gross earnings, itemized deductions, and net pay in words.

### 70. Bank Payout Disbursal CSV Export
* **Scenario**: Finance team exports bank payout file for published batch.
* **Expected Result**: CSV generated with Beneficiary Name, Account Number, IFSC Code, Net Disbursal Amount, and Narration formatted for corporate banking batch upload.
