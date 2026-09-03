package com.example.hr_management_backend.features.leaves.repository;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' AND lr.employeeId IN " +
           "(SELECT e.id FROM Employee e WHERE e.manager.id = :managerId) ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findPendingForManager(@Param("managerId") Long managerId);

    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.employeeId = :employeeId " +
           "AND lr.status = 'APPROVED' AND :targetDate BETWEEN lr.startDate AND lr.endDate " +
           "AND UPPER(lr.leaveType) NOT LIKE '%SHORT%' AND UPPER(lr.leaveType) NOT LIKE '%EARLY%' AND UPPER(lr.leaveType) NOT LIKE '%LATE%'")
    boolean isEmployeeOnApprovedLeave(@Param("employeeId") Long employeeId, @Param("targetDate") LocalDate targetDate);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.status = 'APPROVED' AND :targetDate BETWEEN lr.startDate AND lr.endDate")
    int countActiveLeavesOnDate(@Param("targetDate") LocalDate targetDate);

    /**
     * Overlap check that excludes time-based leave types (SHORT_BREAK, SHORT_LEAVE, EARLY_OUT, EARLY_LEAVE).
     * This allows short breaks and early outs to coexist with regular leaves on the same date.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employeeId = :employeeId " +
           "AND lr.status != 'REJECTED' AND lr.status != 'WITHDRAWN' " +
           "AND UPPER(lr.leaveType) NOT LIKE '%SHORT%' AND UPPER(lr.leaveType) NOT LIKE '%EARLY%' AND UPPER(lr.leaveType) NOT LIKE '%LATE%' " +
           "AND (:startDate <= lr.endDate AND :endDate >= lr.startDate)")
    List<LeaveRequest> findOverlappingLeaves(@Param("employeeId") Long employeeId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employeeId = :employeeId AND lr.status = 'APPROVED' AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedLeavesForEmployeeInRange(@Param("employeeId") Long employeeId,
                                                           @Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate);

    /**
     * Count time-based (short break / early out) requests for an employee within a date range cycle.
     * Used for policy enforcement (hourly/unit limits).
     */
    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.employeeId = :employeeId " +
           "AND (lr.status = 'APPROVED' OR lr.status = 'PENDING') " +
           "AND lr.startDate >= :cycleStart AND lr.startDate <= :cycleEnd " +
           "AND (UPPER(lr.leaveType) LIKE :typePattern)")
    long countTimeBasedRequestsInCycle(@Param("employeeId") Long employeeId,
                                       @Param("cycleStart") LocalDate cycleStart,
                                       @Param("cycleEnd") LocalDate cycleEnd,
                                       @Param("typePattern") String typePattern);

    /**
     * Fetch leaves (approved + pending) scoped to a specific month range for calendar summary.
     * Avoids loading the employee's entire leave history.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employeeId = :employeeId " +
           "AND (lr.status = 'APPROVED' OR lr.status = 'PENDING') " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate " +
           "ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findActiveAndPendingInRange(@Param("employeeId") Long employeeId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    /**
     * Batch fetch all approved leaves for a specific date across all employees.
     * Used by biometric import to avoid N+1 queries.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
           "AND lr.startDate <= :targetDate AND lr.endDate >= :targetDate")
    List<LeaveRequest> findAllApprovedForDate(@Param("targetDate") LocalDate targetDate);

    /**
     * Sum total days of all PENDING leave requests for a given employee, type, and year.
     * Used for "Deduct on Submit" validation.
     */
    @Query("SELECT COALESCE(SUM(lr.totalDays), 0.0) FROM LeaveRequest lr WHERE lr.employeeId = :employeeId " +
           "AND lr.status = 'PENDING' AND (UPPER(lr.leaveType) = UPPER(:leaveType) OR UPPER(lr.leaveType) = UPPER(CONCAT(:leaveType, '_LEAVE')) OR UPPER(lr.leaveType) = UPPER(REPLACE(:leaveType, '_LEAVE', ''))) AND EXTRACT(YEAR FROM lr.startDate) = :year")
    Double sumPendingLeaves(@Param("employeeId") Long employeeId,
                            @Param("leaveType") String leaveType,
                            @Param("year") int year);
}
