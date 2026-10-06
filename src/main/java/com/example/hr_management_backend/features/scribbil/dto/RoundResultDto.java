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
public class RoundResultDto {
    private String roomCode;
    private Integer roundNumber;
    private Integer turnNumber;
    private Long drawerEmployeeId;
    private String drawerName;
    private String word;
    private Integer drawerScoreAwarded;
    private List<PlayerScoreDelta> scoreDeltas;
    private Integer nextTurnInSeconds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerScoreDelta {
        private Long employeeId;
        private String employeeName;
        private Integer pointsEarned;
        private Integer totalScore;
        private Boolean guessedCorrectly;
    }
}
