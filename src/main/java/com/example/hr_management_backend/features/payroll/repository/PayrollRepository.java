package com.example.hr_management_backend.features.payroll.repository;

import com.example.hr_management_backend.features.payroll.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByEmployeeId(Long employeeId);
}
