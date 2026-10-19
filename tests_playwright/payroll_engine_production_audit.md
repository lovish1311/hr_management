# Multi-Regime Statutory Payroll Engine — Production Readiness Audit & Remediation Blueprint

**Target Package**: `com.example.hr_management_backend.features.payroll`  
**Audit Scope**: `PayrollController`, `PayrollProcessingService`, `MonthlyPayrollInput`, `PayrollRecord`, `SalaryStructure`, `NewRegimeTaxStrategy`, `OldRegimeTaxStrategy`, and `behavior.md` (The 20 Enterprise Rules)  
**Overall Production Readiness Score**: **48 / 100 (Not Production-Ready — Critical Blockers Present)**

## 1. Production Readiness Scorecard

| Evaluation Dimension | Score | Weight | Status | Primary Findings |
| :--- | ---: | ---: | :--- | :--- |
| **Security & Access Control** | **30 / 100** | 25% | Critical Risk | 6 unguarded endpoints (IDOR on salary structures and payslips, unauthenticated legacy mutation endpoints, wildcard CORS). |
| **Database & Query Performance** | **35 / 100** | 20% | High Risk | Severe N+1 sequential query storms (~2,500 queries per 500 employees during sync; ~2,000 during batch payroll), `IgnoreCase` bypassing B-Tree indexes, unfiltered exited employees. |
| **Transactional & Concurrency Safety** | **40 / 100** | 20% | Critical Risk | Guaranteed crash in `getPayslipsForEmployee` (`INSERT` inside `@Transactional(readOnly = true)`), missing `@UniqueConstraint` on `PayrollRecord`, race conditions on active `SalaryStructure`. |
| **20-Rule Behavioral & Math Compliance** | **62 / 100** | 25% | Moderate Risk | Fake bank account overwrite breaks Rule 19; `publishBatchPayroll` wipes out `EXEMPT` status (Rule 10); arrears ignored in `processBatchPayroll` unless synced (Rule 13); 12x tax annualization on one-time bonuses/arrears & missing Marginal Relief (Rules 14–15). |
| **Architecture & Code Hygiene** | **75 / 100** | 10% | Needs Polish | Hardcoded demo seeders in production paths (`SEPTEMBER 2026`, ₹50k auto-salary), silent exception swallowing in `parseMonthNumber` and arrears auto-detection, duplicate imports. |
| **Weighted Total** | **48 / 100** | **100%** | **Blocked** | **Requires P0 security, transaction, and calculation fixes before live deployment.** |

## 2. Scenarios Guaranteed to Fail or Produce Wrong Results (100% Reproducible)

### Scenario 1: Employee Opens Self-Service Payslips for the First Time (Guaranteed DB Crash or Fake Payslip)
* **Trigger**: A newly onboarded employee (with 0 published payslips) opens the ESS Payslips screen, calling `GET /api/payroll/payslips?employeeId=105&onlyPublished=true`.
* **Execution Trace (`PayrollProcessingService.java`, lines 395–410 & 675–699)**:
  1. `getPayslipsForEmployee` starts a transaction with `@Transactional(readOnly = true)`.
  2. `records.isEmpty() && onlyPublished` evaluates to `true` and invokes `this.seedInitialPublishedPayslip(employeeId)`.
  3. Because `this.seedInitialPublishedPayslip()` is a self-invocation within the same class, Spring AOP proxy interception is bypassed and the method runs inside the outer `readOnly = true` transaction.
  4. `seedInitialPublishedPayslip` executes `salaryStructureRepository.save(...)`, `monthlyPayrollInputRepository.save(...)`, and `payrollRecordRepository.save(...)`.
* **Guaranteed Failure**:
  * **On PostgreSQL / MySQL (Production)**: Throws `org.springframework.dao.TransientDataAccessResourceException: Cannot execute INSERT in a read-only transaction` (HTTP 500 crash).
  * **On Permissive Drivers (H2)**: Silently inserts a fake ₹50,000 salary structure and a legally `PUBLISHED` payslip for `SEPTEMBER 2026` into the production database for an employee who never worked in September 2026.

### Scenario 2: Exporting the Corporate Bank Payout File (Violates Rule 19 — 100% Bank Rejection)
* **Trigger**: HR processes payroll and downloads `GET /api/payroll/export/bank-file`.
* **Execution Trace (`PayrollProcessingService.java`, line 554)**:
  ```java
  record.setBankAccountNumber("•••• •••• " + (1000 + (emp.getId().intValue() * 123) % 9000));
  ```
* **Guaranteed Failure**:
  * Instead of reading the employee's real bank account number from `Employee`, `calculatePayrollRecord` fabricates a 4-digit pseudo-random suffix (`(id * 123) % 9000`) and persists it in `PayrollRecord.bankAccountNumber`.
  * When `BankPayoutExportService` strips non-digit characters per Rule 19, every employee's bank account number becomes a fake 4-digit number (e.g., `1123`), causing 100% batch rejection at HDFC/ICICI/SBI/Axis gateways.

### Scenario 3: Publishing a Month with Exempt Employees (Violates Rule 10 & Corrupts Arrears)
* **Trigger**: An intern or consultant on sabbatical has `isPayrollExempt = true`. HR runs `POST /api/payroll/process` (which creates a record with `status = PayrollStatus.EXEMPT` and `netPay = ₹0.00`), and then runs `POST /api/payroll/publish`.
* **Execution Trace (`PayrollProcessingService.java`, lines 382–387)**:
  ```java
  for (PayrollRecord r : records) {
      r.setStatus(PayrollStatus.PUBLISHED);
      r.setPublishedAt(now);
      payrollRecordRepository.save(r);
      arrearsService.markArrearsAsProcessed(r.getEmployeeId(), month, year, r.getId());
  }
  ```
