package com.example.hr_management_backend.features.payroll.repository;

import com.example.hr_management_backend.features.payroll.model.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {
    Optional<SalaryStructure> findByEmployeeIdAndIsActiveTrue(Long employeeId);
    List<SalaryStructure> findByIsActiveTrue();
    List<SalaryStructure> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
}
