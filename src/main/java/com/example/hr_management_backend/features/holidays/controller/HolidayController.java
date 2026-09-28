package com.example.hr_management_backend.features.holidays.controller;

import com.example.hr_management_backend.features.auth.model.User;
import com.example.hr_management_backend.features.auth.service.AuthService;
import com.example.hr_management_backend.features.holidays.dto.EmployeeHolidayCalendarDto;
import com.example.hr_management_backend.features.holidays.dto.HolidayListDto;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.model.HolidayList;
import com.example.hr_management_backend.features.holidays.service.HolidayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/holidays", "/api/holidays"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class HolidayController {

    private final HolidayService holidayService;
    private final AuthService authService;

    // ==========================================
    // EMPLOYEE / GENERAL CALENDAR VIEW
    // ==========================================

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeHolidayCalendarDto> getHolidayCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long employeeId) {

        int activeYear = (year != null) ? year : Year.now().getValue();

        // If employeeId is not supplied in query, try to derive from current authenticated user
        if (employeeId == null) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                    User user = authService.getCurrentUser(auth.getName());
                    if (user != null && user.getEmployeeId() != null) {
                        employeeId = user.getEmployeeId();
                    }
                }
            } catch (Exception e) {
                log.debug("Could not resolve employeeId from authentication: {}", e.getMessage());
            }
        }

        EmployeeHolidayCalendarDto calendar = holidayService.getEmployeeHolidayCalendar(activeYear, employeeId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0")
                .body(calendar);
    }

    // ==========================================
    // ADMIN: Holiday List Management
    // ==========================================

    @GetMapping("/lists")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<List<HolidayListDto>> getHolidayLists(@RequestParam(required = false) Integer year) {
        int activeYear = (year != null) ? year : Year.now().getValue();
        return ResponseEntity.ok(holidayService.getHolidayListsByYear(activeYear));
    }

    @GetMapping("/lists/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<HolidayListDto> getHolidayListDetails(@PathVariable Long id) {
        return ResponseEntity.ok(holidayService.getHolidayListDetails(id));
    }

    @PostMapping("/lists")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<HolidayList> createHolidayList(@RequestBody HolidayList list) {
        Long adminId = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                User user = authService.getCurrentUser(auth.getName());
                if (user != null) adminId = user.getId();
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(holidayService.createHolidayList(list, adminId));
    }

    @PutMapping("/lists/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<HolidayList> updateHolidayList(
            @PathVariable Long id,
            @RequestBody HolidayList updated) {
        return ResponseEntity.ok(holidayService.updateHolidayList(id, updated));
    }

    @PutMapping("/lists/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<HolidayList> togglePublishHolidayList(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean published = body.getOrDefault("published", true);
        return ResponseEntity.ok(holidayService.togglePublishHolidayList(id, published));
    }

    @DeleteMapping("/lists/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteHolidayList(@PathVariable Long id) {
        holidayService.deleteHolidayList(id);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // ADMIN: Holiday Entries Management
    // ==========================================

    @PostMapping("/lists/{listId}/holidays")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<Holiday> addHoliday(
            @PathVariable Long listId,
            @RequestBody Holiday holiday) {
        return ResponseEntity.ok(holidayService.addHoliday(listId, holiday));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<Holiday> updateHoliday(
            @PathVariable Long id,
            @RequestBody Holiday holiday) {
        return ResponseEntity.ok(holidayService.updateHoliday(id, holiday));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR', 'HR_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
