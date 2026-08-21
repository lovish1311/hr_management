package com.example.hr_management_backend.features.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    private int totalEmployees;
    private int presentToday;
    private int onLeaveToday;
    private List<PendingLeaveDto> pendingLeaves;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingLeaveDto {
        private Long id;
        private String employeeName;
        private String startDate;
        private String endDate;
        private String reason;
    }
}
