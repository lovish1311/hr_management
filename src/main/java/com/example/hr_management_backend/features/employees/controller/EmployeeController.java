package com.example.hr_management_backend.features.employees.controller;

import com.example.hr_management_backend.features.employees.dto.AssignManagerDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeDetailDto;
import com.example.hr_management_backend.features.employees.dto.EmployeeSummaryDto;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.StarredPeerRepository;
import com.example.hr_management_backend.features.employees.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/employees", "/api/employees"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        return ResponseEntity.ok(employeeService.createEmployee(employee));
    }

    @GetMapping
    public ResponseEntity<List<EmployeeSummaryDto>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployeesSummary());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<EmployeeSummaryDto>> searchEmployees(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(employeeService.searchEmployees(query, department, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDetailDto> getEmployeeById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(employeeService.getEmployeeDetail(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody Employee employeeDetails) {
        try {
            return ResponseEntity.ok(employeeService.updateEmployee(id, employeeDetails));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/manager")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<EmployeeSummaryDto> assignManager(
            @PathVariable Long id,
            @Valid @RequestBody AssignManagerDto dto) {
        return ResponseEntity.ok(employeeService.assignManager(id, dto.getManagerId()));
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<EmployeeDetailDto> updatePermissions(
            @PathVariable Long id,
            @RequestBody com.example.hr_management_backend.features.employees.dto.UpdatePermissionsDto dto) {
        return ResponseEntity.ok(employeeService.updatePermissions(
                id,
                dto.getIsAttendanceTracked(),
                dto.getLateArrivalAllowedUntil(),
                dto.getEarlyOutAllowedAfter()
        ));
    }

    private final StarredPeerRepository starredPeerRepository;

    @GetMapping("/starred")
    public ResponseEntity<List<Long>> getStarredPeers(@RequestParam(defaultValue = "1") Long starrerId) {
        List<Long> starredIds = starredPeerRepository.findByStarrerEmployeeId(starrerId)
                .stream()
                .map(com.example.hr_management_backend.features.employees.model.StarredPeer::getStarredEmployeeId)
                .toList();
        return ResponseEntity.ok(starredIds);
    }

    @PostMapping("/starred/{starredEmployeeId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<java.util.Map<String, Object>> toggleStarPeer(
            @PathVariable Long starredEmployeeId,
            @RequestParam(defaultValue = "1") Long starrerId) {
        var existing = starredPeerRepository.findByStarrerEmployeeIdAndStarredEmployeeId(starrerId, starredEmployeeId);
        boolean isStarred;
        if (existing.isPresent()) {
            starredPeerRepository.deleteByStarrerEmployeeIdAndStarredEmployeeId(starrerId, starredEmployeeId);
            isStarred = false;
        } else {
            starredPeerRepository.save(com.example.hr_management_backend.features.employees.model.StarredPeer.builder()
                    .starrerEmployeeId(starrerId)
                    .starredEmployeeId(starredEmployeeId)
                    .build());
            isStarred = true;
        }
        return ResponseEntity.ok(java.util.Map.of("starred", isStarred, "starredEmployeeId", starredEmployeeId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}

