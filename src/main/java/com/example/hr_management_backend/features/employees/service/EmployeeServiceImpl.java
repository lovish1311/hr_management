package com.example.hr_management_backend.features.employees.service;

import com.example.hr_management_backend.features.employees.dto.EmployeeDetailDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeSummaryDto;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;

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

        return employeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "employees")
    public List<EmployeeSummaryDto> getAllEmployeesSummary() {
        return employeeRepository.findAll().stream()
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
            return employeeRepository.findByDepartment(department, pageable).map(this::mapToSummaryDto);
        } else {
            return employeeRepository.findAll(pageable).map(this::mapToSummaryDto);
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
    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
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
                .managerId(managerId)
                .managerName(managerName)
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
                .managerId(managerId)
                .managerName(managerName)
                .build();
    }
}