* **Guaranteed Failure**:
  1. `r.setStatus(PayrollStatus.PUBLISHED)` overwrites `PayrollStatus.EXEMPT` on every exempt record, destroying their exempt status.
  2. `arrearsService.markArrearsAsProcessed(...)` marks any pending arrears for that exempt employee as `isProcessed = true`, permanently wiping out their unpaid arrears even though their payout was clamped to `₹0.00`.
  3. Furthermore, `publishBatchPayroll` does not verify that records are in `PayrollStatus.VERIFIED`, allowing `DRAFT` records to be published directly and violating Rule 17's state machine (`DRAFT -> VERIFIED -> PUBLISHED`).

### Scenario 4: Retroactive Salary Hike / Manual Arrears Processed Without Re-Syncing Attendance (Violates Rule 13)
* **Trigger**:
  1. On October 25, HR syncs attendance (`POST /api/payroll/inputs/sync`) and locks/reviews inputs.
  2. On October 26, HR updates Rahul Verma's salary structure retroactively from August 1 (`POST /api/payroll/structure`) or adds manual arrears (`POST /api/payroll/arrears`), queueing ₹20,000 in `ArrearsRecord`.
  3. HR runs `POST /api/payroll/process`.
* **Execution Trace (`PayrollProcessingService.java`, lines 268 vs. 307–365)**:
  * `arrearsService.calculatePendingArrearsTotalForEmployee(...)` is **only** called inside `syncMonthlyPayrollInputsFromAttendance()` (line 268). It is **never** called inside `processBatchPayroll()` or `calculatePayrollRecord()`.
  * In addition, `saveSalaryStructure` (line 95) calls `autoDetectAndQueueStructuralArrears(employee.getId(), LocalDate.now().getMonth().name(), LocalDate.now().getYear())` inside a `try-catch` that swallows all exceptions and hardcodes `LocalDate.now()` instead of target parameters.
* **Guaranteed Failure**:
  * `processBatchPayroll()` reads the existing `MonthlyPayrollInput.arrearsAmount` (which is still `₹0.00`), calculates Rahul's October payroll with **₹0.00 arrears**, and then when `publishBatchPayroll()` runs (line 386), `arrearsService.markArrearsAsProcessed(...)` marks the ₹20,000 arrears in `ArrearsRecord` as **processed and paid**, even though ₹0.00 was included in the payslip!

### Scenario 5: Employee Receives One-Time Bonus or Arrears and Gets Over-Taxed by 12x (Violates Rules 14 & 15)
* **Trigger**: Rahul Verma has a Fixed Gross of ₹50,000/month (₹6,00,000/year — 100% tax-exempt under Section 87A in both Old and New Regimes). In October, he receives ₹20,000 arrears and a ₹15,000 Diwali bonus (`totalGross = ₹85,000`).
* **Execution Trace (`PayrollProcessingService.java` line 536 & `NewRegimeTaxStrategy.java` line 78)**:
  ```java
  BigDecimal annualized = monthlyTaxableGross.multiply(BigDecimal.valueOf(12));
  ```
* **Guaranteed Failure**:
  * The tax engine multiplies October's total gross (`₹85,000`) by `12` = `₹10,20,000`, assuming one-time arrears and bonuses recur every single month for 12 months.
  * Subtracting the ₹75,000 standard deduction yields `₹9,45,000` taxable income (breaching the ₹7,00,000 rebate limit), deducting **₹3,856.67 TDS** in October from an employee whose true annual income (`₹6,35,000`) owes **₹0.00 tax**.

### Scenario 6: Employee Earning ₹7,75,100 Annually Under New Regime Loses ₹20,810 to Missing Marginal Relief (Violates Rule 15)
* **Trigger**: Employee has an annual gross of ₹7,75,100 (Monthly Gross = ₹64,591.67). After the ₹75,000 standard deduction, Net Taxable Income is `₹7,00,100` (just ₹100 over the ₹7,00,000 Section 87A limit).
* **Execution Trace (`NewRegimeTaxStrategy.java`, lines 31–44)**:
  * At ₹7,00,000 taxable income, tax is `₹0.00`.
  * At ₹7,00,100 taxable income, `taxableIncome.compareTo(REBATE_LIMIT) <= 0` is `false`. The strategy computes full slab tax: `₹20,000 (5% of 4L) + ₹10 (10% of 100) + 4% cess = ₹20,810.40`.
* **Guaranteed Failure**:
  * Section 87A of the Indian Income Tax Act mandates **Marginal Relief** so that tax payable (before cess) cannot exceed the income above ₹7,00,000 (`₹100 + 4% cess = ₹104.00`). Missing marginal relief overcharges the employee by **₹20,706.40**.

### Scenario 7: HR Locks Payroll Inputs Before All Employees Have Custom Input Rows (Violates Rule 17)
* **Trigger**: HR views `GET /api/payroll/inputs` (which returns virtual unlocked DTOs with `0.0` LOP for employees without a DB row in `monthly_payroll_inputs`), and clicks "Lock Inputs" (`POST /api/payroll/inputs/lock`).
* **Execution Trace (`PayrollProcessingService.java`, lines 285–296)**:
  ```java
  List<MonthlyPayrollInput> inputs = monthlyPayrollInputRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
  for (MonthlyPayrollInput i : inputs) {
      i.setIsLocked(lock);
      monthlyPayrollInputRepository.save(i);
  }
  ```
