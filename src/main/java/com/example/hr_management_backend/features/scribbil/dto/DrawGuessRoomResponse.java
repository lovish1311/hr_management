package com.example.hr_management_backend.features.scribbil.dto;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessGameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessRoomResponse {
    private Long id;
    private String roomCode;
    private String roomName;
    private Long hostEmployeeId;
    private String hostName;
    private DrawGuessGameState state;
    private Integer maxRounds;
    private Integer drawTimeSeconds;
    private Integer wordChoiceCount;
    private String category;
    private Boolean customWordsOnly;
    private Integer currentRound;
    private Integer currentTurnIndex;
    private Long activeDrawerEmployeeId;
    private String currentHint;
    private Integer remainingSeconds;
    private List<DrawGuessPlayerDto> players;
    private LocalDateTime createdAt;
}
