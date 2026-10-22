package com.example.hr_management_backend.features.employees.repository;

import com.example.hr_management_backend.features.employees.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    @EntityGraph(attributePaths = {"manager"})
    Page<Employee> findByDepartmentAndRoleNotIgnoreCase(String department, String role, Pageable pageable);

    @EntityGraph(attributePaths = {"manager"})
    @Query("SELECT e FROM Employee e WHERE UPPER(e.role) != 'SUPER_ADMIN' AND (" +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Employee> searchEmployees(@Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"manager"})
    List<Employee> findByRoleNotIgnoreCase(String role);
    
    @EntityGraph(attributePaths = {"manager"})
    Page<Employee> findByRoleNotIgnoreCase(String role, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT nextval('employee_code_seq')", nativeQuery = true)
    Long getNextEmployeeCodeSequence();

    @Query("SELECT e.department FROM Employee e WHERE e.id = :id")
    Optional<String> findDepartmentById(@Param("id") Long id);

    @Query("SELECT e FROM Employee e WHERE UPPER(e.status) IN ('ACTIVE', 'PROBATION') AND (e.relievingDate IS NULL OR e.relievingDate > :today)")
    List<Employee> findActiveEmployeesForMilestones(@Param("today") java.time.LocalDate today);

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND TRIM(e.department) <> '' ORDER BY e.department ASC")
    List<String> findDistinctDepartments();
}

