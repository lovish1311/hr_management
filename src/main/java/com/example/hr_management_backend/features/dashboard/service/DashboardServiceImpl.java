package com.example.hr_management_backend.features.dashboard.service;

import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.dashboard.dto.DashboardStatsResponse;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Qualifier("dbExecutor")
    private final Executor dbExecutor;

    @Override
    public DashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now();

        // ── PARALLEL QUERY EXECUTION ──────────────────────────────────────────
        // 1. Total active employees count
        CompletableFuture<Integer> totalEmployeesFuture = CompletableFuture.supplyAsync(
                () -> (int) employeeRepository.count(),
                dbExecutor
        );

        // 2. Present today count (leveraging indexed idx_attendance_date_status)
        CompletableFuture<Integer> presentTodayFuture = CompletableFuture.supplyAsync(
                () -> (int) attendanceRepository.countByDateAndStatus(today, "PRESENT"),
                dbExecutor
        );

        // 3. On-leave today count (leveraging indexed idx_leave_status_dates)
        CompletableFuture<Integer> onLeaveTodayFuture = CompletableFuture.supplyAsync(
                () -> leaveRequestRepository.countActiveLeavesOnDate(today),
                dbExecutor
        );

        // 4. Pending leaves with employee names in a SINGLE DB JOIN trip (eliminates employeeRepository.findAll() N+1)
        CompletableFuture<List<DashboardStatsResponse.PendingLeaveDto>> pendingLeavesFuture = CompletableFuture.supplyAsync(
                () -> {
                    List<Object[]> rows = leaveRequestRepository.findPendingLeavesWithEmployeeDetails();
                    List<DashboardStatsResponse.PendingLeaveDto> list = new ArrayList<>(rows.size());
                    for (Object[] row : rows) {
                        if (row != null && row.length >= 5) {
                            Long id = row[0] instanceof Number ? ((Number) row[0]).longValue() : null;
                            String empName = row[1] != null ? row[1].toString() : "Employee";
                            String startDate = row[2] != null ? row[2].toString() : "";
                            String endDate = row[3] != null ? row[3].toString() : "";
                            String reason = row[4] != null ? row[4].toString() : "N/A";

                            list.add(DashboardStatsResponse.PendingLeaveDto.builder()
                                    .id(id)
                                    .employeeName(empName)
                                    .startDate(startDate)
                                    .endDate(endDate)
                                    .reason(reason)
                                    .build());
                        }
                    }
                    return list;
                },
                dbExecutor
        );

        // Await all parallel futures concurrently
        CompletableFuture.allOf(totalEmployeesFuture, presentTodayFuture, onLeaveTodayFuture, pendingLeavesFuture).join();

        try {
            return DashboardStatsResponse.builder()
                    .totalEmployees(totalEmployeesFuture.get())
                    .presentToday(presentTodayFuture.get())
                    .onLeaveToday(onLeaveTodayFuture.get())
                    .pendingLeaves(pendingLeavesFuture.get())
                    .build();
        } catch (Exception e) {
            log.error("Error collecting parallel dashboard metrics, falling back to synchronous defaults", e);
            throw new RuntimeException("Failed to compute dashboard stats", e);
        }
    }
}
