package com.example.hr_management_backend.features.employees.service;

import com.example.hr_management_backend.core.exception.ResourceNotFoundException;
import com.example.hr_management_backend.features.employees.dto.EmployeeDetailDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeSummaryDto;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import java.time.LocalDate;
import java.util.Optional;

import com.example.hr_management_backend.features.auth.repository.UserRepository;
import com.example.hr_management_backend.features.employees.dto.ElevateEmployeeDto;
import com.example.hr_management_backend.features.employees.model.EmployeeAuthority;
import com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeAuthorityRepository employeeAuthorityRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public Employee createEmployee(Employee employee) {
        if (employee.getEmployeeCode() == null || employee.getEmployeeCode().isBlank()) {
            long count = employeeRepository.count() + 1000;
            employee.setEmployeeCode("EMP-" + count);
        }
        return employeeRepository.save(employee);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public Employee updateEmployee(Long id, Employee employeeDetails) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        employee.setFirstName(employeeDetails.getFirstName());
        employee.setLastName(employeeDetails.getLastName());
        employee.setEmail(employeeDetails.getEmail());
        employee.setDepartment(employeeDetails.getDepartment());
        employee.setDesignation(employeeDetails.getDesignation());
        employee.setRole(employeeDetails.getRole());
        employee.setPhoneNumber(employeeDetails.getPhoneNumber());
        employee.setAddress(employeeDetails.getAddress());
        employee.setEmergencyContactName(employeeDetails.getEmergencyContactName());
        employee.setEmergencyContactPhone(employeeDetails.getEmergencyContactPhone());
        employee.setEmploymentType(employeeDetails.getEmploymentType());
        employee.setStatus(employeeDetails.getStatus());
        employee.setBiometricName(employeeDetails.getBiometricName());
        employee.setLateArrivalAllowedUntil(employeeDetails.getLateArrivalAllowedUntil());
        employee.setEarlyOutAllowedAfter(employeeDetails.getEarlyOutAllowedAfter());
        employee.setIsAttendanceTracked(employeeDetails.getIsAttendanceTracked() != null ? employeeDetails.getIsAttendanceTracked() : true);
        employee.setDepartmentCategory(employeeDetails.getDepartmentCategory());

        return employeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employees")
    public List<EmployeeSummaryDto> getAllEmployeesSummary() {
        List<Employee> employees = employeeRepository.findByRoleNotIgnoreCase("SUPER_ADMIN");
        List<Long> employeeIds = employees.stream().map(Employee::getId).toList();
        java.util.Map<Long, List<String>> authoritiesMap = employeeAuthorityRepository.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.groupingBy(
                        EmployeeAuthority::getEmployeeId,
                        Collectors.mapping(EmployeeAuthority::getAuthority, Collectors.toList())
                ));

        return employees.stream()
                .map(e -> mapToSummaryDto(e, authoritiesMap.getOrDefault(e.getId(), java.util.Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeSummaryDto> searchEmployees(String query, String department, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Employee> pageResult;

        if (query != null && !query.isBlank()) {
            pageResult = employeeRepository.searchEmployees(query, pageable);
        } else if (department != null && !department.isBlank()) {
            pageResult = employeeRepository.findByDepartmentAndRoleNotIgnoreCase(department, "SUPER_ADMIN", pageable);
        } else {
            pageResult = employeeRepository.findByRoleNotIgnoreCase("SUPER_ADMIN", pageable);
        }

        List<Long> pageIds = pageResult.getContent().stream().map(Employee::getId).toList();
        java.util.Map<Long, List<String>> authoritiesMap = employeeAuthorityRepository.findByEmployeeIdIn(pageIds).stream()
                .collect(Collectors.groupingBy(
                        EmployeeAuthority::getEmployeeId,
                        Collectors.mapping(EmployeeAuthority::getAuthority, Collectors.toList())
                ));

        return pageResult.map(e -> mapToSummaryDto(e, authoritiesMap.getOrDefault(e.getId(), java.util.Collections.emptyList())));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employee_details", key = "#id")
    public EmployeeDetailDto getEmployeeDetail(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return mapToDetailDto(employee);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public EmployeeSummaryDto assignManager(Long employeeId, Long managerId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (managerId != null) {
            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + managerId));
            
            // Automatically elevate role to MANAGER if currently EMPLOYEE
            if ("EMPLOYEE".equalsIgnoreCase(manager.getRole())) {
                manager.setRole("MANAGER");
                employeeRepository.save(manager);
            }
            employee.setManager(manager);
        } else {
            employee.setManager(null);
        }

        Employee saved = employeeRepository.save(employee);
        return mapToSummaryDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public EmployeeDetailDto updatePermissions(Long employeeId, Boolean isAttendanceTracked, String lateArrivalAllowedUntil, String earlyOutAllowedAfter) {
        return updatePermissions(employeeId, isAttendanceTracked, lateArrivalAllowedUntil, earlyOutAllowedAfter, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public EmployeeDetailDto updatePermissions(Long employeeId, Boolean isAttendanceTracked, String lateArrivalAllowedUntil, String earlyOutAllowedAfter, Boolean hasTambolaAccess) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));

        if (isAttendanceTracked != null) {
            employee.setIsAttendanceTracked(isAttendanceTracked);
        }

        if (hasTambolaAccess != null) {
            employee.setHasTambolaAccess(hasTambolaAccess);
        }

        if (lateArrivalAllowedUntil != null) {
            if (lateArrivalAllowedUntil.isBlank() || "CLEAR".equalsIgnoreCase(lateArrivalAllowedUntil)) {
                employee.setLateArrivalAllowedUntil(null);
            } else {
                try {
                    String timeStr = lateArrivalAllowedUntil.trim();
                    if (timeStr.length() == 5) timeStr += ":00";
                    employee.setLateArrivalAllowedUntil(java.time.LocalTime.parse(timeStr));
                } catch (Exception e) {
                    log.warn("Could not parse lateArrivalAllowedUntil: {}", lateArrivalAllowedUntil);
                }
            }
        }

        if (earlyOutAllowedAfter != null) {
            if (earlyOutAllowedAfter.isBlank() || "CLEAR".equalsIgnoreCase(earlyOutAllowedAfter)) {
                employee.setEarlyOutAllowedAfter(null);
            } else {
                try {
                    String timeStr = earlyOutAllowedAfter.trim();
                    if (timeStr.length() == 5) timeStr += ":00";
                    employee.setEarlyOutAllowedAfter(java.time.LocalTime.parse(timeStr));
                } catch (Exception e) {
                    log.warn("Could not parse earlyOutAllowedAfter: {}", earlyOutAllowedAfter);
                }
            }
        }

        Employee saved = employeeRepository.save(employee);
        return mapToDetailDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public void deleteEmployee(Long id) {
        employeeAuthorityRepository.deleteByEmployeeId(id);
        employeeRepository.deleteById(id);
    }

    private String computeTodayStatus(Employee employee) {
        if (Boolean.FALSE.equals(employee.getIsAttendanceTracked())) {
            return "EXEMPT";
        }
        LocalDate today = LocalDate.now();
        boolean isOnLeave = leaveRequestRepository.isEmployeeOnApprovedLeave(employee.getId(), today);
        if (isOnLeave) {
            return "ON_LEAVE";
        }
        Optional<Attendance> att = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today);
        if (att.isPresent()) {
            String s = att.get().getStatus();
            if ("PRESENT".equalsIgnoreCase(s) || "LATE".equalsIgnoreCase(s) || "ON_LEAVE".equalsIgnoreCase(s)) {
                return s.toUpperCase();
            }
            return "ABSENT";
        }
        return "ABSENT";
    }

    private int computeLeaveBalance(Long employeeId) {
        int currentYear = LocalDate.now().getYear();
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, currentYear)
                .map(lb -> (int) (lb.getCasualLeaveRemaining() + lb.getSickLeaveRemaining() + lb.getEarnedLeaveRemaining()))
                .orElse(14);
    }

    private EmployeeSummaryDto mapToSummaryDto(Employee employee, List<String> authorities) {
        String managerName = null;
        Long managerId = null;
        if (employee.getManager() != null) {
            managerId = employee.getManager().getId();
            managerName = employee.getManager().getFirstName() + " " + employee.getManager().getLastName();
        }

        return EmployeeSummaryDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .designation(employee.getDesignation())
                .role(employee.getRole())
                .status(employee.getStatus())
                .phoneNumber(employee.getPhoneNumber())
                .joiningDate(employee.getJoiningDate())
                .isAttendanceTracked(employee.getIsAttendanceTracked() != null ? employee.getIsAttendanceTracked() : true)
                .departmentCategory(employee.getDepartmentCategory())
                .lateArrivalAllowedUntil(employee.getLateArrivalAllowedUntil())
                .earlyOutAllowedAfter(employee.getEarlyOutAllowedAfter())
                .managerId(managerId)
                .managerName(managerName)
                .todayAttendanceStatus(computeTodayStatus(employee))
                .leaveBalance(computeLeaveBalance(employee.getId()))
                .authorities(authorities != null ? authorities : java.util.Collections.emptyList())
                .build();
    }

    private EmployeeSummaryDto mapToSummaryDto(Employee employee) {
        List<String> authorities = employeeAuthorityRepository.findAuthoritiesByEmployeeId(employee.getId());
        return mapToSummaryDto(employee, authorities);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public EmployeeDetailDto elevateRoleAndPermissions(Long employeeId, ElevateEmployeeDto dto, String actorEmail) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            String newRole = dto.getRole().trim().toUpperCase().replace("ROLE_", "");
            employee.setRole(newRole);
            employeeRepository.save(employee);

            // Synchronize corresponding User entity if it exists
            userRepository.findByEmail(employee.getEmail()).ifPresent(user -> {
                user.setRole("ROLE_" + newRole);
                userRepository.save(user);
            });
        }

        // Atomically synchronize granular permissions in employee_authorities
        employeeAuthorityRepository.deleteByEmployeeId(employeeId);
        if (dto.getAuthorities() != null && !dto.getAuthorities().isEmpty()) {
            List<EmployeeAuthority> authoritiesToSave = dto.getAuthorities().stream()
                    .filter(auth -> auth != null && !auth.isBlank())
                    .map(auth -> EmployeeAuthority.builder()
                            .employeeId(employeeId)
                            .authority(auth.trim().toUpperCase())
                            .grantedBy(actorEmail)
                            .grantedAt(LocalDateTime.now())
                            .build())
                    .toList();
            employeeAuthorityRepository.saveAll(authoritiesToSave);
        }

        log.info("Elevated role and authorities for employeeId={} (email={}) by actor={}. New role={}, authorities={}",
                employeeId, employee.getEmail(), actorEmail, employee.getRole(), dto.getAuthorities());

        return mapToDetailDto(employee);
    }

    private EmployeeDetailDto mapToDetailDto(Employee employee) {
        String managerName = null;
        Long managerId = null;
        if (employee.getManager() != null) {
            managerId = employee.getManager().getId();
            managerName = employee.getManager().getFirstName() + " " + employee.getManager().getLastName();
        }

        List<String> authorities = employeeAuthorityRepository.findAuthoritiesByEmployeeId(employee.getId());

        return EmployeeDetailDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .department(employee.getDepartment())
                .designation(employee.getDesignation())
                .role(employee.getRole())
                .joiningDate(employee.getJoiningDate())
                .employmentType(employee.getEmploymentType())
                .status(employee.getStatus())
                .phoneNumber(employee.getPhoneNumber())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender())
                .address(employee.getAddress())
                .emergencyContactName(employee.getEmergencyContactName())
                .emergencyContactPhone(employee.getEmergencyContactPhone())
                .biometricName(employee.getBiometricName())
                .lateArrivalAllowedUntil(employee.getLateArrivalAllowedUntil())
                .earlyOutAllowedAfter(employee.getEarlyOutAllowedAfter())
                .isAttendanceTracked(employee.getIsAttendanceTracked() != null ? employee.getIsAttendanceTracked() : true)
                .departmentCategory(employee.getDepartmentCategory())
                .managerId(managerId)
                .managerName(managerName)
                .todayAttendanceStatus(computeTodayStatus(employee))
                .leaveBalance(computeLeaveBalance(employee.getId()))
                .hasTambolaAccess(Boolean.TRUE.equals(employee.getHasTambolaAccess()))
                .authorities(authorities)
                .build();
    }
}