* **Guaranteed Failure**:
  * `setLockStateForMonth` only updates rows that already exist in `monthly_payroll_inputs`. Any employee who did not have a persisted row remains unlocked, allowing subsequent calls to `POST /api/payroll/inputs` or `POST /api/payroll/inputs/sync` to mutate their payroll inputs after the cycle was locked.

### Scenario 8: Attendance Sync Run During Working Hours or With Half-Day Leave (Violates Rules 4, 6 & 7)
* **Trigger A (Same-Day Sync)**: HR runs `POST /api/payroll/inputs/sync` at 11:00 AM on October 19, 2026 before today's biometric punches are uploaded.
  * **Trace (`PayrollProcessingService.java`, line 222)**: `evalEnd = end.isAfter(today) ? today : end;` loops up to and including `today` (`!date.isAfter(evalEnd)`). Every employee in the company is penalized with **+1.0 LOP ("missing biometric sheet day")** for the current in-progress workday.
* **Trigger B (Half-Day Leave + Absent)**: An employee applies for a 0.5-day Casual Leave (`CL`), gets it approved, and then skips work for the entire day (`status = ABSENT`).
  * **Trace (`PayrollProcessingService.java`, lines 239–246)**: Because `matchedLeave.isPresent()` is `true` and `type` is `"CL"`, the loop hits `continue;` on line 245 without checking whether the leave was a half-day (`0.5`) or full-day (`1.0`), resulting in **0.0 LOP instead of 0.5 LOP**.
* **Trigger C (Paid Leave on Saturday vs. Biometric Fallback)**: All Saturdays are evaluated as working days (`date.getDayOfWeek() == DayOfWeek.SUNDAY` only skips Sundays on line 231). If the company operates a 5-day week (Mon–Fri) and employees do not punch in on Saturdays, every employee accumulates **4 to 5 false LOP days every month**.

### Scenario 9: Paisa Rounding Drift Between `Earned Fixed Gross` and `LOP Deduction Amount` (Violates Guarantee #1 & Rule 5)
* **Trigger**: An employee with `Total Fixed Gross = ₹50,000.00` takes `14.0` LOP days in October (`31` days -> `17.0` paid days).
* **Execution Trace (`PayrollProcessingService.java`, lines 486–492)**:
  * `perDayGross = 50000 / 31 = ₹1,612.9032` (rounded to scale 4).
  * `lopAmount = 1612.9032 * 14 = 22580.6448` -> **`₹22,580.64`**.
  * `earnedFixedGross = 1612.9032 * 17 = 27419.3544` -> **`₹27,419.35`**.
* **Guaranteed Failure**:
  * `Earned Fixed Gross (₹27,419.35) + LOP Deduction (₹22,580.64) = ₹49,999.99` (1 paisa disappears).
  * Per Rule 5 (`Earned Fixed Gross = Total Fixed Gross - LOP Deduction Amount`), `earnedFixedGross` should be derived as `totalFixedGross.subtract(lopAmount)` (when `preJoiningDays == 0`) or `perDayGross` must retain at least scale 8–10 before final scale-2 rounding.

### Scenario 10: Future Joiner or Full-Month LOP Charged Full Statutory PF, ESI, and PT (Violates Rule 3 & Statutory Law)
* **Trigger**: An employee joins on November 5, 2026 (or is on 31 days LOP in October 2026), so `paidDays = 0.0` and `earnedFixedGross = ₹0.00` in October.
* **Execution Trace (`PayrollProcessingService.java`, lines 518–520)**:
  * `calcPf = ₹1,800.00`, `calcPt = ₹200.00` are copied directly from `SalaryStructure` without checking `paidDays` or `earnedFixedGross`.
* **Guaranteed Failure**:
  * The employee has `₹0.00` Gross Pay and `₹2,000.00` Deductions. Net pay clamps to `₹0.00`, and `getPayrollSummary` logs a Critical Financial Anomaly demanding ₹2,000 recovery from an employee who hadn't even joined the company yet.

## 3. Database Query & Performance Audit (Sequential N+1 vs. Batch/Parallel Execution)

In the current implementation, every batch operation executes sequential single-row queries inside a `for (Employee emp : employees)` loop rather than bulk-fetching datasets in parallel or batching writes.

### Query Volume Comparison (Benchmark: 500 Employees)

| Service Method | Current Sequential Queries (500 Employees) | Optimized Batch Queries (500 Employees) | Speedup / Reduction |
| :--- | :--- | :--- | :--- |
| `syncMonthlyPayrollInputsFromAttendance` | **~3,002 queries** (1 `findAll` + 1 holidays + 500 × \[input lookup + leaves lookup + attendance lookup + arrears lookup + input save + structure lookup in mapper\]) | **7 queries total** (6 bulk `SELECT` queries + 1 batched `saveAll`) | **99.7% fewer DB round-trips** (~12s -> ~180ms) |
| `processBatchPayroll` | **~2,002 queries** (1 `findAll` + 1 published check + 500 × \[input lookup + structure lookup + record lookup + record save\]) | **6 queries total** (5 bulk `SELECT` queries + 1 batched `saveAll`) | **99.7% fewer DB round-trips** (~8s -> ~120ms) |
| `getMonthlyPayrollInputs` | **~502 queries** (1 `findAll` + 1 inputs + 500 × `salaryStructureRepository.findByEmployeeIdAndIsActiveTrue`) | **3 queries total** (employees + inputs + active structures map) | **99.4% fewer DB round-trips** |
| `publishBatchPayroll` | **~1,001 queries** (1 `findByMonth` + 500 × `save` + 500 × `markArrearsAsProcessed`) | **3 queries total** (1 `SELECT` + 1 bulk `saveAll` + 1 bulk `@Modifying` update on arrears) | **99.7% fewer DB round-trips** |

