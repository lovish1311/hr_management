package com.example.hr_management_backend.features.scribbil.service;

import com.example.hr_management_backend.features.scribbil.dto.*;

public interface DrawGuessGameService {

    DrawGuessRoomResponse createRoom(CreateDrawGuessRoomRequest request, String employeeEmail);

    DrawGuessRoomResponse joinRoom(JoinDrawGuessRoomRequest request, String employeeEmail);

    DrawGuessRoomResponse getRoomDetails(String roomCode, String employeeEmail);

    void startGame(String roomCode, String employeeEmail);

    void restartGame(String roomCode, RestartDrawGuessRoomRequest request, String employeeEmail);

    void selectWord(String roomCode, String selectedWord, String employeeEmail);

    GuessResultDto submitGuess(String roomCode, String guess, String employeeEmail);

    void handleStroke(DrawStrokeDto stroke, String employeeEmail);

    void handleStroke(DrawStrokeDto stroke, Long employeeId);

    void clearCanvas(String roomCode, String employeeEmail);

    void clearCanvas(String roomCode, Long employeeId);

    void undoStroke(String roomCode, String employeeEmail);

    void undoStroke(String roomCode, Long employeeId);

    DrawGuessGameStateDto getGameState(String roomCode, String employeeEmail);

    void handlePlayerDisconnect(String roomCode, Long employeeId);

    void handlePlayerReconnect(String roomCode, Long employeeId);

    java.util.List<DrawStrokeDto> getCanvasHistory(String roomCode);
}
