package com.example.hr_management_backend.features.employees.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import java.util.List;
import java.util.Optional;

public interface EmployeeService {
    Employee createEmployee(Employee employee);
    Employee updateEmployee(Long id, Employee employee);
    List<Employee> getAllEmployees();
    Optional<Employee> getEmployeeById(Long id);
    void deleteEmployee(Long id);
}
