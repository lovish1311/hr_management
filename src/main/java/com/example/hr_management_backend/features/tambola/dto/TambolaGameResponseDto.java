package com.example.hr_management_backend.features.tambola.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TambolaGameResponseDto {
    private Long id;
    private String roomCode;
    private String title;
    private String status;
    private Integer autoDrawIntervalSeconds;
    private Integer totalNumbersDrawn;
    private Integer lastDrawnNumber;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private int playerCount;
    @com.fasterxml.jackson.annotation.JsonProperty("isHost")
    private boolean isHost;
}
