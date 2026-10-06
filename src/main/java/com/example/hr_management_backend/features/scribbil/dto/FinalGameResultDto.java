package com.example.hr_management_backend.features.scribbil.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalGameResultDto {
    private String roomCode;
    private Integer totalRounds;
    private List<PodiumPlayerDto> leaderboard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PodiumPlayerDto {
        private Integer rank; // 1, 2, 3...
        private Long employeeId;
        private String employeeName;
        private String avatarUrl;
        private Integer totalScore;
    }
}
