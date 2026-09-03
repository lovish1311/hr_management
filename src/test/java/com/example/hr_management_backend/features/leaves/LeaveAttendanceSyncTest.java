package com.example.hr_management_backend.features.leaves;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.attendance.service.AttendanceService;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.leaves.service.LeaveService;
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

    @Test
    @DisplayName("Approving a leave should auto-sync ON_LEAVE status to Attendance and block check-in")
    void testLeaveApprovalSyncsAttendanceAndBlocksCheckIn() {
        Long testEmployeeId = 9999L;
        LocalDate today = LocalDate.now();

        // 1. Submit leave request for today
        LeaveRequest request = LeaveRequest.builder()
                .employeeId(testEmployeeId)
                .startDate(today)
                .endDate(today)
                .leaveType("CASUAL")
                .reason("Doctor Appointment")
                .build();

        LeaveRequest submitted = leaveService.applyForLeave(request);
        assertThat(submitted.getStatus()).isEqualTo("PENDING");

        // 2. Approve leave request
        LeaveRequest approved = leaveService.updateStatus(submitted.getId(), "APPROVED", null, 1L);
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
