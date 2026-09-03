package com.example.hr_management_backend.features.leaves.controller;

import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/leaves", "/api/leaves"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/apply")
    public ResponseEntity<LeaveRequest> applyForLeave(@RequestBody LeaveRequest request) {
        return ResponseEntity.ok(leaveService.applyForLeave(request));
    }

    @GetMapping("/balance/{employeeId}")
    public ResponseEntity<LeaveBalance> getLeaveBalance(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year) {
        int activeYear = (year != null) ? year : Year.now().getValue();
        return ResponseEntity.ok(leaveService.getOrCreateLeaveBalance(employeeId, activeYear));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<LeaveRequest> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String rejectionReason = body.get("rejectionReason");
        Long approverId = null;
        try {
            String rawId = body.get("approverId");
            if (rawId != null && !rawId.isBlank()) approverId = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        LeaveRequest updated = leaveService.updateStatus(id, status, rejectionReason, approverId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getLeavesByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(leaveService.getLeavesByEmployee(employeeId));
    }

    @GetMapping("/pending/manager/{managerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'MANAGER')")
    public ResponseEntity<List<LeaveRequest>> getPendingForManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(leaveService.getPendingForManager(managerId));
    }

    @GetMapping("/pending/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<List<LeaveRequest>> getAllPendingRequests() {
        return ResponseEntity.ok(leaveService.getAllPendingRequests());
    }

    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAllLeaveRequests() {
        return ResponseEntity.ok(leaveService.getAllLeaveRequests());
    }

    @PostMapping("/admin/bulk-grant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<Map<String, String>> bulkGrantLeaves(@RequestBody Map<String, Object> body) {
        String leaveType = (String) body.getOrDefault("leaveType", "CASUAL");
        int grantDays = ((Number) body.getOrDefault("grantDays", 1)).intValue();
        @SuppressWarnings("unchecked")
        List<Integer> rawExclusions = (List<Integer>) body.get("excludedEmployeeIds");
        List<Long> excludedEmployeeIds = (rawExclusions != null)
                ? rawExclusions.stream().map(Long::valueOf).toList()
                : List.of();

        leaveService.bulkGrantLeaves(leaveType, grantDays, excludedEmployeeIds);
        return ResponseEntity.ok(Map.of("message", "Bulk leave grant executed successfully."));
    }

    @PostMapping("/admin/apply-on-behalf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<LeaveRequest> applyOnBehalfByHr(@RequestBody LeaveRequest request) {
        return ResponseEntity.ok(leaveService.applyOnBehalfByHr(request));
    }

    @PostMapping("/admin/adjust-balance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<LeaveBalance> adjustEmployeeBalance(@RequestBody Map<String, Object> body) {
        Long employeeId = ((Number) body.get("employeeId")).longValue();
        String leaveType = (String) body.getOrDefault("leaveType", "CASUAL");
        int adjustmentDays = ((Number) body.getOrDefault("adjustmentDays", 0)).intValue();

        return ResponseEntity.ok(leaveService.adjustEmployeeBalance(employeeId, leaveType, adjustmentDays));
    }

    @PutMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR')")
    public ResponseEntity<LeaveRequest> withdrawApprovedLeave(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long actorId = null;
        try {
            String rawId = body.get("actorId");
            if (rawId != null && !rawId.isBlank()) actorId = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(leaveService.withdrawApprovedLeave(id, actorId));
    }
}

