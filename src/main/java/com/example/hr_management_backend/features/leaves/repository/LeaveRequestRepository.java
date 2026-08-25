package com.example.hr_management_backend.features.leaves.repository;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' AND lr.employeeId IN " +
           "(SELECT e.id FROM Employee e WHERE e.manager.id = :managerId) ORDER BY lr.createdAt DESC")
    List<LeaveRequest> findPendingForManager(@Param("managerId") Long managerId);
}

