package com.example.hr_management_backend.features.employees.service;

import com.example.hr_management_backend.features.employees.dto.EmployeeDetailDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeSummaryDto;
import com.example.hr_management_backend.features.employees.model.Employee;
import org.springframework.data.domain.Page;

import java.util.List;

public interface EmployeeService {
    Employee createEmployee(Employee employee);
    Employee updateEmployee(Long id, Employee employee);
    List<EmployeeSummaryDto> getAllEmployeesSummary();
    Page<EmployeeSummaryDto> searchEmployees(String query, String department, int page, int size);
    EmployeeDetailDto getEmployeeDetail(Long id);
    EmployeeSummaryDto assignManager(Long employeeId, Long managerId);
    EmployeeDetailDto updatePermissions(Long employeeId, Boolean isAttendanceTracked, String lateArrivalAllowedUntil, String earlyOutAllowedAfter);
    EmployeeDetailDto updatePermissions(Long employeeId, Boolean isAttendanceTracked, String lateArrivalAllowedUntil, String earlyOutAllowedAfter, Boolean hasTambolaAccess);
    EmployeeDetailDto elevateRoleAndPermissions(Long employeeId, com.example.hr_management_backend.features.employees.dto.ElevateEmployeeDto dto, String actorEmail);
    void deleteEmployee(Long id);
}