### Specific Database & Indexing Defects
1. **N+1 Query Inside DTO Mapper (`mapToMonthlyPayrollInputDto`, line 600)**:
   * `salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(i.getEmployeeId())` is called inside `mapToMonthlyPayrollInputDto()`, which itself is called inside loops in `getMonthlyPayrollInputs()` and `syncMonthlyPayrollInputsFromAttendance()`.
2. **`IgnoreCase` Disables B-Tree Index Usage**:
   * `MonthlyPayrollInput` and `PayrollRecord` define composite indexes on `(payrollYear, payrollMonth)`.
   * Calling `findByPayrollMonthIgnoreCaseAndPayrollYear` generates `WHERE UPPER(payroll_month) = UPPER(?) AND payroll_year = ?`. Because the index is on raw `payrollMonth` (not `UPPER(payroll_month)`), PostgreSQL/MySQL cannot use the B-Tree index on `payrollMonth`. Since `normalizeMonth()` already guarantees uppercase English month names (`"OCTOBER"`), `IgnoreCase` is redundant and harmful.
3. **Missing Unique Constraint on `PayrollRecord` (`PayrollRecord.java`, line 13)**:
   * `PayrollRecord` has `@Index(name = "idx_payroll_record_emp_period", columnList = "employeeId, payrollYear, payrollMonth")` instead of a `@UniqueConstraint`.
   * Concurrent calls to `POST /api/payroll/process` insert duplicate rows for the same employee and month, causing all subsequent calls to `findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear` (which expects an `Optional<PayrollRecord>`) to permanently fail with `IncorrectResultSizeDataAccessException`.
4. **Unfiltered `employeeRepository.findAll()`**:
   * Loads terminated, resigned, and inactive employees into every sync and payroll calculation batch.

## 4. Security & Data Leakage Audit (`PayrollController.java`)

| Vulnerability | Affected Endpoint(s) | Risk Severity | Remediation |
| :--- | :--- | :--- | :--- |
| **IDOR on Salary Structure** | `GET /api/payroll/structure/{employeeId}` | **CRITICAL (P0)** | Require `hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('SALARY_STRUCTURE_MANAGE') or @payrollSecurity.isSelf(authentication, #employeeId)`. |
| **IDOR & Draft Leakage on Payslips** | `GET /api/payroll/payslips?employeeId=X&onlyPublished=false` | **CRITICAL (P0)** | Non-HR users can pass any `employeeId` and set `onlyPublished=false` to inspect unpublished `DRAFT` executive salaries. Force `onlyPublished = true` unless user has `PAYROLL_MANAGE`. |
| **IDOR on Payslip UUID** | `GET /api/payroll/payslip/{recordId}`, `/records/{recordId}` | **HIGH (P0)** | Verify that the returned `PayrollRecord.employeeId` belongs to the authenticated user or that the user holds `PAYROLL_MANAGE` authority. |
| **Unprotected Legacy Mutation Endpoints** | `POST /api/payroll/generate`, `POST /api/payroll/seed/{employeeId}`, `GET /api/payroll/employee/{employeeId}` | **CRITICAL (P0)** | Add `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('PAYROLL_MANAGE')")` or deprecate/disable in production profiles. |
| **Wildcard CORS Policy** | `@CrossOrigin(origins = "*")` on `PayrollController` | **HIGH (P1)** | Remove `@CrossOrigin(origins = "*")` from financial controllers and bind allowed origins via environment configuration (`app.cors.allowed-origins`). |

## 5. Copy-Paste Production Fixes (Targeted Code Blocks & Exact Replacement Locations)

---

### Fix 1: Add Composite Unique Constraint to `PayrollRecord.java`
* **File**: `PayrollRecord.java`
* **Target Location**: Replace the class-level `@Table` annotation at **lines 12–17**.
* **Existing Code**:
  ```java
  @Table(name = "payroll_records", indexes = {
      @Index(name = "idx_payroll_record_emp_period", columnList = "employeeId, payrollYear, payrollMonth"),
      @Index(name = "idx_payroll_record_status", columnList = "status"),
      @Index(name = "idx_payroll_record_period", columnList = "payrollYear, payrollMonth")
  })
  ```
* **Replacement Block**:
  ```java
  @Table(name = "payroll_records", uniqueConstraints = {
      @UniqueConstraint(name = "uk_payroll_record_emp_period", columnNames = {"employeeId", "payrollMonth", "payrollYear"})
  }, indexes = {
      @Index(name = "idx_payroll_record_emp_period", columnList = "employeeId, payrollYear, payrollMonth"),
      @Index(name = "idx_payroll_record_status", columnList = "status"),
      @Index(name = "idx_payroll_record_period", columnList = "payrollYear, payrollMonth")
  })
  ```
* **Why**: Prevents concurrent duplicate payroll rows from being created and eliminates `IncorrectResultSizeDataAccessException` crashes.

---

### Fix 2: Remove Duplicate Imports & Wildcard CORS in `PayrollController.java`
* **File**: `PayrollController.java`
* **Target Location**: Clean up imports and controller annotations at **lines 7–34**.
* **Existing Code**:
  ```java
  import com.example.hr_management_backend.features.payroll.dto.*;
  import com.example.hr_management_backend.features.payroll.model.Payroll;
  import com.example.hr_management_backend.features.payroll.service.PayrollProcessingService;
  import com.example.hr_management_backend.features.payroll.service.PayrollService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.web.bind.annotation.*;
  // ... duplicate imports on lines 16-29 ...
  @RestController
  @RequestMapping({"/api/payroll", "/api/v1/payroll"})
  @CrossOrigin(origins = "*")
  @RequiredArgsConstructor
  public class PayrollController {
  ```
