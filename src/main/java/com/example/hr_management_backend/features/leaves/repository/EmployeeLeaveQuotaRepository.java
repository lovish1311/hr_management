package com.example.hr_management_backend.features.leaves.repository;

import com.example.hr_management_backend.features.leaves.model.EmployeeLeaveQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeLeaveQuotaRepository extends JpaRepository<EmployeeLeaveQuota, Long> {
    List<EmployeeLeaveQuota> findByEmployeeId(Long employeeId);
    List<EmployeeLeaveQuota> findByEmployeeIdAndYear(Long employeeId, Integer year);
    Optional<EmployeeLeaveQuota> findByEmployeeIdAndYearAndLeaveType(Long employeeId, Integer year, String leaveType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT eq FROM EmployeeLeaveQuota eq WHERE eq.employeeId = :employeeId AND eq.year = :year AND eq.leaveType = :leaveType")
    Optional<EmployeeLeaveQuota> findByEmployeeIdAndYearAndLeaveTypeForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("year") Integer year,
            @Param("leaveType") String leaveType);
}
