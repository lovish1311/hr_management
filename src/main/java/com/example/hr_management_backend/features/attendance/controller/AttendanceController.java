package com.example.hr_management_backend.features.attendance.controller;

import com.example.hr_management_backend.features.attendance.dto.AttendanceCalendarDayDto;
import com.example.hr_management_backend.features.attendance.dto.BiometricImportSummaryDto;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.security.AttendanceSecurity;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/attendance", "/api/attendance"})
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceSecurity attendanceSecurity;

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Attendance> checkIn(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            Authentication authentication) {
        Long targetId = resolveEmployeeId(employeeId, authentication);
        return ResponseEntity.ok(attendanceService.checkIn(targetId, time));
    }

    @PostMapping("/check-out")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Attendance> checkOut(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            Authentication authentication) {
        Long targetId = resolveEmployeeId(employeeId, authentication);
        return ResponseEntity.ok(attendanceService.checkOut(targetId, time));
    }

    @PostMapping("/mark")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR')")
    public ResponseEntity<Attendance> markAttendance(
            @RequestParam Long employeeId,
            @RequestParam String status) {
        return ResponseEntity.ok(attendanceService.markAttendance(employeeId, status));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or @attendanceSecurity.isSelf(authentication, #employeeId)")
    public ResponseEntity<List<Attendance>> getAttendanceHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(attendanceService.getAttendanceHistory(employeeId));
    }

    @DeleteMapping("/clear-all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> clearAllAttendance() {
        attendanceService.clearAllAttendance();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/calendar-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or @attendanceSecurity.isSelf(authentication, #employeeId)")
    public ResponseEntity<List<AttendanceCalendarDayDto>> getMonthlyCalendarSummary(
            @RequestParam Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int activeYear = (year != null && year > 0) ? year : now.getYear();
        int activeMonth = (month != null && month > 0) ? month : now.getMonthValue();
        return ResponseEntity.ok(attendanceService.getMonthlyCalendarSummary(employeeId, activeYear, activeMonth));
    }

    @PostMapping("/import-biometric")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR')")
    public ResponseEntity<BiometricImportSummaryDto> importBiometricExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return ResponseEntity.ok(attendanceService.parseAndCommitBiometricExcel(file, targetDate));
    }

    private Long resolveEmployeeId(Long requestedId, Authentication authentication) {
        Long authenticatedId = attendanceSecurity.getAuthenticatedEmployeeId(authentication);
        if (requestedId == null) {
            if (authenticatedId == null) {
                throw new AccessDeniedException("Could not resolve authenticated employee.");
            }
            return authenticatedId;
        }

        if (requestedId.equals(authenticatedId)) {
            return requestedId;
        }

        boolean isElevated = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_HR"));
        if (!isElevated) {
            throw new AccessDeniedException("Not authorized to record attendance for another employee.");
        }
        return requestedId;
    }
}
