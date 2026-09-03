package com.example.hr_management_backend.features.attendance.controller;

import com.example.hr_management_backend.features.attendance.dto.AttendanceCalendarDayDto;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/attendance", "/api/attendance"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/mark")
    public ResponseEntity<Attendance> markAttendance(@RequestParam Long employeeId, @RequestParam String status) {
        return ResponseEntity.ok(attendanceService.markAttendance(employeeId, status));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Attendance>> getAttendanceHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendanceHistory(employeeId));
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Void> clearAllAttendance() {
        attendanceService.clearAllAttendance();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/calendar-summary")
    public ResponseEntity<List<AttendanceCalendarDayDto>> getMonthlyCalendarSummary(
            @RequestParam Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int activeYear = (year != null) ? year : now.getYear();
        int activeMonth = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(attendanceService.getMonthlyCalendarSummary(employeeId, activeYear, activeMonth));
    }

    @PostMapping("/import-biometric")
    public ResponseEntity<com.example.hr_management_backend.features.attendance.dto.BiometricImportSummaryDto> importBiometricExcel(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "targetDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return ResponseEntity.ok(attendanceService.parseAndCommitBiometricExcel(file, targetDate));
    }
}

