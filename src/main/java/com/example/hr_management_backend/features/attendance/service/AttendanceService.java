package com.example.hr_management_backend.features.attendance.service;

import com.example.hr_management_backend.features.attendance.dto.AttendanceCalendarDayDto;
import com.example.hr_management_backend.features.attendance.dto.BiometricImportRowDto;
import com.example.hr_management_backend.features.attendance.dto.BiometricImportSummaryDto;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import com.example.hr_management_backend.features.settings.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {
    
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final SettingsService settingsService;

    @Transactional
    public Attendance markAttendance(Long employeeId, String status) {
        LocalDate today = LocalDate.now();

        // Check if employee has an approved leave today
        if (leaveRequestRepository.isEmployeeOnApprovedLeave(employeeId, today)) {
            throw new IllegalStateException("Cannot mark attendance: Employee is on approved leave today.");
        }

        Attendance attendance = attendanceRepository.findByEmployeeIdAndDate(employeeId, today)
                .orElseGet(() -> new Attendance(null, employeeId, today, LocalTime.now(), null, status));

        if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            attendance.setTotalWorkingMinutes((int) java.time.Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime()).toMinutes());
        }

        attendance.setCheckInTime(LocalTime.now());
        attendance.setStatus(status);

        return attendanceRepository.save(attendance);
    }

    @Transactional
    public void clearAllAttendance() {
        attendanceRepository.deleteAll();
        log.warn("All attendance records have been deleted from the database via clearAllAttendance()");
    }

    @Transactional
    public void syncLeaveToAttendance(Long employeeId, LocalDate startDate, LocalDate endDate) {
        syncLeaveToAttendance(employeeId, startDate, endDate, null, "CASUAL");
    }

    @Transactional
    public void syncLeaveToAttendance(Long employeeId, LocalDate startDate, LocalDate endDate, String leaveType) {
        syncLeaveToAttendance(employeeId, startDate, endDate, null, leaveType);
    }

    @Transactional
    public void syncLeaveToAttendance(Long employeeId, LocalDate startDate, LocalDate endDate, Double totalDays, String leaveType) {
        if (startDate == null || endDate == null) return;

        // Do not mark the day as ON_LEAVE if it is a time-based leave
        if (isShortBreak(leaveType) || isEarlyOut(leaveType) || isLateArrival(leaveType)) {
            // It's a time-based permission (late arrival, early out, short break)
            // Instead of marking ON_LEAVE, we must re-evaluate the attendance for these days retroactively.
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                reEvaluateAttendance(employeeId, current);
                current = current.plusDays(1);
            }
            return;
        }

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            final LocalDate dateToSync = current;

            // Skip weekends — leave does not consume weekend days
            if (dateToSync.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || dateToSync.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }

            // Use HALF_DAY_LEAVE for 0.5-day requests on single-day leaves
            boolean isSingleDay = startDate.equals(endDate);
            String leaveStatus = (isSingleDay && totalDays != null && totalDays == 0.5)
                    ? "HALF_DAY_LEAVE"
                    : "ON_LEAVE";

            Attendance attendance = attendanceRepository.findByEmployeeIdAndDate(employeeId, dateToSync)
                    .orElseGet(() -> new Attendance(null, employeeId, dateToSync, null, null, leaveStatus));

            attendance.setStatus(leaveStatus);
            attendanceRepository.save(attendance);
            log.info("Auto-synced attendance to {} for employeeId={} on date={}", leaveStatus, employeeId, dateToSync);

            current = current.plusDays(1);
        }
    }

    @Transactional
    public void removeLeaveFromAttendance(Long employeeId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return;

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            final LocalDate dateToSync = current;
            attendanceRepository.findByEmployeeIdAndDate(employeeId, dateToSync).ifPresent(attendance -> {
                if ("ON_LEAVE".equalsIgnoreCase(attendance.getStatus()) || "HALF_DAY_LEAVE".equalsIgnoreCase(attendance.getStatus())) {
                    if (attendance.getCheckInTime() != null || attendance.getCheckOutTime() != null) {
                        reEvaluateAttendance(employeeId, dateToSync);
                    } else {
                        attendanceRepository.delete(attendance);
                    }
                    log.info("Removed leave status from attendance for employeeId={} on date={}", employeeId, dateToSync);
                }
            });
            current = current.plusDays(1);
        }
    }

    @Transactional
    public void reEvaluateAttendance(Long employeeId, LocalDate date) {
        if (date.isAfter(LocalDate.now())) return; // Only re-evaluate past or current days

        attendanceRepository.findByEmployeeIdAndDate(employeeId, date).ifPresent(attendance -> {
            if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() == null) {
                // No punch data exists, we can't do anything retroactive
                return;
            }

            Employee matched = employeeRepository.findById(employeeId).orElse(null);
            if (matched == null) return;

            // Fetch approved leaves for this date
            List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesForEmployeeInRange(employeeId, date, date);
            LeaveRequest shortBreakLeave = approvedLeaves.stream().filter(l -> isShortBreak(l.getLeaveType())).findFirst().orElse(null);
            LeaveRequest earlyOutLeave = approvedLeaves.stream().filter(l -> isEarlyOut(l.getLeaveType())).findFirst().orElse(null);
            LeaveRequest lateArrivalLeave = approvedLeaves.stream().filter(l -> isLateArrival(l.getLeaveType())).findFirst().orElse(null);

            LocalTime firstIn = attendance.getCheckInTime();
            LocalTime lastOut = attendance.getCheckOutTime();
            String status = attendance.getStatus();

            // Evaluate Late Arrival
            LocalTime effectiveLateCutoff = getUniversalLateCutoff();
            if (matched.getLateArrivalAllowedUntil() != null) {
                effectiveLateCutoff = matched.getLateArrivalAllowedUntil();
            }

            if (firstIn != null) {
                if (shortBreakLeave != null) {
                    LocalTime bStart = shortBreakLeave.getStartTime() != null ? shortBreakLeave.getStartTime() : getUniversalShiftStart();
                    LocalTime bEnd = shortBreakLeave.getEndTime() != null ? shortBreakLeave.getEndTime() : LocalTime.of(11, 0);

                    if (bStart.isBefore(LocalTime.of(9, 45))) {
                        status = firstIn.isAfter(bEnd) ? "LATE" : "PRESENT";
                    } else {
                        status = firstIn.isAfter(effectiveLateCutoff) ? "LATE" : "PRESENT";
                    }
                } else if (lateArrivalLeave != null) {
                    LocalTime approvedLate = lateArrivalLeave.getEndTime() != null ? lateArrivalLeave.getEndTime() : LocalTime.of(10, 30);
                    status = firstIn.isAfter(approvedLate) ? "LATE" : "PRESENT";
                } else if (earlyOutLeave != null) {
                    status = firstIn.isAfter(effectiveLateCutoff) ? "LATE" : "PRESENT";
                } else {
                    status = firstIn.isAfter(effectiveLateCutoff) ? "LATE" : "PRESENT";
                }
            } else {
                status = "UNEXCUSED_ABSENT";
            }

            // Evaluate Early Out if present
            if ("PRESENT".equals(status) && lastOut != null) {
                LocalTime effectiveEarlyCutoff = getUniversalEarlyCutoff();
                if (matched.getEarlyOutAllowedAfter() != null) {
                    effectiveEarlyCutoff = matched.getEarlyOutAllowedAfter();
                }

                if (earlyOutLeave != null) {
                    LocalTime approvedEarlyStart = earlyOutLeave.getStartTime() != null ? earlyOutLeave.getStartTime() : LocalTime.of(16, 0);
                    if (lastOut.isBefore(approvedEarlyStart)) {
                        status = "LATE"; // Or "LEFT_EARLY" depending on how it's defined
                    }
                } else if (shortBreakLeave == null && lateArrivalLeave == null) {
                    // Only apply early out penalty if no other time-based leave is present?
                    // Actually, just standard logic:
                    if (lastOut.isBefore(effectiveEarlyCutoff)) {
                        status = "LATE"; // Consistent with parseAndCommitBiometricExcel
                    }
                }
            }

            // Update the attendance record
            attendance.setStatus(status);
            attendanceRepository.save(attendance);
            log.info("Retroactively re-evaluated attendance for employeeId={}, date={} -> status={}", employeeId, date, status);
        });
    }

    @Transactional(readOnly = true)
    public List<Attendance> getAttendanceHistory(Long employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId);
    }

    @Transactional(readOnly = true)
    public List<AttendanceCalendarDayDto> getMonthlyCalendarSummary(Long employeeId, int year, int month) {
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        LocalDate today = LocalDate.now();

        Optional<Employee> empOpt = employeeRepository.findById(employeeId);
        LocalTime customLateCutoff = empOpt.map(Employee::getLateArrivalAllowedUntil).orElse(null);
        LocalTime defaultLateCutoff = customLateCutoff != null ? customLateCutoff : getUniversalLateCutoff();

        // Fetch attendance for the month
        Map<LocalDate, Attendance> attendanceMap = attendanceRepository
                .findByEmployeeIdAndDateBetween(employeeId, startOfMonth, endOfMonth)
                .stream()
                .collect(Collectors.toMap(Attendance::getDate, a -> a, (e1, e2) -> e1));

        // Fetch approved and pending leave requests scoped to this month (P3: avoids loading entire history)
        List<LeaveRequest> leaves = leaveRequestRepository.findActiveAndPendingInRange(employeeId, startOfMonth, endOfMonth);

        List<AttendanceCalendarDayDto> summaryList = new ArrayList<>();

        for (LocalDate date = startOfMonth; !date.isAfter(endOfMonth); date = date.plusDays(1)) {
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            final LocalDate current = date;

            // Find ALL matching leaves for this date (not just the first one)
            List<LeaveRequest> dayLeaves = leaves.stream()
                    .filter(l -> l.getStartDate() != null && l.getEndDate() != null &&
                            !current.isBefore(l.getStartDate()) && !current.isAfter(l.getEndDate()))
                    .toList();

            // Separate full-day leaves from time-based (short break / early out)
            LeaveRequest fullDayLeave = dayLeaves.stream()
                    .filter(l -> !isShortBreak(l.getLeaveType()) && !isEarlyOut(l.getLeaveType()) && !isLateArrival(l.getLeaveType()))
                    .findFirst().orElse(null);
            LeaveRequest timeBasedLeave = dayLeaves.stream()
                    .filter(l -> isShortBreak(l.getLeaveType()) || isEarlyOut(l.getLeaveType()) || isLateArrival(l.getLeaveType()))
                    .findFirst().orElse(null);

            // Priority: full-day leave takes precedence for calendar status; time-based is secondary
            LeaveRequest activeLeave = fullDayLeave != null ? fullDayLeave : timeBasedLeave;

            Attendance attendance = attendanceMap.get(current);

            String status;
            String statusLabel;
            String leaveType = activeLeave != null ? activeLeave.getLeaveType() : null;
            LocalTime checkIn = attendance != null ? attendance.getCheckInTime() : null;
            LocalTime checkOut = attendance != null ? attendance.getCheckOutTime() : null;

            boolean isShortBreak = isShortBreak(leaveType);
            boolean isEarlyOut = isEarlyOut(leaveType);
            boolean isLateArrival = isLateArrival(leaveType);

            if (isWeekend) {
                status = "WEEKEND";
                statusLabel = "Weekend";
            } else if (activeLeave != null && "PENDING".equalsIgnoreCase(activeLeave.getStatus())) {
                status = "PENDING_LEAVE";
                statusLabel = "Pending " + (leaveType != null ? leaveType.toUpperCase().replaceAll("_", " ") : "Leave") + " Approval";
            } else if (activeLeave != null && !isShortBreak && !isEarlyOut && !isLateArrival) {
                // Full Day Leave
                if ("UNPAID".equalsIgnoreCase(leaveType)) {
                    status = "LOP_LEAVE";
                    statusLabel = "Loss of Pay (Unpaid Leave)";
                } else {
                    status = "PAID_LEAVE";
                    statusLabel = leaveType != null ? leaveType.toUpperCase().replaceAll("_", " ") + " Leave" : "Approved Leave";
                }
            } else if (activeLeave != null && isShortBreak) {
                LocalTime breakStart = activeLeave.getStartTime() != null ? activeLeave.getStartTime() : getUniversalShiftStart();
                LocalTime breakEnd = activeLeave.getEndTime() != null ? activeLeave.getEndTime() : LocalTime.of(11, 0);

                if (checkIn != null) {
                    if (breakStart.isBefore(LocalTime.of(9, 45))) {
                        if (!checkIn.isAfter(breakEnd)) {
                            status = "PRESENT";
                            statusLabel = "Present (Approved Short Break: " + formatTime(breakStart) + " - " + formatTime(breakEnd) + ")";
                        } else {
                            status = "LATE";
                            statusLabel = "Late Arrival (" + formatTime(checkIn) + " - Short Break ended " + formatTime(breakEnd) + ")";
                        }
                    } else {
                        if (checkIn.isAfter(defaultLateCutoff)) {
                            status = "LATE";
                            statusLabel = "Late Arrival (" + formatTime(checkIn) + ")";
                        } else {
                            status = "PRESENT";
                            statusLabel = "Present (Approved Mid-Day Break: " + formatTime(breakStart) + " - " + formatTime(breakEnd) + ")";
                        }
                    }
                } else if (current.isAfter(today)) {
                    status = "UPCOMING";
                    statusLabel = "Upcoming Working Day";
                } else {
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Absent (Approved Short Break: " + formatTime(breakStart) + " - " + formatTime(breakEnd) + ")";
                }
            } else if (activeLeave != null && isEarlyOut) {
                LocalTime approvedEarlyStart = activeLeave.getStartTime() != null ? activeLeave.getStartTime() : LocalTime.of(16, 0);

                if (checkIn != null) {
                    if (checkIn.isAfter(defaultLateCutoff)) {
                        status = "LATE";
                        statusLabel = "Late Arrival (" + formatTime(checkIn) + ")";
                    } else if (checkOut != null) {
                        if (!checkOut.isBefore(approvedEarlyStart)) {
                            status = "PRESENT";
                            statusLabel = "Present (Approved Early Out from " + formatTime(approvedEarlyStart) + ")";
                        } else {
                            status = "LATE";
                            statusLabel = "Left Early (" + formatTime(checkOut) + " - Approved from " + formatTime(approvedEarlyStart) + ")";
                        }
                    } else {
                        status = "PRESENT";
                        statusLabel = "Present (Approved Early Out from " + formatTime(approvedEarlyStart) + ")";
                    }
                } else if (current.isAfter(today)) {
                    status = "UPCOMING";
                    statusLabel = "Upcoming Working Day";
                } else {
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Absent (Approved Early Out)";
                }
            } else if (activeLeave != null && isLateArrival) {
                LocalTime approvedLate = activeLeave.getEndTime() != null ? activeLeave.getEndTime() : LocalTime.of(10, 30);
                if (checkIn != null) {
                    if (checkIn.isAfter(approvedLate)) {
                        status = "LATE";
                        statusLabel = "Late Arrival (" + formatTime(checkIn) + " - Approved until " + formatTime(approvedLate) + ")";
                    } else {
                        status = "PRESENT";
                        statusLabel = "Present (Approved Late Arrival up to " + formatTime(approvedLate) + ")";
                    }
                } else if (current.isAfter(today)) {
                    status = "UPCOMING";
                    statusLabel = "Upcoming Working Day";
                } else {
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Absent (Approved Late Arrival)";
                }
            } else if (attendance != null) {
                if (checkIn != null) {
                    if (checkIn.isAfter(defaultLateCutoff)) {
                        status = "LATE";
                        statusLabel = "Late Check-In (" + formatTime(checkIn) + ")";
                    } else {
                        status = "PRESENT";
                        statusLabel = customLateCutoff != null && checkIn.isAfter(getUniversalLateCutoff())
                                ? "Present (Late Exemption Granted)"
                                : "Present (" + formatTime(checkIn) + ")";
                    }
                } else if ("ON_LEAVE".equalsIgnoreCase(attendance.getStatus())) {
                    status = "PAID_LEAVE";
                    statusLabel = "On Leave";
                } else {
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Absent (LOP)";
                }
            } else if (current.isAfter(today)) {
                status = "UPCOMING";
                statusLabel = "Upcoming Working Day";
            } else {
                status = "UNEXCUSED_ABSENT";
                statusLabel = "Absent (LOP)";
            }

            summaryList.add(AttendanceCalendarDayDto.builder()
                    .date(current)
                    .status(status)
                    .statusLabel(statusLabel)
                    .leaveType(leaveType)
                    .checkInTime(checkIn)
                    .checkOutTime(checkOut)
                    .isWeekend(isWeekend)
                    .isHoliday(false)
                    .leaveRequestId(activeLeave != null ? activeLeave.getId() : null)
                    .totalWorkingMinutes(attendance != null ? attendance.getTotalWorkingMinutes() : 0)
                    .build());
        }

        return summaryList;
    }

    private boolean isShortBreak(String leaveType) {
        if (leaveType == null) return false;
        String normalized = leaveType.toUpperCase().replaceAll("[ _-]", "");
        return normalized.contains("SHORTBREAK") || normalized.contains("SHORTLEAVE");
    }

    private boolean isEarlyOut(String leaveType) {
        if (leaveType == null) return false;
        String normalized = leaveType.toUpperCase().replaceAll("[ _-]", "");
        return normalized.contains("EARLYOUT") || normalized.contains("EARLYLEAVE");
    }

    private boolean isLateArrival(String leaveType) {
        if (leaveType == null) return false;
        String normalized = leaveType.toUpperCase().replaceAll("[ _-]", "");
        return normalized.contains("LATEARRIVAL") || normalized.contains("LATEIN");
    }

    private String formatTime(LocalTime time) {
        if (time == null) return "";
        return time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
    }

    /**
     * Ephemeral parsing and batch execution of Biometric Punch Machine Excel File.
     * Processes file input stream directly in-memory and saves attendance batch atomically.
     */
    @Transactional
    public BiometricImportSummaryDto parseAndCommitBiometricExcel(MultipartFile file, LocalDate targetDate) {
        final LocalDate activeDate = targetDate != null ? targetDate : LocalDate.now();

        List<Employee> allEmployees = employeeRepository.findAll();
        Map<String, Employee> biometricNameMap = new HashMap<>();
        Map<String, Employee> fullNameMap = new HashMap<>();
        Map<String, Employee> firstNameMap = new HashMap<>();

        for (Employee emp : allEmployees) {
            if (emp.getBiometricName() != null && !emp.getBiometricName().isBlank()) {
                biometricNameMap.put(emp.getBiometricName().replaceAll("\\s+", " ").trim().toLowerCase(), emp);
            }
            String fullName = (emp.getFirstName() + " " + emp.getLastName()).replaceAll("\\s+", " ").trim().toLowerCase();
            fullNameMap.put(fullName, emp);
            firstNameMap.put(emp.getFirstName().replaceAll("\\s+", " ").trim().toLowerCase(), emp);
        }

        List<BiometricImportRowDto> parsedRows = new ArrayList<>();
        List<Attendance> attendanceToSave = new ArrayList<>();

        int presentCount = 0;
        int lateCount = 0;
        int absentCount = 0;
        int unmatchedCount = 0;

        try (Workbook workbook = getBeautifiedWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);

            int nameColIndex = 1;
            int startRowIndex = 1;

            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    if (cell != null) {
                        String cellVal = getCellValueAsString(cell).replaceAll("\\s+", " ").trim().toLowerCase();
                        if (cellVal.contains("name") || cellVal.contains("employee")) {
                            nameColIndex = c;
                            break;
                        }
                    }
                }
            }

            // Pre-fetch all approved leaves for target date in a single batch query (P3 perf optimization)
            Map<Long, List<LeaveRequest>> approvedLeavesMap = leaveRequestRepository
                    .findAllApprovedForDate(activeDate)
                    .stream()
                    .collect(Collectors.groupingBy(LeaveRequest::getEmployeeId));

            for (int r = startRowIndex; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Cell nameCell = row.getCell(nameColIndex);
                if (nameCell == null && nameColIndex > 0) nameCell = row.getCell(0);
                if (nameCell == null) continue;

                String rawName = getCellValueAsString(nameCell).replaceAll("\\s+", " ").trim();
                if (rawName.isBlank() || "S.No.".equalsIgnoreCase(rawName) || "Name".equalsIgnoreCase(rawName) || "S.No".equalsIgnoreCase(rawName) || "Employee Name".equalsIgnoreCase(rawName)) {
                    continue;
                }

                List<LocalTime> times = new ArrayList<>();
                for (int c = nameColIndex + 1; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        LocalTime parsed = parseTimeCell(cell);
                        if (parsed != null) {
                            times.add(parsed);
                        }
                    }
                }

                String key = rawName.toLowerCase();
                Employee matched = biometricNameMap.get(key);
                if (matched == null) matched = fullNameMap.get(key);
                if (matched == null) matched = firstNameMap.get(key);
                if (matched == null && key.contains(" ")) {
                    String firstToken = key.split(" ")[0];
                    matched = firstNameMap.get(firstToken);
                }

                LocalTime firstIn = !times.isEmpty() ? times.get(0) : null;
                LocalTime lastOut = times.size() > 1 ? times.get(times.size() - 1) : null;

                LocalTime lateCutoff = getUniversalLateCutoff();
                if (matched != null && matched.getLateArrivalAllowedUntil() != null) {
                    lateCutoff = matched.getLateArrivalAllowedUntil();
                }

                String status;
                String statusLabel;

                LocalTime effectiveLateCutoff = (matched != null && matched.getLateArrivalAllowedUntil() != null)
                        ? matched.getLateArrivalAllowedUntil()
                        : lateCutoff;

                if (matched == null) {
                    unmatchedCount++;
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Unmatched Biometric Name (" + rawName + ")";
                } else if (Boolean.FALSE.equals(matched.getIsAttendanceTracked())) {
                    status = "EXEMPT";
                    statusLabel = "Tracking Disabled";
                } else if (firstIn == null) {
                    absentCount++;
                    status = "UNEXCUSED_ABSENT";
                    statusLabel = "Absent (No Punches)";
                } else {
                    // Check active approved leaves for short break / early out / full day leave (using pre-fetched map)
                    List<LeaveRequest> approvedLeaves = approvedLeavesMap.getOrDefault(matched.getId(), List.of());
                    LeaveRequest fullDayLeave = approvedLeaves.stream()
                            .filter(l -> !isShortBreak(l.getLeaveType()) && !isEarlyOut(l.getLeaveType()) && !isLateArrival(l.getLeaveType()))
                            .findFirst().orElse(null);
                    LeaveRequest shortBreakLeave = approvedLeaves.stream().filter(l -> isShortBreak(l.getLeaveType())).findFirst().orElse(null);
                    LeaveRequest earlyOutLeave = approvedLeaves.stream().filter(l -> isEarlyOut(l.getLeaveType())).findFirst().orElse(null);
                    LeaveRequest lateArrivalLeave = approvedLeaves.stream().filter(l -> isLateArrival(l.getLeaveType())).findFirst().orElse(null);

                    if (fullDayLeave != null) {
                        boolean isHalfDay = fullDayLeave.getTotalDays() != null && fullDayLeave.getTotalDays() == 0.5;
                        status = isHalfDay ? "HALF_DAY_LEAVE" : "ON_LEAVE";
                        statusLabel = (isHalfDay ? "Half Day Leave (" : "On Approved Leave (") + fullDayLeave.getLeaveType().toUpperCase().replaceAll("_", " ") + ") - Biometric Punched";
                    } else if (shortBreakLeave != null) {
                        LocalTime bStart = shortBreakLeave.getStartTime() != null ? shortBreakLeave.getStartTime() : getUniversalShiftStart();
                        LocalTime bEnd = shortBreakLeave.getEndTime() != null ? shortBreakLeave.getEndTime() : LocalTime.of(11, 0);
                        if (bStart.isBefore(LocalTime.of(9, 45))) {
                            if (!firstIn.isAfter(bEnd)) {
                                presentCount++;
                                status = "PRESENT";
                                statusLabel = "Present (Approved Short Break: " + formatTime(bStart) + " - " + formatTime(bEnd) + ")";
                            } else {
                                lateCount++;
                                status = "LATE";
                                statusLabel = "Late Arrival (" + formatTime(firstIn) + " - Short Break ended " + formatTime(bEnd) + ")";
                            }
                        } else {
                            if (firstIn.isAfter(effectiveLateCutoff)) {
                                lateCount++;
                                status = "LATE";
                                statusLabel = "Late Arrival (" + formatTime(firstIn) + ")";
                            } else {
                                presentCount++;
                                status = "PRESENT";
                                statusLabel = "Present (Approved Short Break)";
                            }
                        }
                    } else if (lateArrivalLeave != null) {
                        LocalTime approvedLate = lateArrivalLeave.getEndTime() != null ? lateArrivalLeave.getEndTime() : LocalTime.of(10, 30);
                        if (firstIn.isAfter(approvedLate)) {
                            lateCount++;
                            status = "LATE";
                            statusLabel = "Late Arrival (" + formatTime(firstIn) + " - Approved until " + formatTime(approvedLate) + ")";
                        } else {
                            presentCount++;
                            status = "PRESENT";
                            statusLabel = "Present (Approved Late Arrival up to " + formatTime(approvedLate) + ")";
                        }
                    } else if (earlyOutLeave != null) {
                        LocalTime approvedEarly = earlyOutLeave.getStartTime() != null ? earlyOutLeave.getStartTime() : LocalTime.of(16, 0);
                        if (firstIn.isAfter(effectiveLateCutoff)) {
                            lateCount++;
                            status = "LATE";
                            statusLabel = "Late Arrival (" + formatTime(firstIn) + ")";
                        } else {
                            presentCount++;
                            status = "PRESENT";
                            statusLabel = "Present (Approved Early Out from " + formatTime(approvedEarly) + ")";
                        }
                    } else if (firstIn.isAfter(effectiveLateCutoff)) {
                        lateCount++;
                        status = "LATE";
                        statusLabel = "Late Arrival (" + formatTime(firstIn) + ")";
                    } else {
                        LocalTime effectiveEarlyCutoff = (matched != null && matched.getEarlyOutAllowedAfter() != null)
                                ? matched.getEarlyOutAllowedAfter()
                                : getUniversalEarlyCutoff();
                        
                        if (lastOut != null && lastOut.isBefore(effectiveEarlyCutoff)) {
                            lateCount++;
                            status = "LATE";
                            statusLabel = "Left Early (" + formatTime(lastOut) + ")";
                        } else {
                            presentCount++;
                            status = "PRESENT";
                            if (matched != null && matched.getLateArrivalAllowedUntil() != null && firstIn.isAfter(lateCutoff)) {
                                statusLabel = "Present (Late Exemption Granted)";
                            } else if (matched != null && matched.getEarlyOutAllowedAfter() != null && lastOut != null && lastOut.isBefore(getUniversalEarlyCutoff())) {
                                statusLabel = "Present (Early Out Exemption Granted)";
                            } else {
                                statusLabel = "Present (" + formatTime(firstIn) + ")";
                            }
                        }
                    }
                }

                if (matched != null) {
                    List<LeaveRequest> appLeaves = approvedLeavesMap.getOrDefault(matched.getId(), List.of());
                    boolean onLeave = appLeaves.stream().anyMatch(l -> !isShortBreak(l.getLeaveType()) && !isEarlyOut(l.getLeaveType()) && !isLateArrival(l.getLeaveType()));
                    if (onLeave) {
                        status = "PAID_LEAVE";
                        statusLabel = "On Approved Leave (Punches Logged)";
                    } 
                    
                    final Long empId = matched.getId();
                    final LocalTime inTime = firstIn;
                    final LocalTime outTime = lastOut;
                    final String attStatus = status;

                    Attendance att = attendanceRepository.findByEmployeeIdAndDate(empId, activeDate)
                            .orElseGet(() -> new Attendance(null, empId, activeDate, inTime, outTime, attStatus));
                    att.setCheckInTime(firstIn);
                    att.setCheckOutTime(lastOut);
                    if (att.getCheckInTime() != null && att.getCheckOutTime() != null) {
                        att.setTotalWorkingMinutes((int) java.time.Duration.between(att.getCheckInTime(), att.getCheckOutTime()).toMinutes());
                    }
                    att.setStatus(status);
                    attendanceToSave.add(att);
                }

                parsedRows.add(BiometricImportRowDto.builder()
                        .employeeName(rawName)
                        .matchedEmployeeId(matched != null ? matched.getId() : null)
                        .matchedEmployeeName(matched != null ? matched.getFirstName() + " " + matched.getLastName() : null)
                        .firstInTime(firstIn)
                        .lastOutTime(lastOut)
                        .status(status)
                        .statusLabel(statusLabel)
                        .isMatched(matched != null)
                        .build());
            }

            if (!attendanceToSave.isEmpty()) {
                attendanceRepository.saveAll(attendanceToSave);
                log.info("Batch imported biometric attendance: saved {} records for date={}", attendanceToSave.size(), activeDate);
            }

        } catch (Exception e) {
            log.error("Failed to parse biometric excel file: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing biometric Excel file: " + e.getMessage());
        }

        return BiometricImportSummaryDto.builder()
                .targetDate(activeDate)
                .totalProcessed(parsedRows.size())
                .presentCount(presentCount)
                .lateCount(lateCount)
                .absentCount(absentCount)
                .unmatchedCount(unmatchedCount)
                .rows(parsedRows)
                .build();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue().toString() : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private LocalTime parseTimeCell(Cell cell) {
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    return LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
                } else {
                    double val = cell.getNumericCellValue();
                    if (val > 0 && val < 1.0) { // Excel fractional day representation of time
                        long totalSec = Math.round(val * 86400.0);
                        return LocalTime.ofSecondOfDay(totalSec % 86400);
                    }
                }
            }

            String text = getCellValueAsString(cell);
            if (text == null) return null;
            text = text.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ENGLISH);
            if (text.isBlank() || "-".equals(text) || text.contains("#")) return null;

            if (text.contains("AM") || text.contains("PM")) {
                DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("[hh:mm:ss a][hh:mm a][h:m:s a][h:m a][hh:mma][h:ma]")
                        .toFormatter(Locale.ENGLISH);
                return LocalTime.parse(text, fmt);
            } else {
                DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("[HH:mm:ss][HH:mm][H:m:s][H:m]")
                        .toFormatter(Locale.ENGLISH);
                return LocalTime.parse(text, fmt);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime getUniversalShiftStart() {
        try {
            return LocalTime.parse(settingsService.getSetting("shiftStartTime"));
        } catch (Exception e) {
            return LocalTime.of(9, 0);
        }
    }

    private LocalTime getUniversalShiftEnd() {
        try {
            return LocalTime.parse(settingsService.getSetting("shiftEndTime"));
        } catch (Exception e) {
            return LocalTime.of(18, 0);
        }
    }

    private LocalTime getUniversalLateCutoff() {
        try {
            String graceStr = settingsService.getSetting("lateArrivalGraceMinutes");
            long grace = (graceStr != null && !graceStr.isEmpty()) ? Long.parseLong(graceStr) : 15L;
            return getUniversalShiftStart().plusMinutes(grace);
        } catch (Exception e) {
            return LocalTime.of(10, 15);
        }
    }

    private LocalTime getUniversalEarlyCutoff() {
        try {
            String graceStr = settingsService.getSetting("earlyOutGraceMinutes");
            long grace = (graceStr != null && !graceStr.isEmpty()) ? Long.parseLong(graceStr) : 0L;
            return LocalTime.of(18, 0);
        } catch (Exception e) {
            return LocalTime.of(18, 0);
        }
    }

    private Workbook getBeautifiedWorkbook(MultipartFile file) throws IOException {
        String scriptPath = "C:\\Users\\Lovish\\Projects\\attendance_beautifier.py";
        java.io.File scriptFile = new java.io.File(scriptPath);

        if (scriptFile.exists()) {
            java.io.File tempDir = new java.io.File(System.getProperty("java.io.tmpdir"));
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "raw.xlsx";
            String ext = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".xlsx";
            
            java.io.File rawFile = java.io.File.createTempFile("biometric_raw_", ext, tempDir);
            java.io.File beautifiedFile = java.io.File.createTempFile("biometric_beautified_", ".xlsx", tempDir);
            
            try {
                file.transferTo(rawFile);

                String pythonExecutable = "C:\\Users\\Lovish\\AppData\\Local\\Programs\\Python\\Python312\\python.exe";
                if (!new java.io.File(pythonExecutable).exists()) {
                    pythonExecutable = "python";
                }

                ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath, rawFile.getAbsolutePath(), beautifiedFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);

                if (finished && process.exitValue() == 0 && beautifiedFile.exists() && beautifiedFile.length() > 0) {
                    log.info("Auto-beautified biometric Excel file successfully via python attendance_beautifier.py!");
                    return WorkbookFactory.create(beautifiedFile);
                } else {
                    log.warn("Python beautifier script did not finish or exited with error. Using original file stream.");
                }
            } catch (Exception e) {
                log.warn("Failed to execute python attendance_beautifier.py: {}. Falling back to original stream.", e.getMessage());
            } finally {
                rawFile.deleteOnExit();
                beautifiedFile.deleteOnExit();
            }
        }
        return WorkbookFactory.create(file.getInputStream());
    }
}




