package com.example.hr_management_backend.features.leaves.repository;

import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);

    /**
     * Pessimistic Write Lock query to prevent concurrent leave balance deduction race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.employeeId = :employeeId AND lb.year = :year")
    Optional<LeaveBalance> findByEmployeeIdAndYearForUpdate(@Param("employeeId") Long employeeId, @Param("year") Integer year);
}
