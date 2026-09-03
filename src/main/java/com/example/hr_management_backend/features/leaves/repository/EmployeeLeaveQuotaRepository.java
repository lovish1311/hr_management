package com.example.hr_management_backend.features.leaves.repository;

import com.example.hr_management_backend.features.leaves.model.EmployeeLeaveQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveQuotaRepository extends JpaRepository<EmployeeLeaveQuota, Long> {
    List<EmployeeLeaveQuota> findByEmployeeIdAndYear(Long employeeId, Integer year);
    Optional<EmployeeLeaveQuota> findByEmployeeIdAndYearAndLeaveType(Long employeeId, Integer year, String leaveType);
}
