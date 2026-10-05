package com.example.hr_management_backend.features.payroll.controller;

import com.example.hr_management_backend.features.payroll.dto.*;
import com.example.hr_management_backend.features.payroll.model.Payroll;
import com.example.hr_management_backend.features.payroll.service.PayrollProcessingService;
import com.example.hr_management_backend.features.payroll.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/payroll", "/api/v1/payroll"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollProcessingService processingService;
    private final PayrollService legacyPayrollService;

    // =========================================================================
    // PHASE 1: PRE-PAYROLL (ADMIN CONFIGURATION & VARIABLES)
    // =========================================================================

    @PostMapping("/structure")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<SalaryStructureDto> saveSalaryStructure(@RequestBody SalaryStructureDto dto) {
        return ResponseEntity.ok(processingService.saveSalaryStructure(dto));
    }

    @GetMapping("/structure/{employeeId}")
    public ResponseEntity<SalaryStructureDto> getSalaryStructure(@PathVariable Long employeeId) {
        return ResponseEntity.ok(processingService.getSalaryStructure(employeeId));
    }

    @GetMapping("/structure")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<SalaryStructureDto>> getAllActiveSalaryStructures() {
        return ResponseEntity.ok(processingService.getAllActiveSalaryStructures());
    }

    @PostMapping("/inputs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<MonthlyPayrollInputDto> saveMonthlyPayrollInput(@RequestBody MonthlyPayrollInputDto dto) {
        return ResponseEntity.ok(processingService.saveMonthlyPayrollInput(dto));
    }

    @GetMapping("/inputs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<MonthlyPayrollInputDto>> getMonthlyPayrollInputs(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(processingService.getMonthlyPayrollInputs(m, y));
    }

    @PostMapping("/inputs/sync")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<MonthlyPayrollInputDto>> syncMonthlyPayrollInputs(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(processingService.syncMonthlyPayrollInputsFromAttendance(m, y));
    }

    @PostMapping("/inputs/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Map<String, String>> lockInputs(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        processingService.setLockStateForMonth(m, y, true);
        return ResponseEntity.ok(Map.of("message", "Inputs locked for " + m + " " + y));
    }

    @PostMapping("/inputs/unlock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Map<String, String>> unlockInputs(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        processingService.setLockStateForMonth(m, y, false);
        return ResponseEntity.ok(Map.of("message", "Inputs unlocked for " + m + " " + y));
    }

    // =========================================================================
    // PHASE 2: CORE CALCULATION ENGINE
    // =========================================================================

    @PostMapping("/process")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<PayrollRecordDto>> processPayroll(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(processingService.processBatchPayroll(m, y));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Map<String, String>> verifyPayroll(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        processingService.verifyBatchPayroll(m, y);
        return ResponseEntity.ok(Map.of("message", "Payroll verified for " + m + " " + y));
    }

    // =========================================================================
    // PHASE 3: POST-PAYROLL & PUBLISHING (ESS & ADMIN)
    // =========================================================================

    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Map<String, String>> publishPayroll(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        processingService.publishBatchPayroll(m, y);
        return ResponseEntity.ok(Map.of("message", "Payslips published for " + m + " " + y));
    }

    @GetMapping("/payslips")
    public ResponseEntity<List<PayrollRecordDto>> getPayslips(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "true") boolean onlyPublished) {
        return ResponseEntity.ok(processingService.getPayslipsForEmployee(employeeId, onlyPublished));
    }

    @GetMapping("/payslip/{recordId}")
    public ResponseEntity<PayrollRecordDto> getPayslipById(@PathVariable UUID recordId) {
        return ResponseEntity.ok(processingService.getPayslipById(recordId));
    }

    @GetMapping("/records")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<PayrollRecordDto>> getPayrollRecordsForMonth(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(processingService.getPayrollRecordsForMonth(m, y));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<PayrollSummaryDto> getPayrollSummary(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        String m = (month != null && !month.isBlank()) ? month : LocalDate.now().getMonth().name();
        int y = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(processingService.getPayrollSummary(m, y));
    }

    // =========================================================================
    // BACKWARD COMPATIBILITY ENDPOINTS (LEGACY)
    // =========================================================================

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
}
