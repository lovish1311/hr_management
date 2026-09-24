package com.example.hr_management_backend.features.tambola.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TambolaStateDto {
    private TambolaGameResponseDto game;
    private List<TambolaPlayerDto> players;
    private List<TambolaDrawResponseDto> draws;
    private List<TambolaWinnerDto> winners;
    private TambolaTicketDto myTicket;
    @com.fasterxml.jackson.annotation.JsonProperty("isHost")
    private boolean isHost;
}
