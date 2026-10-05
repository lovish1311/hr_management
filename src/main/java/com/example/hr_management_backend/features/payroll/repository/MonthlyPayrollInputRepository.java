package com.example.hr_management_backend.features.payroll.repository;

import com.example.hr_management_backend.features.payroll.model.MonthlyPayrollInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyPayrollInputRepository extends JpaRepository<MonthlyPayrollInput, Long> {
    Optional<MonthlyPayrollInput> findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(
            Long employeeId, String payrollMonth, Integer payrollYear);

    List<MonthlyPayrollInput> findByPayrollMonthIgnoreCaseAndPayrollYear(
            String payrollMonth, Integer payrollYear);

    List<MonthlyPayrollInput> findByEmployeeIdOrderByPayrollYearDesc(Long employeeId);
}
