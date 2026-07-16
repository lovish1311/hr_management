package com.example.hr_management_backend.features.employees.repository;

import com.example.hr_management_backend.features.employees.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
