package com.example.hr_management_backend.features.attendance.controller;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Autowired
    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/mark")
    public ResponseEntity<Attendance> markAttendance(@RequestParam Long employeeId, @RequestParam String status) {
        return ResponseEntity.ok(attendanceService.markAttendance(employeeId, status));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Attendance>> getAttendanceHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendanceHistory(employeeId));
    }
}
