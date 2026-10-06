package com.example.hr_management_backend.features.scribbil.dto;

import com.example.hr_management_backend.features.scribbil.model.DrawGuessGameState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessGameStateDto {
    private String roomCode;
    private DrawGuessGameState state;
    private Integer currentRound;
    private Integer maxRounds;
    private Integer currentTurnIndex;
    private Long activeDrawerEmployeeId;
    private String activeDrawerName;
    private String hintPattern; // e.g. "_ _ P _ _ _"
    private Integer wordLength;
    private Integer remainingSeconds;
    private List<DrawGuessPlayerDto> players;
    private List<DrawStrokeDto> activeCanvasHistory;
    private List<WordOptionDto> wordOptions; // Non-null ONLY for the drawer during WORD_SELECTION
}
