package com.example.hr_management_backend.features.dashboard.service;

import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.dashboard.dto.DashboardStatsResponse;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public DashboardStatsResponse getDashboardStats() {
        int totalEmployees = (int) employeeRepository.count();
        LocalDate today = LocalDate.now();

        // Map employee ID to Name
        Map<Long, String> employeeNameMap = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Employee::getId,
                        e -> e.getFirstName() + " " + e.getLastName(),
                        (existing, replacement) -> existing
                ));

        List<LeaveRequest> pendingList = leaveRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        List<DashboardStatsResponse.PendingLeaveDto> pendingLeaves = new ArrayList<>();

        for (LeaveRequest leave : pendingList) {
            String empName = employeeNameMap.getOrDefault(leave.getEmployeeId(), "Employee #" + leave.getEmployeeId());
            pendingLeaves.add(DashboardStatsResponse.PendingLeaveDto.builder()
                    .id(leave.getId())
                    .employeeName(empName)
                    .startDate(leave.getStartDate() != null ? leave.getStartDate().toString() : "")
                    .endDate(leave.getEndDate() != null ? leave.getEndDate().toString() : "")
                    .reason(leave.getReason() != null ? leave.getReason() : "N/A")
                    .build());
        }

        int onLeaveTodayCount = leaveRequestRepository.countActiveLeavesOnDate(today);
        int presentTodayCount = (int) attendanceRepository.countByDateAndStatus(today, "PRESENT");

        return DashboardStatsResponse.builder()
                .totalEmployees(totalEmployees)
                .presentToday(presentTodayCount)
                .onLeaveToday(onLeaveTodayCount)
                .pendingLeaves(pendingLeaves)
                .build();
    }
}

