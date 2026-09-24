package com.example.hr_management_backend.features.payroll.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.payroll.model.Payroll;
import com.example.hr_management_backend.features.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public Payroll generatePayroll(Payroll payroll) {
        payroll.setId(null);

        // Fetch employee details if present
        if (payroll.getEmployeeId() != null) {
            employeeRepository.findById(payroll.getEmployeeId()).ifPresent(emp -> {
                if (payroll.getEmployeeName() == null || payroll.getEmployeeName().isEmpty()) {
                    payroll.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
                }
                if (payroll.getEmployeeCode() == null || payroll.getEmployeeCode().isEmpty()) {
                    payroll.setEmployeeCode(emp.getEmployeeCode());
                }
                if (payroll.getDesignation() == null || payroll.getDesignation().isEmpty()) {
                    payroll.setDesignation(emp.getDesignation());
                }
                if (payroll.getDepartment() == null || payroll.getDepartment().isEmpty()) {
                    payroll.setDepartment(emp.getDepartment());
                }
            });
        }

        computePayrollTotals(payroll);
        return payrollRepository.save(payroll);
    }

    public void computePayrollTotals(Payroll payroll) {
        double basic = payroll.getBasicSalary() != null ? payroll.getBasicSalary() : 0.0;
        double hra = payroll.getHra() != null ? payroll.getHra() : 0.0;
        double special = payroll.getSpecialAllowance() != null ? payroll.getSpecialAllowance() : 0.0;
        double bonuses = payroll.getBonuses() != null ? payroll.getBonuses() : 0.0;
        double gross = payroll.getGrossSalary() != null ? payroll.getGrossSalary() : 0.0;

        // Default base salary breakdown if only basicSalary is set
        if (basic <= 0 && gross > 0) {
            basic = gross * 0.50;
            hra = gross * 0.30;
            special = gross * 0.20;
            payroll.setBasicSalary(basic);
            payroll.setHra(hra);
            payroll.setSpecialAllowance(special);
        } else if (basic > 0 && hra <= 0) {
            hra = basic * 0.40;
            special = basic * 0.20;
            payroll.setHra(hra);
            payroll.setSpecialAllowance(special);
        }

        gross = basic + hra + special + bonuses;
        payroll.setBasicSalary(basic);
        payroll.setHra(hra);
        payroll.setSpecialAllowance(special);
        payroll.setBonuses(bonuses);
        payroll.setGrossSalary(gross);

        double pf = payroll.getProvidentFund() != null ? payroll.getProvidentFund() : 0.0;
        if (pf <= 0 && basic > 0) {
            pf = Math.min(basic * 0.12, 1800.0);
        }
        payroll.setProvidentFund(pf);

        double pt = payroll.getProfessionalTax() != null ? payroll.getProfessionalTax() : 200.0;
        payroll.setProfessionalTax(pt);

        double tax = payroll.getTaxDeduction() != null ? payroll.getTaxDeduction() : 0.0;
        payroll.setTaxDeduction(tax);

        double lop = payroll.getUnpaidLeaveDeduction() != null ? payroll.getUnpaidLeaveDeduction() : 0.0;
        payroll.setUnpaidLeaveDeduction(lop);

        double other = payroll.getOtherDeductions() != null ? payroll.getOtherDeductions() : 0.0;
        payroll.setOtherDeductions(other);

        double totalDeductions = pf + pt + tax + lop + other;
        payroll.setTotalDeductions(totalDeductions);
        payroll.setNetSalary(Math.max(0, gross - totalDeductions));

        if (payroll.getPaymentDate() == null) {
            payroll.setPaymentDate(LocalDate.now());
        }
        if (payroll.getPaymentStatus() == null) {
            payroll.setPaymentStatus("PAID");
        }
        if (payroll.getTotalWorkingDays() == null) {
            payroll.setTotalWorkingDays(30);
        }
        if (payroll.getDaysWorked() == null) {
            payroll.setDaysWorked(30);
        }
        if (payroll.getUnpaidDays() == null) {
            payroll.setUnpaidDays(0);
        }
        if (payroll.getPaymentMethod() == null) {
            payroll.setPaymentMethod("Bank Transfer (NEFT)");
        }
        if (payroll.getBankAccountNumber() == null) {
            payroll.setBankAccountNumber("•••• •••• 4589");
        }
    }

    @Transactional
    public List<Payroll> getPayrollHistory(Long employeeId) {
        List<Payroll> list = payrollRepository.findByEmployeeIdOrderByPayPeriodDesc(employeeId);
        if (list.isEmpty()) {
            list = seedDefaultPayrollForEmployee(employeeId);
        }
        return list;
    }

    @Transactional
    public Optional<Payroll> getPayslipForMonth(Long employeeId, String payPeriod) {
        Optional<Payroll> opt = payrollRepository.findByEmployeeIdAndPayPeriod(employeeId, payPeriod);
        if (opt.isPresent()) {
            return opt;
        }
        // If not present, check full history (which triggers seed if empty)
        List<Payroll> history = getPayrollHistory(employeeId);
        return history.stream().filter(p -> p.getPayPeriod().equalsIgnoreCase(payPeriod)).findFirst();
    }

    @Transactional
    public List<Payroll> seedDefaultPayrollForEmployee(Long employeeId) {
        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        String name = empOpt.map(e -> e.getFirstName() + " " + e.getLastName()).orElse("Employee #" + employeeId);
        String code = empOpt.map(Employee::getEmployeeCode).orElse("EMP-" + employeeId);
        String desig = empOpt.map(Employee::getDesignation).orElse("Software Engineer");
        String dept = empOpt.map(Employee::getDepartment).orElse("Engineering");

        String[] periods = {"2026-06", "2026-05", "2026-04", "2026-03", "2026-02", "2026-01"};
        List<Payroll> seeded = new ArrayList<>();

        for (int i = 0; i < periods.length; i++) {
            String period = periods[i];
            Payroll p = Payroll.builder()
                    .employeeId(employeeId)
                    .employeeName(name)
                    .employeeCode(code)
                    .designation(desig)
                    .department(dept)
                    .payPeriod(period)
                    .basicSalary(25000.0)
                    .hra(10000.0)
                    .specialAllowance(5000.0)
                    .bonuses(i == 0 ? 2000.0 : 0.0)
                    .providentFund(1800.0)
                    .professionalTax(200.0)
                    .taxDeduction(1000.0)
                    .unpaidLeaveDeduction(0.0)
                    .paymentStatus("PAID")
                    .paymentDate(LocalDate.of(2026, 6 - i, 28))
                    .totalWorkingDays(30)
                    .daysWorked(30)
                    .unpaidDays(0)
                    .paymentMethod("Bank Transfer (NEFT)")
                    .bankAccountNumber("•••• •••• 9842")
                    .build();

            computePayrollTotals(p);
            seeded.add(payrollRepository.save(p));
        }
        log.info("Seeded 6-month historical payroll for employee {}: {}", employeeId, name);
        return seeded;
    }
}
