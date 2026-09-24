package com.example.hr_management_backend.features.payroll.controller;

import com.example.hr_management_backend.features.payroll.model.Payroll;
import com.example.hr_management_backend.features.payroll.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/payroll", "/api/payroll"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @PostMapping("/generate")
    public ResponseEntity<Payroll> generatePayroll(@RequestBody Payroll payroll) {
        return ResponseEntity.ok(payrollService.generatePayroll(payroll));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Payroll>> getPayrollHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.getPayrollHistory(employeeId));
    }

    @GetMapping("/employee/{employeeId}/month/{payPeriod}")
    public ResponseEntity<Payroll> getPayslipForMonth(
            @PathVariable Long employeeId,
            @PathVariable String payPeriod) {
        return payrollService.getPayslipForMonth(employeeId, payPeriod)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/seed/{employeeId}")
    public ResponseEntity<List<Payroll>> seedEmployeePayroll(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.seedDefaultPayrollForEmployee(employeeId));
    }
}