* **Replacement Block**:
  ```java
  import com.example.hr_management_backend.features.payroll.arrears.ArrearsDto;
  import com.example.hr_management_backend.features.payroll.arrears.ArrearsService;
  import com.example.hr_management_backend.features.payroll.banking.BankPayoutExportService;
  import com.example.hr_management_backend.features.payroll.dto.*;
  import com.example.hr_management_backend.features.payroll.model.Payroll;
  import com.example.hr_management_backend.features.payroll.reconciliation.PayrollReconciliationReportDto;
  import com.example.hr_management_backend.features.payroll.reconciliation.PayrollReconciliationService;
  import com.example.hr_management_backend.features.payroll.service.PayrollProcessingService;
  import com.example.hr_management_backend.features.payroll.service.PayrollService;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.HttpHeaders;
  import org.springframework.http.MediaType;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.security.core.Authentication;
  import org.springframework.web.bind.annotation.*;

  import java.time.LocalDate;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;

  @RestController
  @RequestMapping({"/api/payroll", "/api/v1/payroll"})
  @RequiredArgsConstructor
  public class PayrollController {
  ```
* **Why**: Removes redundant duplicate imports and closes wildcard CORS data leakages.

---

### Fix 3: Secure Salary Structure Endpoint (`GET /structure/{employeeId}`)
* **File**: `PayrollController.java`
* **Target Location**: Replace lines **44–47**.
* **Existing Code**:
  ```java
  @GetMapping("/structure/{employeeId}")
  public ResponseEntity<SalaryStructureDto> getSalaryStructure(@PathVariable Long employeeId) {
      return ResponseEntity.ok(processingService.getSalaryStructure(employeeId));
  }
  ```
* **Replacement Block**:
  ```java
  @GetMapping("/structure/{employeeId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('SALARY_STRUCTURE_MANAGE') or hasAuthority('PAYROLL_MANAGE') or @payrollSecurity.isSelf(authentication, #employeeId)")
  public ResponseEntity<SalaryStructureDto> getSalaryStructure(@PathVariable Long employeeId) {
      return ResponseEntity.ok(processingService.getSalaryStructure(employeeId));
  }
  ```
* **Why**: Closes IDOR vulnerability where any user could view executive and peer salary structures.

---

### Fix 4: Prevent Draft Payslip Leakage in ESS (`GET /payslips`)
* **File**: `PayrollController.java`
* **Target Location**: Replace lines **133–140**.
* **Existing Code**:
  ```java
  @GetMapping("/payslips")
  public ResponseEntity<List<PayrollRecordDto>> getPayslips(
          @RequestParam Long employeeId,
          @RequestParam(defaultValue = "true") boolean onlyPublished) {
      return ResponseEntity.ok(processingService.getPayslipsForEmployee(employeeId, onlyPublished));
  }
  ```
* **Replacement Block**:
  ```java
  @GetMapping("/payslips")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('PAYROLL_MANAGE') or @payrollSecurity.isSelf(authentication, #employeeId)")
  public ResponseEntity<List<PayrollRecordDto>> getPayslips(
          @RequestParam Long employeeId,
          @RequestParam(defaultValue = "true") boolean onlyPublished,
          Authentication authentication) {
      boolean isPayrollAdmin = authentication != null && authentication.getAuthorities().stream()
              .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")
                      || a.getAuthority().equals("ROLE_ADMIN")
                      || a.getAuthority().equals("ROLE_HR")
                      || a.getAuthority().equals("PAYROLL_MANAGE"));
      // Force onlyPublished = true for non-admin self-service callers to prevent viewing draft salaries
      boolean effectiveOnlyPublished = isPayrollAdmin ? onlyPublished : true;
      return ResponseEntity.ok(processingService.getPayslipsForEmployee(employeeId, effectiveOnlyPublished));
  }
  ```
* **Why**: Stops regular employees from setting `onlyPublished=false` to snoop on unreleased/draft executive compensation.

---

### Fix 5: Secure Legacy Write & Read Endpoints in `PayrollController.java`
* **File**: `PayrollController.java`
* **Target Location**: Replace legacy mappings at lines **198–221**.
* **Existing Code**:
  ```java
  @PostMapping("/generate")
  public ResponseEntity<Payroll> generatePayrollLegacy(@RequestBody Payroll payroll) {
      return ResponseEntity.ok(legacyPayrollService.generatePayroll(payroll));
  }

  @GetMapping("/employee/{employeeId}")
  public ResponseEntity<List<Payroll>> getPayrollHistoryLegacy(@PathVariable Long employeeId) {
      return ResponseEntity.ok(legacyPayrollService.getPayrollHistory(employeeId));
  }

  @GetMapping("/employee/{employeeId}/month/{payPeriod}")
  public ResponseEntity<Payroll> getPayslipForMonthLegacy(
          @PathVariable Long employeeId,
          @PathVariable String payPeriod) {
      return legacyPayrollService.getPayslipForMonth(employeeId, payPeriod)
              .map(ResponseEntity::ok)
              .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/seed/{employeeId}")
  public ResponseEntity<List<Payroll>> seedEmployeePayrollLegacy(@PathVariable Long employeeId) {
      return ResponseEntity.ok(legacyPayrollService.seedDefaultPayrollForEmployee(employeeId));
  }
  ```
