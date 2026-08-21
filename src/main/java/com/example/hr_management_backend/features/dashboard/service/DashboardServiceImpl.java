package com.example.hr_management_backend.features.dashboard.service;

import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.dashboard.dto.DashboardStatsResponse;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        // Map employee ID to Name
        Map<Long, String> employeeNameMap = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Employee::getId,
                        e -> e.getFirstName() + " " + e.getLastName(),
                        (existing, replacement) -> existing
                ));

        List<LeaveRequest> allLeaves = leaveRequestRepository.findAll();
        List<DashboardStatsResponse.PendingLeaveDto> pendingLeaves = new ArrayList<>();

        int onLeaveTodayCount = 0;

        for (LeaveRequest leave : allLeaves) {
            if ("PENDING".equalsIgnoreCase(leave.getStatus())) {
                String empName = employeeNameMap.getOrDefault(leave.getEmployeeId(), "Employee #" + leave.getEmployeeId());
                pendingLeaves.add(DashboardStatsResponse.PendingLeaveDto.builder()
                        .id(leave.getId())
                        .employeeName(empName)
                        .startDate(leave.getStartDate() != null ? leave.getStartDate().toString() : "")
                        .endDate(leave.getEndDate() != null ? leave.getEndDate().toString() : "")
                        .reason(leave.getReason() != null ? leave.getReason() : "N/A")
                        .build());
            } else if ("APPROVED".equalsIgnoreCase(leave.getStatus())) {
                onLeaveTodayCount++;
            }
        }

        int presentToday = Math.max(0, totalEmployees - onLeaveTodayCount);

        return DashboardStatsResponse.builder()
                .totalEmployees(totalEmployees)
                .presentToday(presentToday)
                .onLeaveToday(onLeaveTodayCount)
                .pendingLeaves(pendingLeaves)
                .build();
    }
}
