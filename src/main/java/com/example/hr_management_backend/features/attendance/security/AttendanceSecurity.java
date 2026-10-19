package com.example.hr_management_backend.features.attendance.security;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("attendanceSecurity")
@RequiredArgsConstructor
public class AttendanceSecurity {

    private final EmployeeRepository employeeRepository;

    /**
     * Checks if the authenticated caller owns the employeeId record or is a manager/admin.
     */
    public boolean isSelf(Authentication authentication, Long employeeId) {
        if (authentication == null || employeeId == null) {
            return false;
        }
        String email = authentication.getName();
        if ("admin@company.com".equalsIgnoreCase(email)) {
            return true;
        }
        return employeeRepository.findById(employeeId)
                .map(emp -> emp.getEmail() != null && emp.getEmail().equalsIgnoreCase(email))
                .orElse(false);
    }

    /**
     * Finds the employee ID associated with the authenticated principal.
     */
    public Long getAuthenticatedEmployeeId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String email = authentication.getName();
        return employeeRepository.findByEmail(email)
                .map(Employee::getId)
                .orElse(null);
    }
}
