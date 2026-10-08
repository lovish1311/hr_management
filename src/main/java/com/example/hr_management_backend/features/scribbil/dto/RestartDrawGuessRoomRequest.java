package com.example.hr_management_backend.features.scribbil.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestartDrawGuessRoomRequest {
    private Integer maxRounds;
    private Integer drawTimeSeconds;
    private Integer wordChoiceCount;
    private String category;
    private Boolean customWordsOnly;
}

