package com.example.hr_management_backend.features.scribbil.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuessResultDto {
    private String roomCode;
    private Long employeeId;
    private String employeeName;
    private String guess;
    private Boolean isCorrect;
    private Boolean isClose;
    private Integer pointsAwarded;
    private Integer totalScore;
    private String message;
}
