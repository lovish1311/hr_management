package com.example.hr_management_backend.features.payroll;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.payroll.dto.*;
import com.example.hr_management_backend.features.payroll.model.PayrollStatus;
import com.example.hr_management_backend.features.payroll.service.PayrollProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.example.hr_management_backend.features.payroll.banking.BankPayoutExportService;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PayrollProcessingServiceTest {

    @Autowired
    private PayrollProcessingService payrollProcessingService;

    @Autowired
    private BankPayoutExportService bankPayoutExportService;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        testEmployee = employeeRepository.save(Employee.builder()
                .firstName("Aarav")
                .lastName("Verma")
                .email("aarav.payroll.test@company.com")
                .employeeCode("EMP-PYR-01")
                .department("Engineering")
                .designation("Senior Engineer")
                .role("EMPLOYEE")
                .isAttendanceTracked(true)
                .build());
    }

    @Test
    @DisplayName("Test 1: Save & retrieve SalaryStructure with high precision BigDecimal")
    void testSalaryStructureLifecycle() {
        SalaryStructureDto dto = SalaryStructureDto.builder()
                .employeeId(testEmployee.getId())
                .basicPay(BigDecimal.valueOf(50000.00))
                .hra(BigDecimal.valueOf(20000.00))
                .conveyanceAllowance(BigDecimal.valueOf(5000.00))
                .medicalAllowance(BigDecimal.valueOf(2500.00))
                .specialAllowance(BigDecimal.valueOf(2500.00))
                .pfContribution(BigDecimal.valueOf(1800.00))
                .esiContribution(BigDecimal.ZERO)
                .professionalTax(BigDecimal.valueOf(200.00))
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .isActive(true)
                .build();

        SalaryStructureDto saved = payrollProcessingService.saveSalaryStructure(dto);
        assertNotNull(saved.getId());
        assertEquals(BigDecimal.valueOf(80000.00).setScale(2), saved.getTotalFixedGross().setScale(2));
        assertEquals(BigDecimal.valueOf(2000.00).setScale(2), saved.getTotalStatutoryDeductions().setScale(2));
        assertEquals(BigDecimal.valueOf(78000.00).setScale(2), saved.getNetCtc().setScale(2));

        SalaryStructureDto retrieved = payrollProcessingService.getSalaryStructure(testEmployee.getId());
        assertEquals(saved.getId(), retrieved.getId());
        assertEquals(BigDecimal.valueOf(50000.00).setScale(2), retrieved.getBasicPay().setScale(2));
    }

    @Test
    @DisplayName("Test 2: MonthlyPayrollInput manual adjustments & lock flow")
    void testMonthlyPayrollInputAdjustments() {
        MonthlyPayrollInputDto inputDto = MonthlyPayrollInputDto.builder()
                .employeeId(testEmployee.getId())
                .payrollMonth("OCTOBER")
                .payrollYear(2026)
                .lopDays(2.0)
                .overtimeHours(4.0)
                .adHocBonus(BigDecimal.valueOf(3500.00))
                .adHocDeduction(BigDecimal.valueOf(500.00))
                .notes("Festival advance adjustment")
                .build();

        MonthlyPayrollInputDto savedInput = payrollProcessingService.saveMonthlyPayrollInput(inputDto);
        assertEquals(2.0, savedInput.getLopDays());
        assertEquals(4.0, savedInput.getOvertimeHours());
        assertEquals(BigDecimal.valueOf(3500.00).setScale(2), savedInput.getAdHocBonus().setScale(2));
        assertFalse(savedInput.getIsLocked());

        // Lock inputs for October 2026
        payrollProcessingService.setLockStateForMonth("OCTOBER", 2026, true);

        // Attempting to modify locked inputs must throw IllegalStateException
        inputDto.setLopDays(3.0);
        assertThrows(IllegalStateException.class, () -> payrollProcessingService.saveMonthlyPayrollInput(inputDto));

        // Unlock
        payrollProcessingService.setLockStateForMonth("OCTOBER", 2026, false);
        inputDto.setLopDays(1.5);
        MonthlyPayrollInputDto unlockedSaved = payrollProcessingService.saveMonthlyPayrollInput(inputDto);
        assertEquals(1.5, unlockedSaved.getLopDays());
    }

    @Test
    @DisplayName("Test 3: Batch Payroll Math Calculation with Pro-rata LOP, Overtime, and Deductions")
    void testCoreCalculationEngine() {
        // Setup salary structure: 62,000 Fixed Gross
        payrollProcessingService.saveSalaryStructure(SalaryStructureDto.builder()
                .employeeId(testEmployee.getId())
                .basicPay(BigDecimal.valueOf(31000.00))
                .hra(BigDecimal.valueOf(15500.00))
                .conveyanceAllowance(BigDecimal.valueOf(3500.00))
                .medicalAllowance(BigDecimal.valueOf(6000.00))
                .specialAllowance(BigDecimal.valueOf(6000.00))
                .pfContribution(BigDecimal.valueOf(1800.00))
                .esiContribution(BigDecimal.ZERO)
                .professionalTax(BigDecimal.valueOf(200.00))
                .effectiveDate(LocalDate.of(2026, 10, 1))
                .isActive(true)
                .build());

        // Setup pre-payroll input: 1 LOP day in October (31 days)
        payrollProcessingService.saveMonthlyPayrollInput(MonthlyPayrollInputDto.builder()
                .employeeId(testEmployee.getId())
                .payrollMonth("OCTOBER")
                .payrollYear(2026)
                .lopDays(1.0)
                .overtimeHours(8.0)
                .adHocBonus(BigDecimal.valueOf(1000.00))
                .adHocDeduction(BigDecimal.valueOf(0.00))
                .build());

        // Execute Batch Calculation
        List<PayrollRecordDto> results = payrollProcessingService.processBatchPayroll("OCTOBER", 2026);
        assertFalse(results.isEmpty());

        PayrollRecordDto record = results.stream()
                .filter(r -> r.getEmployeeId().equals(testEmployee.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(PayrollStatus.DRAFT, record.getStatus());
        assertEquals(31, record.getTotalDaysInMonth());
        assertEquals(30.0, record.getPaidDays());
        assertEquals(1.0, record.getLopDays());

        // Per-Day Gross = 62000 / 31 = 2000.00
        // LOP Deduction Amount = 2000.00 * 1 = 2000.00
        assertEquals(BigDecimal.valueOf(2000.00).setScale(2), record.getLopDeductionAmount().setScale(2));

        // Overtime: (2000 / 8) * 1.5 * 8 = 375.00/hr * 8 = 3000.00
        assertEquals(BigDecimal.valueOf(3000.00).setScale(2), record.getOvertimeAmount().setScale(2));

        // Total Gross = (62000 - 2000) + 3000 + 1000 = 64000.00
        assertEquals(BigDecimal.valueOf(64000.00).setScale(2), record.getTotalGrossPay().setScale(2));

        // Total Deductions = PF (1800) + PT (200) = 2000.00
        assertEquals(BigDecimal.valueOf(2000.00).setScale(2), record.getTotalDeductions().setScale(2));

        // Net Pay = 64000 - 2000 = 62000.00
        assertEquals(BigDecimal.valueOf(62000.00).setScale(2), record.getNetPay().setScale(2));

        // Verify status transition
        payrollProcessingService.verifyBatchPayroll("OCTOBER", 2026);
        PayrollRecordDto verified = payrollProcessingService.getPayslipById(record.getId());
        assertEquals(PayrollStatus.VERIFIED, verified.getStatus());

        // Publish status transition
        payrollProcessingService.publishBatchPayroll("OCTOBER", 2026);
        PayrollRecordDto published = payrollProcessingService.getPayslipById(record.getId());
        assertEquals(PayrollStatus.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());

        // Employee ESS lookup returns this published record
        List<PayrollRecordDto> employeePayslips = payrollProcessingService.getPayslipsForEmployee(testEmployee.getId(), true);
        assertTrue(employeePayslips.stream().anyMatch(p -> p.getId().equals(record.getId())));
    }

    @Test
    @DisplayName("Test 4: Boundary Validation - Future Pay Periods Must Be Rejected")
    void testFutureMonthRejectionSafeguard() {
        // Current local year-month is October 2026 (or whatever system date is)
        // Processing next year or month beyond current month must be rejected
        int futureYear = LocalDate.now().getYear() + 1;
        assertThrows(IllegalArgumentException.class, () ->
                payrollProcessingService.processBatchPayroll("JANUARY", futureYear)
        );

        assertThrows(IllegalArgumentException.class, () ->
                payrollProcessingService.setLockStateForMonth("DECEMBER", futureYear, true)
        );

        assertThrows(IllegalArgumentException.class, () ->
                payrollProcessingService.syncMonthlyPayrollInputsFromAttendance("JUNE", futureYear)
        );
    }

    @Test
    @DisplayName("Test 5: Immutability Freeze - Modifications Prohibited After Publishing")
    void testPostPublishImmutabilityFreeze() {
        // Setup & calculate
        payrollProcessingService.saveSalaryStructure(SalaryStructureDto.builder()
                .employeeId(testEmployee.getId())
                .basicPay(BigDecimal.valueOf(40000.00))
                .hra(BigDecimal.valueOf(20000.00))
                .pfContribution(BigDecimal.valueOf(1800.00))
                .professionalTax(BigDecimal.valueOf(200.00))
                .isActive(true)
                .build());

        payrollProcessingService.processBatchPayroll("SEPTEMBER", 2026);
        payrollProcessingService.verifyBatchPayroll("SEPTEMBER", 2026);
        payrollProcessingService.publishBatchPayroll("SEPTEMBER", 2026);

        // Attempting to modify inputs for a published period must fail
        MonthlyPayrollInputDto editAttempt = MonthlyPayrollInputDto.builder()
                .employeeId(testEmployee.getId())
                .payrollMonth("SEPTEMBER")
                .payrollYear(2026)
                .lopDays(5.0)
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                payrollProcessingService.saveMonthlyPayrollInput(editAttempt)
        );
        assertTrue(ex.getMessage().contains("PUBLISHED"));

        // Attempting to sync or lock/unlock published month must also fail
        assertThrows(IllegalStateException.class, () ->
                payrollProcessingService.syncMonthlyPayrollInputsFromAttendance("SEPTEMBER", 2026)
        );
        assertThrows(IllegalStateException.class, () ->
                payrollProcessingService.setLockStateForMonth("SEPTEMBER", 2026, false)
        );
    }

    @Test
    @DisplayName("Test 6: Exemption Feature & Corporate Bank Export Exclusion Safeguard")
    void testExemptEmployeeHandlingAndBankExclusion() {
        // Create an exempt employee (e.g. stipend / direct board retainer)
        Employee exemptEmployee = employeeRepository.save(Employee.builder()
                .firstName("Kavita")
                .lastName("Sharma")
                .email("kavita.exempt." + UUID.randomUUID() + "@company.com")
                .employeeCode("EMP-EXEMPT-01")
                .department("Advisory")
                .designation("Senior Advisor")
                .role("EMPLOYEE")
                .isPayrollExempt(true)
                .build());

        // Process batch payroll
        List<PayrollRecordDto> records = payrollProcessingService.processBatchPayroll("OCTOBER", 2026);

        PayrollRecordDto exemptRecord = records.stream()
                .filter(r -> r.getEmployeeId().equals(exemptEmployee.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(PayrollStatus.EXEMPT, exemptRecord.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), exemptRecord.getNetPay().setScale(2));
        assertEquals(BigDecimal.ZERO.setScale(2), exemptRecord.getTotalGrossPay().setScale(2));

        // Generate corporate bank payout CSV
        byte[] csvBytes = bankPayoutExportService.generateStandardBankPayoutCsv("OCTOBER", 2026);
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

        // Exempt employee must NOT be present in bank payout CSV
        assertFalse(csvContent.contains("EMP-EXEMPT-01"), "Exempt employee must not appear in banking payout file");
        assertFalse(csvContent.contains("Kavita Sharma"), "Exempt employee name must not appear in banking payout file");
    }

    @Test
    @DisplayName("Test 7: Mid-Month Joiner Proration Math")
    void testMidMonthJoinerProration() {
        // Employee joins on October 16th, 2026 (Month has 31 days)
        Employee midMonthJoiner = employeeRepository.save(Employee.builder()
                .firstName("Rohan")
                .lastName("Mehta")
                .email("rohan.joiner." + UUID.randomUUID() + "@company.com")
                .employeeCode("EMP-JOIN-16")
                .department("Product")
                .designation("Product Manager")
                .role("EMPLOYEE")
                .joiningDate(LocalDate.of(2026, 10, 16))
                .isAttendanceTracked(true)
                .build());

        // Fixed gross = 62,000 (Basic 31,000 + HRA 15,500 + Conveyance 3,500 + Medical 6,000 + Special 6,000)
        payrollProcessingService.saveSalaryStructure(SalaryStructureDto.builder()
                .employeeId(midMonthJoiner.getId())
                .basicPay(BigDecimal.valueOf(31000.00))
                .hra(BigDecimal.valueOf(15500.00))
                .conveyanceAllowance(BigDecimal.valueOf(3500.00))
                .medicalAllowance(BigDecimal.valueOf(6000.00))
                .specialAllowance(BigDecimal.valueOf(6000.00))
                .pfContribution(BigDecimal.valueOf(1800.00))
                .esiContribution(BigDecimal.ZERO)
                .professionalTax(BigDecimal.valueOf(200.00))
                .isActive(true)
                .build());

        List<PayrollRecordDto> records = payrollProcessingService.processBatchPayroll("OCTOBER", 2026);
        PayrollRecordDto joinerRecord = records.stream()
                .filter(r -> r.getEmployeeId().equals(midMonthJoiner.getId()))
                .findFirst()
                .orElseThrow();

        // 31 days in October. Pre-joining days = 15. Paid days = 16.
        assertEquals(31, joinerRecord.getTotalDaysInMonth());
        assertEquals(16.0, joinerRecord.getPaidDays());

        // Per-day gross = 62,000 / 31 = 2000.00
        // Earned fixed gross = 2000.00 * 16 = 32,000.00
        assertEquals(BigDecimal.valueOf(32000.00).setScale(2), joinerRecord.getTotalGrossPay().setScale(2));

        // Deductions = PF 1800 + PT 200 = 2000.00
        assertEquals(BigDecimal.valueOf(2000.00).setScale(2), joinerRecord.getTotalDeductions().setScale(2));

        // Net pay = 32000 - 2000 = 30,000.00
        assertEquals(BigDecimal.valueOf(30000.00).setScale(2), joinerRecord.getNetPay().setScale(2));
    }
}

