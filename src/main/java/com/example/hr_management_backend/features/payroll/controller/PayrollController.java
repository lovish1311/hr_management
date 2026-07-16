package com.example.hr_management_backend.features.payroll.controller;

import com.example.hr_management_backend.features.payroll.model.Payroll;
import com.example.hr_management_backend.features.payroll.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    @Autowired
    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Payroll> generatePayroll(@RequestBody Payroll payroll) {
        return ResponseEntity.ok(payrollService.generatePayroll(payroll));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Payroll>> getPayrollHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.getPayrollHistory(employeeId));
    }
}
