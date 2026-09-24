package com.example.hr_management_backend.features.tambola.service;

import com.example.hr_management_backend.features.tambola.dto.*;

import java.util.List;

public interface TambolaGameService {

    TambolaGameResponseDto createGame(Long employeeId, CreateTambolaGameDto dto);

    TambolaTicketDto joinGame(Long employeeId, String roomCode);

    TambolaTicketDto getGameTicket(Long employeeId, String roomCode);

    TambolaStateDto getGameState(Long employeeId, String roomCode);

    List<TambolaGameResponseDto> getActiveGames();

    TambolaGameResponseDto startGame(Long employeeId, String roomCode);

    TambolaDrawResponseDto drawNextNumber(Long employeeId, String roomCode);

    TambolaClaimResultDto claimPrize(Long employeeId, String roomCode, String prizeType);

    TambolaGameResponseDto pauseGame(Long employeeId, String roomCode);

    TambolaGameResponseDto resumeGame(Long employeeId, String roomCode);

    TambolaGameResponseDto endGame(Long employeeId, String roomCode);

    TambolaGameResponseDto restartGame(Long employeeId, String roomCode);
}
