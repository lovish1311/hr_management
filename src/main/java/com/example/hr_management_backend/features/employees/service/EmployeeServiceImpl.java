package com.example.hr_management_backend.features.employees.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

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
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

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
        return employeeRepository.findByRoleNotIgnoreCase("SUPER_ADMIN").stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeSummaryDto> searchEmployees(String query, String department, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        if (query != null && !query.isBlank()) {
            return employeeRepository.searchEmployees(query, pageable).map(this::mapToSummaryDto);
        } else if (department != null && !department.isBlank()) {
            return employeeRepository.findByDepartmentAndRoleNotIgnoreCase(department, "SUPER_ADMIN", pageable).map(this::mapToSummaryDto);
        } else {
            return employeeRepository.findByRoleNotIgnoreCase("SUPER_ADMIN", pageable).map(this::mapToSummaryDto);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employee_details", key = "#id")
    public EmployeeDetailDto getEmployeeDetail(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));
        return mapToDetailDto(employee);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
    public EmployeeSummaryDto assignManager(Long employeeId, Long managerId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));

        if (managerId != null) {
            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new RuntimeException("Manager not found with id: " + managerId));
            
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
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));

        if (isAttendanceTracked != null) {
            employee.setIsAttendanceTracked(isAttendanceTracked);
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

    private EmployeeSummaryDto mapToSummaryDto(Employee employee) {
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
                .build();
    }

    private EmployeeDetailDto mapToDetailDto(Employee employee) {
        String managerName = null;
        Long managerId = null;
        if (employee.getManager() != null) {
            managerId = employee.getManager().getId();
            managerName = employee.getManager().getFirstName() + " " + employee.getManager().getLastName();
        }

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
                .build();
    }
}

