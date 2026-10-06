package com.example.hr_management_backend.features.scribbil.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessPlayerDto {
    private Long id;
    private String roomCode;
    private Long employeeId;
    private String employeeName;
    private String avatarUrl;
    private Integer score;
    private Integer turnScore;
    private Boolean hasGuessedCorrectly;
    private Boolean isDrawer;
    private Boolean isHost;
    private Boolean isConnected;
    private Integer turnOrder;
    private LocalDateTime joinedAt;
}
