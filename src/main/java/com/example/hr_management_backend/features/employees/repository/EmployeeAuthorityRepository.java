package com.example.hr_management_backend.features.employees.repository;

import com.example.hr_management_backend.features.employees.model.EmployeeAuthority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface EmployeeAuthorityRepository extends JpaRepository<EmployeeAuthority, Long> {

    /**
     * Highly optimized indexed check: uses the unique index (employee_id, authority)
     * for sub-millisecond authorization evaluation without table scans.
     */
    boolean existsByEmployeeIdAndAuthority(Long employeeId, String authority);

    List<EmployeeAuthority> findByEmployeeId(Long employeeId);

    List<EmployeeAuthority> findByEmployeeIdIn(Collection<Long> employeeIds);

    @Query("SELECT a.authority FROM EmployeeAuthority a WHERE a.employeeId = :employeeId")
    List<String> findAuthoritiesByEmployeeId(@Param("employeeId") Long employeeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmployeeAuthority a WHERE a.employeeId = :employeeId")
    void deleteByEmployeeId(@Param("employeeId") Long employeeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EmployeeAuthority a WHERE a.employeeId = :employeeId AND a.authority = :authority")
    void deleteByEmployeeIdAndAuthority(@Param("employeeId") Long employeeId, @Param("authority") String authority);
}
