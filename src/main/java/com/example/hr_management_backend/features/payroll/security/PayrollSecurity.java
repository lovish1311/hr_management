package com.example.hr_management_backend.features.payroll.security;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("payrollSecurity")
@RequiredArgsConstructor
public class PayrollSecurity {

    private final EmployeeRepository employeeRepository;

    /**
     * Checks if the authenticated user has self-access (owns the employee record) or is system super admin.
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
}
