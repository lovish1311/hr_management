package com.example.hr_management_backend.features.attendance;

import com.example.hr_management_backend.features.attendance.dto.AttendanceCalendarDayDto;
import com.example.hr_management_backend.features.attendance.dto.BiometricImportSummaryDto;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AttendanceServiceTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        testEmployee = employeeRepository.save(Employee.builder()
                .firstName("Karan")
                .lastName("Kapoor")
                .email("karan.attendance." + UUID.randomUUID() + "@company.com")
                .employeeCode("EMP-ATT-01")
                .department("Engineering")
                .designation("Software Engineer")
                .role("EMPLOYEE")
                .isAttendanceTracked(true)
                .build());
    }

    @Test
    @DisplayName("Test 1: Check-in records first punch and preserves it on subsequent punches (Rule 2)")
    void testCheckInNonDestructive() {
        LocalTime firstPunch = LocalTime.of(9, 30);
        Attendance att1 = attendanceService.checkIn(testEmployee.getId(), firstPunch);
        assertNotNull(att1.getId());
        assertEquals(firstPunch, att1.getCheckInTime());
        assertEquals("PRESENT", att1.getStatus());

        // Subsequent check-in at 10:45 AM must preserve the original 9:30 AM check-in
        LocalTime secondPunch = LocalTime.of(10, 45);
        Attendance att2 = attendanceService.checkIn(testEmployee.getId(), secondPunch);
        assertEquals(att1.getId(), att2.getId());
        assertEquals(firstPunch, att2.getCheckInTime(), "Original check-in time must never be overwritten");
    }

    @Test
    @DisplayName("Test 2: Late arrival punch after grace cutoff classified as LATE (Rule 3 & 4)")
    void testCheckInLateArrival() {
        // Universal shift start is 10:00 with 15-min grace -> late cutoff is 10:15
        LocalTime latePunch = LocalTime.of(10, 25);
        Attendance att = attendanceService.checkIn(testEmployee.getId(), latePunch);
        assertEquals("LATE", att.getStatus());
    }

    @Test
    @DisplayName("Test 3: Late arrival punch waived by approved LATE_ARRIVAL leave (Rule 4 & 6)")
    void testCheckInWithApprovedLateWaiver() {
        LocalDate today = LocalDate.now();
        leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("LATE_ARRIVAL")
                .startDate(today)
                .endDate(today)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .totalDays(0.0)
                .status("APPROVED")
                .build());

        // Check-in at 10:30 AM (before waiver expires at 11:00 AM)
        LocalTime punch = LocalTime.of(10, 30);
        Attendance att = attendanceService.checkIn(testEmployee.getId(), punch);
        assertEquals("PRESENT", att.getStatus(), "Approved late waiver must maintain PRESENT status");
    }

    @Test
    @DisplayName("Test 4: Check-in blocked when employee is on approved full-day leave (Rule 6)")
    void testCheckInBlockedOnApprovedLeave() {
        LocalDate today = LocalDate.now();
        leaveRequestRepository.save(LeaveRequest.builder()
                .employeeId(testEmployee.getId())
                .leaveType("CASUAL")
                .startDate(today)
                .endDate(today)
                .totalDays(1.0)
                .status("APPROVED")
                .build());

        assertThrows(IllegalStateException.class, () ->
                attendanceService.checkIn(testEmployee.getId(), LocalTime.of(9, 30)));
    }

    @Test
    @DisplayName("Test 5: Check-out under 4 hours (240 mins) duration classified as HALF_DAY (Rule 5)")
    void testCheckOutUnderFourHoursMarkedHalfDay() {
        // Check-in at 10:00 AM
        attendanceService.checkIn(testEmployee.getId(), LocalTime.of(10, 0));

        // Check-out at 1:00 PM (180 mins < 240 mins)
        Attendance outAtt = attendanceService.checkOut(testEmployee.getId(), LocalTime.of(13, 0));
        assertEquals(180, outAtt.getTotalWorkingMinutes());
        assertEquals("HALF_DAY", outAtt.getStatus(), "Working under 240 mins must be classified as HALF_DAY");
    }

    @Test
    @DisplayName("Test 6: Check-out standard full day duration >= 8 hours (Rule 5)")
    void testCheckOutStandardFullDay() {
        // Check-in at 9:30 AM
        attendanceService.checkIn(testEmployee.getId(), LocalTime.of(9, 30));

        // Check-out at 19:00 PM (570 mins)
        Attendance outAtt = attendanceService.checkOut(testEmployee.getId(), LocalTime.of(19, 0));
        assertEquals(570, outAtt.getTotalWorkingMinutes());
        assertEquals("PRESENT", outAtt.getStatus());
    }

    @Test
    @DisplayName("Test 7: Check-out before check-in throws IllegalStateException (Rule 2)")
    void testCheckOutWithoutCheckInThrows() {
        assertThrows(IllegalStateException.class, () ->
                attendanceService.checkOut(testEmployee.getId(), LocalTime.of(19, 0)));
    }

    @Test
    @DisplayName("Test 8: Monthly calendar summary marks weekends and leaves properly (Rule 11)")
    void testMonthlyCalendarSummary() {
        // October 2026 calendar summary
        List<AttendanceCalendarDayDto> days = attendanceService.getMonthlyCalendarSummary(testEmployee.getId(), 2026, 10);
        assertNotNull(days);
        assertEquals(31, days.size());

        // Verify Sundays are marked WEEKEND
        AttendanceCalendarDayDto sunday = days.stream()
                .filter(d -> d.getDate().getDayOfWeek() == java.time.DayOfWeek.SUNDAY)
                .findFirst()
                .orElseThrow();
        assertEquals("WEEKEND", sunday.getStatus());

        // Verify Saturdays are marked WEEKEND
        AttendanceCalendarDayDto saturday = days.stream()
                .filter(d -> d.getDate().getDayOfWeek() == java.time.DayOfWeek.SATURDAY)
                .findFirst()
                .orElseThrow();
        assertEquals("WEEKEND", saturday.getStatus());
    }

    @Test
    @DisplayName("Test 9: Pure JVM Biometric Excel Import with Multi-Row Deduplication (Rule 7, 8, 9)")
    void testBiometricExcelImportAndDeduplication() throws Exception {
        LocalDate importDate = LocalDate.of(2026, 10, 15);

        // Build an in-memory Excel workbook with 2 rows for Karan Kapoor (simulating split/repeated punches)
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Punches");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("S.No");
            header.createCell(1).setCellValue("Employee Name");
            header.createCell(2).setCellValue("In 1");
            header.createCell(3).setCellValue("Out 1");

            // Row 1: Karan Kapoor morning punch
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue(1);
            row1.createCell(1).setCellValue(testEmployee.getFirstName() + " " + testEmployee.getLastName());
            row1.createCell(2).setCellValue("09:30 AM");
            row1.createCell(3).setCellValue("01:00 PM");

            // Row 2: Karan Kapoor afternoon punch (duplicate entry in raw machine dump)
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue(2);
            row2.createCell(1).setCellValue(testEmployee.getFirstName() + " " + testEmployee.getLastName());
            row2.createCell(2).setCellValue("02:00 PM");
            row2.createCell(3).setCellValue("07:00 PM");

            wb.write(bos);
            excelBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "biometric_sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        BiometricImportSummaryDto summary = attendanceService.parseAndCommitBiometricExcel(file, importDate);
        assertNotNull(summary);

        // Must successfully deduplicate: only 1 attendance record in DB for Karan Kapoor on Oct 15
        List<Attendance> records = attendanceRepository.findByEmployeeIdAndDateBetween(testEmployee.getId(), importDate, importDate);
        assertEquals(1, records.size(), "Deduplication must result in exactly 1 attendance record per employee per day");

        Attendance saved = records.get(0);
        assertEquals(LocalTime.of(9, 30), saved.getCheckInTime(), "Earliest punch must be captured as firstIn");
        assertEquals(LocalTime.of(19, 0), saved.getCheckOutTime(), "Latest punch must be captured as lastOut");
        assertEquals("PRESENT", saved.getStatus());
    }

    @Test
    @DisplayName("Test 10: Database Unique Constraint Enforces 1 Record Per Day (Rule 1)")
    void testUniqueConstraintEnforcement() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        attendanceRepository.saveAndFlush(new Attendance(null, testEmployee.getId(), date, LocalTime.of(9, 0), LocalTime.of(18, 0), "PRESENT"));

        // Inserting a second record with identical employeeId and date must throw DataIntegrityViolationException
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            attendanceRepository.saveAndFlush(new Attendance(null, testEmployee.getId(), date, LocalTime.of(10, 0), LocalTime.of(19, 0), "LATE"));
        });
    }

    @Test
    @DisplayName("Test 11: Mid-Month Hire Pre-Joining Days Marked PRE_JOINING Without LOP / Unexcused Absences")
    void testPreJoiningDaysAreNotMarkedAsLopOrAbsent() {
        LocalDate joiningDate = LocalDate.of(2026, 10, 15);
        Employee midMonthEmployee = employeeRepository.save(Employee.builder()
                .firstName("Aarav")
                .lastName("Mehta")
                .email("aarav.joining." + UUID.randomUUID() + "@company.com")
                .employeeCode("EMP-JOIN-01")
                .department("Engineering")
                .designation("DevOps Engineer")
                .role("EMPLOYEE")
                .isAttendanceTracked(true)
                .joiningDate(joiningDate)
                .build());

        List<AttendanceCalendarDayDto> calendar = attendanceService.getMonthlyCalendarSummary(midMonthEmployee.getId(), 2026, 10);
        assertNotNull(calendar);
        assertEquals(31, calendar.size());

        // Days 1 through 14 must be PRE_JOINING, NOT UNEXCUSED_ABSENT or LOP_LEAVE
        for (int day = 1; day <= 14; day++) {
            LocalDate d = LocalDate.of(2026, 10, day);
            AttendanceCalendarDayDto dayDto = calendar.stream()
                    .filter(c -> c.getDate().equals(d))
                    .findFirst()
                    .orElseThrow();
            assertEquals("PRE_JOINING", dayDto.getStatus(), "Day " + day + " prior to joining date must be PRE_JOINING");
            assertEquals("Pre-Joining", dayDto.getStatusLabel());
        }

        // On or after joining date (Oct 15), if no punch and in past, it is not PRE_JOINING
        AttendanceCalendarDayDto joinDayDto = calendar.stream()
                .filter(c -> c.getDate().equals(joiningDate))
                .findFirst()
                .orElseThrow();
        assertNotEquals("PRE_JOINING", joinDayDto.getStatus(), "Joining date must not be PRE_JOINING");
    }
}
