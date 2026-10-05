package com.example.hr_management_backend.features.payroll.repository;

import com.example.hr_management_backend.features.payroll.model.PayrollRecord;
import com.example.hr_management_backend.features.payroll.model.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, UUID> {
    Optional<PayrollRecord> findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(
            Long employeeId, String payrollMonth, Integer payrollYear);

    List<PayrollRecord> findByPayrollMonthIgnoreCaseAndPayrollYear(
            String payrollMonth, Integer payrollYear);

    List<PayrollRecord> findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
            Long employeeId, PayrollStatus status);

    List<PayrollRecord> findByEmployeeIdOrderByPayrollYearDescProcessedAtDesc(Long employeeId);
}
