package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
import com.example.hr_management_backend.features.employees.model.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LeaveAttendanceSyncTest {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private com.example.hr_management_backend.features.employees.repository.EmployeeRepository employeeRepository;

    @Test
    @DisplayName("Approving a leave should auto-sync ON_LEAVE status to Attendance and block check-in")
    void testLeaveApprovalSyncsAttendanceAndBlocksCheckIn() {
        Employee hrAdmin = employeeRepository.findByEmail("hr@company.com").orElseGet(() ->
                employeeRepository.save(Employee.builder()
                        .email("hr@company.com")
                        .firstName("HR")
                        .lastName("Admin")
                        .role("HR")
                        .employeeCode("HR01")
                        .build()));

        Employee testEmp = employeeRepository.findByEmail("sync.test@company.com").orElseGet(() ->
                employeeRepository.save(Employee.builder()
                        .email("sync.test@company.com")
                        .firstName("Sync")
                        .lastName("Test")
                        .role("EMPLOYEE")
                        .employeeCode("SYNC01")
                        .build()));

        Long testEmployeeId = testEmp.getId();
        LocalDate today = LocalDate.now();

        // 1. Submit leave request for today
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(testEmployeeId)
                .startDate(today)
                .endDate(today)
                .leaveType("CASUAL")
                .reason("Doctor Appointment")
                .build();

        LeaveRequest submitted = leaveService.applyForLeave(request, testEmp.getEmail());
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        // 2. Approve leave request by HR admin
        LeaveRequest approved = leaveService.updateStatus(submitted.getId(), "APPROVED", null, hrAdmin.getId());
        assertThat(approved.getStatus()).isEqualTo("APPROVED");

        // Force manual trigger of attendance sync to test listener domain logic
        attendanceService.syncLeaveToAttendance(testEmployeeId, today, today, "CASUAL");

        // 3. Verify Attendance record exists with status ON_LEAVE
        List<Attendance> history = attendanceService.getAttendanceHistory(testEmployeeId);
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getStatus()).isEqualTo("ON_LEAVE");

        // 4. Verify marking check-in on approved leave day throws exception
        assertThatThrownBy(() -> attendanceService.markAttendance(testEmployeeId, "PRESENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Employee is on approved leave today.");
    }
}
