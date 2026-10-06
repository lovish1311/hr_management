package com.example.hr_management_backend.features.employees;

import com.example.hr_management_backend.core.security.CustomUserDetailsService;
import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.dto.ElevateEmployeeDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeDetailDto;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.employees.service.EmployeeService;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EnterpriseRbacTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeAuthorityRepository employeeAuthorityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Employee employee1;
    private Employee employee2;

    @BeforeEach
    void setUp() {
        employee1 = employeeRepository.findByEmail("rbac1@company.com").orElseGet(() ->
                employeeRepository.save(Employee.builder()
                        .email("rbac1@company.com")
                        .firstName("Rbac")
                        .lastName("One")
                        .role("EMPLOYEE")
                        .employeeCode("RBAC01")
                        .build())
        );

        userRepository.findByEmail("rbac1@company.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("rbac1@company.com")
                        .password("pass123")
                        .role("ROLE_EMPLOYEE")
                        .employeeId(employee1.getId())
                        .build())
        );

        employee2 = employeeRepository.findByEmail("rbac2@company.com").orElseGet(() ->
                employeeRepository.save(Employee.builder()
                        .email("rbac2@company.com")
                        .firstName("Rbac")
                        .lastName("Two")
                        .role("EMPLOYEE")
                        .employeeCode("RBAC02")
                        .build())
        );

        userRepository.findByEmail("rbac2@company.com").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("rbac2@company.com")
                        .password("pass123")
                        .role("ROLE_EMPLOYEE")
                        .employeeId(employee2.getId())
                        .build())
        );
    }

    @Test
    @DisplayName("Super Admin can elevate employee to Manager with PAYROLL_MANAGE and LEAVE_APPROVE_ALL")
    void testElevateRoleAndAssignGranularAuthorities() {
        ElevateEmployeeDto dto = ElevateEmployeeDto.builder()
                .role("MANAGER")
                .authorities(List.of("PAYROLL_MANAGE", "LEAVE_APPROVE_ALL"))
                .build();

        EmployeeDetailDto updated = employeeService.elevateRoleAndPermissions(employee1.getId(), dto, "admin@company.com");

        assertEquals("MANAGER", updated.getRole());
        assertTrue(updated.getAuthorities().contains("PAYROLL_MANAGE"));
        assertTrue(updated.getAuthorities().contains("LEAVE_APPROVE_ALL"));

        // Verify repository direct queries
        assertTrue(employeeAuthorityRepository.existsByEmployeeIdAndAuthority(employee1.getId(), "PAYROLL_MANAGE"));
        assertTrue(employeeAuthorityRepository.existsByEmployeeIdAndAuthority(employee1.getId(), "LEAVE_APPROVE_ALL"));

        // Verify User entity sync
        User updatedUser = userRepository.findByEmail(employee1.getEmail()).orElseThrow();
        assertEquals("ROLE_MANAGER", updatedUser.getRole());

        // Verify Spring Security UserDetails loading
        UserDetails userDetails = userDetailsService.loadUserByUsername(employee1.getEmail());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER")));
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PAYROLL_MANAGE")));
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("LEAVE_APPROVE_ALL")));
    }

    @Test
    @DisplayName("Employee granted LEAVE_APPROVE_ALL can approve another employee's leave")
    void testLeaveApproveAllCanApproveOther() {
        // Grant LEAVE_APPROVE_ALL to employee1
        employeeService.elevateRoleAndPermissions(
                employee1.getId(),
                ElevateEmployeeDto.builder()
                        .role("EMPLOYEE")
                        .authorities(List.of("LEAVE_APPROVE_ALL"))
                        .build(),
                "admin@company.com"
        );

        // Employee2 applies for leave
        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(employee2.getId())
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now().plusDays(3))
                .leaveType("CASUAL")
                .status("PENDING")
                .totalDays(2.0)
                .build());

        // Employee1 approves employee2's leave using LEAVE_APPROVE_ALL
        LeaveRequest updated = leaveService.updateStatus(leave.getId(), "APPROVED", null, employee1.getEmail());

        assertEquals("APPROVED", updated.getStatus());
        assertEquals(employee1.getId(), updated.getApprovedBy());
    }

    @Test
    @DisplayName("Employee with LEAVE_APPROVE_ALL strictly CANNOT approve their own leave")
    void testLeaveApproveAllCannotSelfApprove() {
        // Grant LEAVE_APPROVE_ALL to employee1
        employeeService.elevateRoleAndPermissions(
                employee1.getId(),
                ElevateEmployeeDto.builder()
                        .role("MANAGER")
                        .authorities(List.of("LEAVE_APPROVE_ALL"))
                        .build(),
                "admin@company.com"
        );

        // Employee1 applies for leave
        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(employee1.getId())
                .startDate(LocalDate.now().plusDays(4))
                .endDate(LocalDate.now().plusDays(5))
                .leaveType("CASUAL")
                .status("PENDING")
                .totalDays(2.0)
                .build());

        // Employee1 attempts to approve own leave -> must throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            leaveService.updateStatus(leave.getId(), "APPROVED", null, employee1.getEmail());
        });
    }

    @Test
    @DisplayName("Revoking authority removes it atomically from repository")
    void testRevokeAuthority() {
        // Assign both authorities
        employeeService.elevateRoleAndPermissions(
                employee1.getId(),
                ElevateEmployeeDto.builder()
                        .role("MANAGER")
                        .authorities(List.of("PAYROLL_MANAGE", "LEAVE_APPROVE_ALL"))
                        .build(),
                "admin@company.com"
        );

        // Now update with only PAYROLL_MANAGE
        EmployeeDetailDto updated = employeeService.elevateRoleAndPermissions(
                employee1.getId(),
                ElevateEmployeeDto.builder()
                        .role("MANAGER")
                        .authorities(List.of("PAYROLL_MANAGE"))
                        .build(),
                "admin@company.com"
        );

        assertTrue(updated.getAuthorities().contains("PAYROLL_MANAGE"));
        assertFalse(updated.getAuthorities().contains("LEAVE_APPROVE_ALL"));
        assertFalse(employeeAuthorityRepository.existsByEmployeeIdAndAuthority(employee1.getId(), "LEAVE_APPROVE_ALL"));
    }
}