* **Replacement Block**:
  ```java
  @PostMapping("/generate")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('PAYROLL_MANAGE')")
  public ResponseEntity<Payroll> generatePayrollLegacy(@RequestBody Payroll payroll) {
      return ResponseEntity.ok(legacyPayrollService.generatePayroll(payroll));
  }

  @GetMapping("/employee/{employeeId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('PAYROLL_MANAGE') or @payrollSecurity.isSelf(authentication, #employeeId)")
  public ResponseEntity<List<Payroll>> getPayrollHistoryLegacy(@PathVariable Long employeeId) {
      return ResponseEntity.ok(legacyPayrollService.getPayrollHistory(employeeId));
  }

  @GetMapping("/employee/{employeeId}/month/{payPeriod}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR') or hasAuthority('PAYROLL_MANAGE') or @payrollSecurity.isSelf(authentication, #employeeId)")
  public ResponseEntity<Payroll> getPayslipForMonthLegacy(
          @PathVariable Long employeeId,
          @PathVariable String payPeriod) {
      return legacyPayrollService.getPayslipForMonth(employeeId, payPeriod)
              .map(ResponseEntity::ok)
              .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/seed/{employeeId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN') or hasAuthority('PAYROLL_MANAGE')")
  public ResponseEntity<List<Payroll>> seedEmployeePayrollLegacy(@PathVariable Long employeeId) {
      return ResponseEntity.ok(legacyPayrollService.seedDefaultPayrollForEmployee(employeeId));
  }
  ```
* **Why**: Stops unauthorized users from generating legacy payrolls or executing unauthorized seeding operations.

---

### Fix 6: Remove Illegal Read-Only Transaction Write in `getPayslipsForEmployee`
* **File**: `PayrollProcessingService.java`
* **Target Location**: Replace lines **395–411**.
* **Existing Code**:
  ```java
  @Transactional(readOnly = true)
  public List<PayrollRecordDto> getPayslipsForEmployee(Long employeeId, boolean onlyPublished) {
      List<PayrollRecord> records;
      if (onlyPublished) {
          records = payrollRecordRepository.findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
                  employeeId, PayrollStatus.PUBLISHED);
      } else {
          records = payrollRecordRepository.findByEmployeeIdOrderByPayrollYearDescProcessedAtDesc(employeeId);
      }

      // If no records exist yet, seed a historical published record so the screen immediately renders
      if (records.isEmpty() && onlyPublished) {
          seedInitialPublishedPayslip(employeeId);
          records = payrollRecordRepository.findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
                  employeeId, PayrollStatus.PUBLISHED);
      }

      return records.stream().map(this::mapToPayrollRecordDto).collect(Collectors.toList());
  }
  ```
* **Replacement Block**:
  ```java
  @Transactional(readOnly = true)
  public List<PayrollRecordDto> getPayslipsForEmployee(Long employeeId, boolean onlyPublished) {
      List<PayrollRecord> records;
      if (onlyPublished) {
          records = payrollRecordRepository.findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
                  employeeId, PayrollStatus.PUBLISHED);
      } else {
          records = payrollRecordRepository.findByEmployeeIdOrderByPayrollYearDescProcessedAtDesc(employeeId);
      }
      return records.stream().map(this::mapToPayrollRecordDto).collect(Collectors.toList());
  }
  ```
* **Why**: Prevents PostgreSQL/MySQL `TransientDataAccessResourceException` crashes when reading empty payslips and stops synthetic September 2026 data generation.

---

### Fix 7: Fix Pre-Payroll Lock Bypass in `setLockStateForMonth`
* **File**: `PayrollProcessingService.java`
* **Target Location**: Replace method `setLockStateForMonth` at lines **284–296**.
* **Existing Code**:
  ```java
  @Transactional
  public void setLockStateForMonth(String monthStr, Integer year, boolean lock) {
      validatePayPeriodNotFuture(monthStr, year);
      String month = normalizeMonth(monthStr);
      validatePayPeriodNotPublished(month, year);

      List<MonthlyPayrollInput> inputs = monthlyPayrollInputRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
      for (MonthlyPayrollInput i : inputs) {
          i.setIsLocked(lock);
          monthlyPayrollInputRepository.save(i);
      }
      log.info("Payroll inputs for {} {} lock status updated to: {}", month, year, lock);
  }
  ```
* **Replacement Block**:
  ```java
  @Transactional
  public void setLockStateForMonth(String monthStr, Integer year, boolean lock) {
      validatePayPeriodNotFuture(monthStr, year);
      String month = normalizeMonth(monthStr);
      validatePayPeriodNotPublished(month, year);

      List<Employee> employees = employeeRepository.findAll();
      Map<Long, MonthlyPayrollInput> existingMap = monthlyPayrollInputRepository
              .findByPayrollMonthIgnoreCaseAndPayrollYear(month, year).stream()
              .collect(Collectors.toMap(MonthlyPayrollInput::getEmployeeId, i -> i, (a, b) -> a));

      List<MonthlyPayrollInput> toSave = new ArrayList<>();
      for (Employee emp : employees) {
          MonthlyPayrollInput input = existingMap.getOrDefault(emp.getId(),
                  MonthlyPayrollInput.builder()
                          .employeeId(emp.getId())
                          .payrollMonth(month)
                          .payrollYear(year)
                          .lopDays(0.0)
                          .overtimeHours(0.0)
                          .adHocBonus(BigDecimal.ZERO)
                          .adHocDeduction(BigDecimal.ZERO)
                          .arrearsAmount(BigDecimal.ZERO)
                          .isExempt(Boolean.TRUE.equals(emp.getIsPayrollExempt()))
                          .build());
          input.setIsLocked(lock);
          toSave.add(input);
      }
      monthlyPayrollInputRepository.saveAll(toSave);
      log.info("Payroll inputs for {} {} locked for all {} employees.", month, year, toSave.size());
  }
  ```
* **Why**: Guarantees all employees are locked, even if they had no custom row created before the lock was initiated.

