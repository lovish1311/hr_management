package com.example.hr_management_backend.features.payroll.service;

import com.example.hr_management_backend.features.payroll.model.Payroll;
import com.example.hr_management_backend.features.payroll.repository.PayrollRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PayrollService {

    private final PayrollRepository payrollRepository;

    @Autowired
    public PayrollService(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    public Payroll generatePayroll(Payroll payroll) {
        payroll.setId(null);
        double net = payroll.getBaseSalary() + payroll.getBonuses() - payroll.getDeductions();
        payroll.setNetSalary(net);
        return payrollRepository.save(payroll);
    }

    public List<Payroll> getPayrollHistory(Long employeeId) {
        return payrollRepository.findByEmployeeId(employeeId);
    }
}
