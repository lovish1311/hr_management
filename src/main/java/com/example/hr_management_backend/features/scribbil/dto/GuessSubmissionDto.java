package com.example.hr_management_backend.features.scribbil.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuessSubmissionDto {
    private String roomCode;
    private String guess;
}