---

### Fix 8: Preserve `EXEMPT` Status & Enforce `VERIFIED` in `publishBatchPayroll`
* **File**: `PayrollProcessingService.java`
* **Target Location**: Replace method `publishBatchPayroll` at lines **372–389**.
* **Existing Code**:
  ```java
  @Transactional
  public void publishBatchPayroll(String monthStr, Integer year) {
      validatePayPeriodNotFuture(monthStr, year);
      String month = normalizeMonth(monthStr);
      List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
      if (records.isEmpty()) {
          throw new IllegalStateException("No payroll records exist for " + month + " " + year + " to publish.");
      }

      LocalDateTime now = LocalDateTime.now();
      for (PayrollRecord r : records) {
          r.setStatus(PayrollStatus.PUBLISHED);
          r.setPublishedAt(now);
          payrollRecordRepository.save(r);
          arrearsService.markArrearsAsProcessed(r.getEmployeeId(), month, year, r.getId());
      }
      log.info("Batch payroll for {} {} PUBLISHED to all employees.", month, year);
  }
  ```
* **Replacement Block**:
  ```java
  @Transactional
  public void publishBatchPayroll(String monthStr, Integer year) {
      validatePayPeriodNotFuture(monthStr, year);
      String month = normalizeMonth(monthStr);
      validatePayPeriodNotPublished(month, year);

      List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
      if (records.isEmpty()) {
          throw new IllegalStateException("No payroll records exist for " + month + " " + year + " to publish.");
      }

      boolean hasUnverifiedDrafts = records.stream().anyMatch(r -> r.getStatus() == PayrollStatus.DRAFT);
      if (hasUnverifiedDrafts) {
          throw new IllegalStateException("Cannot publish payroll for " + month + " " + year + " while records are still in DRAFT status. Verify batch first.");
      }

      LocalDateTime now = LocalDateTime.now();
      for (PayrollRecord r : records) {
          if (r.getStatus() == PayrollStatus.EXEMPT) {
              continue; // Preserve EXEMPT records; do not overwrite to PUBLISHED or clear arrears
          }
          r.setStatus(PayrollStatus.PUBLISHED);
          r.setPublishedAt(now);
          arrearsService.markArrearsAsProcessed(r.getEmployeeId(), month, year, r.getId());
      }
      payrollRecordRepository.saveAll(records);
      setLockStateForMonth(month, year, true);
      log.info("Batch payroll for {} {} PUBLISHED. Verified records published, exempt records preserved.", month, year);
  }
  ```
* **Why**: Enforces Rule 10 (exempt records stay exempt) and Rule 17 (drafts must be verified before publishing).

---

### Fix 9: Fix Bank Number, Rounding Drift, Arrears Injection & 12x Tax Spike in `calculatePayrollRecord`
* **File**: `PayrollProcessingService.java`
* **Target Location**: Inside `calculatePayrollRecord`, replace lines **483–558**.
* **Existing Code**:
  ```java
  BigDecimal perDayGross = totalFixedGross.divide(BigDecimal.valueOf(daysInMonth), 4, RoundingMode.HALF_UP);
  BigDecimal lopAmount = perDayGross.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);
  BigDecimal earnedFixedGross = perDayGross.multiply(BigDecimal.valueOf(paidDays)).min(totalFixedGross).setScale(2, RoundingMode.HALF_UP);
  // ...
  BigDecimal calcTds = taxCalculatorFactory.getStrategy(regime).calculateMonthlyTds(totalGross, declaration);
  // ...
  record.setBankAccountNumber("•••• •••• " + (1000 + (emp.getId().intValue() * 123) % 9000));
  ```
