package com.example.hr_management_backend.features.leaves.controller;

import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    @Autowired
    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping("/apply")
    public ResponseEntity<LeaveRequest> applyForLeave(@RequestBody LeaveRequest request) {
        return ResponseEntity.ok(leaveService.applyForLeave(request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<LeaveRequest> updateStatus(@PathVariable Long id, @RequestParam String status) {
        LeaveRequest updated = leaveService.updateStatus(id, status);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<LeaveRequest>> getLeavesByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(leaveService.getLeavesByEmployee(employeeId));
    }

    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAllLeaveRequests() {
        return ResponseEntity.ok(leaveService.getAllLeaveRequests());
    }
}