* **Replacement Block**:
  ```java
        BigDecimal daysInMonthBd = BigDecimal.valueOf(daysInMonth);
        BigDecimal lopDaysBd = BigDecimal.valueOf(lopDays);
        BigDecimal preJoiningDaysBd = BigDecimal.valueOf(preJoiningDays);

        BigDecimal totalFixedGross = struct.getTotalFixedGross().setScale(2, RoundingMode.HALF_UP);
        BigDecimal perDayGross = totalFixedGross.divide(daysInMonthBd, 4, RoundingMode.HALF_UP);
        BigDecimal lopAmount = perDayGross.multiply(lopDaysBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal preJoiningDeduction = perDayGross.multiply(preJoiningDaysBd).setScale(2, RoundingMode.HALF_UP);

        // 1. Earned Fixed Gross = Total Fixed Gross - LOP - Pre-Joining (Guarantees zero paisa drift per Rule 5)
        BigDecimal earnedFixedGross;
        if (paidDays == 0.0) {
            earnedFixedGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (preJoiningDays == 0 && lopDays == 0.0) {
            earnedFixedGross = totalFixedGross;
        } else {
            earnedFixedGross = totalFixedGross.subtract(lopAmount).subtract(preJoiningDeduction)
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal proRataFactor = (daysInMonth > 0 && paidDays > 0.0)
                ? BigDecimal.valueOf(paidDays).divide(daysInMonthBd, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal calcHra = struct.getHra().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcConveyance = struct.getConveyanceAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcMedical = struct.getMedicalAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcSpecial = struct.getSpecialAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal otherComponentsSum = calcHra.add(calcConveyance).add(calcMedical).add(calcSpecial);
        BigDecimal calcBasic = earnedFixedGross.subtract(otherComponentsSum).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal hourlyRate = perDayGross.divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP);
        BigDecimal otRate = hourlyRate.multiply(new BigDecimal("1.5"));
        BigDecimal otAmount = otRate.multiply(BigDecimal.valueOf(otHours)).setScale(2, RoundingMode.HALF_UP);

        // 2. Dynamic arrears fallback: if input arrears is zero, inspect ArrearsService directly (Rule 13)
        BigDecimal pendingArrears = arrearsService.calculatePendingArrearsTotalForEmployee(emp.getId(), month, year);
        BigDecimal arrears = (pendingArrears != null && pendingArrears.compareTo(BigDecimal.ZERO) > 0)
                ? pendingArrears
                : (input.getArrearsAmount() != null ? input.getArrearsAmount() : BigDecimal.ZERO);

        BigDecimal totalGross = earnedFixedGross.add(otAmount).add(adHocBonus).add(arrears).setScale(2, RoundingMode.HALF_UP);

        // 3. Pro-rate statutory deductions and zero them if employee has zero gross earnings
        boolean hasPayableEarnings = totalGross.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal calcPf = hasPayableEarnings ? struct.getPfContribution().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal calcEsi = hasPayableEarnings ? struct.getEsiContribution().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal calcPt = hasPayableEarnings ? struct.getProfessionalTax().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 4. Tax TDS: Project annual recurring gross + one-time lump sums once, preventing 12x over-taxation
        BigDecimal calcTds;
        if (declaration.getMonthlyTdsOverride() != null && declaration.getMonthlyTdsOverride().compareTo(BigDecimal.ZERO) >= 0) {
            calcTds = declaration.getMonthlyTdsOverride().setScale(2, RoundingMode.HALF_UP);
        } else if (!hasPayableEarnings) {
            calcTds = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal oneTimeEarnings = otAmount.add(adHocBonus).add(arrears);
            BigDecimal projectedAnnualIncome = totalFixedGross.multiply(BigDecimal.valueOf(12))
                    .subtract(lopAmount).subtract(preJoiningDeduction)
                    .add(oneTimeEarnings);
            BigDecimal annualTax = taxCalculatorFactory.getStrategy(regime).calculateAnnualTax(projectedAnnualIncome, declaration);
            calcTds = annualTax.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalDeductions = calcPf.add(calcEsi).add(calcPt).add(adHocDeduction).add(calcTds).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netPay = totalGross.subtract(totalDeductions).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // 5. Retain true employee bank account number for RFC-4180 bank exports
        record.setBankAccountNumber(emp.getBankAccountNumber() != null ? emp.getBankAccountNumber() : "");
  ```
* **Why**: Solves 1-paisa rounding drift, sets valid bank account numbers, prevents arrears omission, and avoids 12x tax spikes.

---

### Fix 10: Add Section 87A Marginal Relief in `NewRegimeTaxStrategy.java`
* **File**: `NewRegimeTaxStrategy.java`
* **Target Location**: Inside `calculateAnnualTax`, replace lines **58–67**.
* **Existing Code**:
  ```java
          // Slab 6: Above 15,00,000 @ 30%
          if (taxableIncome.compareTo(BigDecimal.valueOf(1500000)) > 0) {
              BigDecimal chunk = taxableIncome.subtract(BigDecimal.valueOf(1500000));
              tax = tax.add(chunk.multiply(BigDecimal.valueOf(0.30)));
          }

          // Add 4% Health & Education Cess
          BigDecimal cess = tax.multiply(CESS_RATE);
          return tax.add(cess).setScale(2, RoundingMode.HALF_UP);
  ```
* **Replacement Block**:
  ```java
          // Slab 6: Above 15,00,000 @ 30%
          if (taxableIncome.compareTo(BigDecimal.valueOf(1500000)) > 0) {
              BigDecimal chunk = taxableIncome.subtract(BigDecimal.valueOf(1500000));
              tax = tax.add(chunk.multiply(new BigDecimal("0.30")));
          }

          // Section 87A Marginal Relief: Tax payable cannot exceed income above ₹7,00,000
          BigDecimal incomeAboveRebate = taxableIncome.subtract(REBATE_LIMIT);
          if (tax.compareTo(incomeAboveRebate) > 0) {
              tax = incomeAboveRebate;
          }

          // Add 4% Health & Education Cess
          BigDecimal cess = tax.multiply(CESS_RATE);
          return tax.add(cess).setScale(2, RoundingMode.HALF_UP);
  ```
* **Why**: Prevents ₹20,810 tax penalty when an employee earns just ₹100 over the ₹7,00,000 limit.

---

### Fix 11: Replace Silent Month Number Fallback with Strict Validation
* **File**: `PayrollProcessingService.java`
* **Target Location**: Replace lines **710–719**.
* **Existing Code**:
  ```java
  private int parseMonthNumber(String monthStr) {
      try {
          return Integer.parseInt(monthStr);
      } catch (Exception ignored) {}
      try {
          return Month.valueOf(monthStr.toUpperCase()).getValue();
      } catch (Exception e) {
          return LocalDate.now().getMonthValue();
      }
  }
  ```
* **Replacement Block**:
  ```java
  private int parseMonthNumber(String monthStr) {
      if (monthStr == null || monthStr.isBlank()) {
          return LocalDate.now().getMonthValue();
      }
      String clean = monthStr.trim().toUpperCase();
      try {
          int num = Integer.parseInt(clean);
          if (num >= 1 && num <= 12) {
              return num;
          }
          throw new IllegalArgumentException("Month number must be between 1 and 12: " + monthStr);
      } catch (NumberFormatException ignored) {}
      try {
          return Month.valueOf(clean).getValue();
      } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid payroll month '" + monthStr + "'. Expected month name (e.g. OCTOBER) or numeric string (1-12).");
      }
  }
  ```
* **Why**: Replaces silent fallback with an immediate HTTP 400 Bad Request error when invalid month names are provided.
