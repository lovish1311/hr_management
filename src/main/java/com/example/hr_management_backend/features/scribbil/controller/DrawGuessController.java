package com.example.hr_management_backend.features.scribbil.controller;

import com.example.hr_management_backend.features.scribbil.dto.*;
import com.example.hr_management_backend.features.scribbil.service.DrawGuessGameService;
import com.example.hr_management_backend.features.scribbil.service.WordDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/games/draw-and-guess")
@RequiredArgsConstructor
public class DrawGuessController {

    private final DrawGuessGameService gameService;
    private final WordDictionaryService wordDictionaryService;

    @PostMapping("/rooms")
    public ResponseEntity<DrawGuessRoomResponse> createRoom(
            @RequestBody CreateDrawGuessRoomRequest request,
            Authentication authentication) {
        DrawGuessRoomResponse response = gameService.createRoom(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rooms/join")
    public ResponseEntity<DrawGuessRoomResponse> joinRoom(
            @RequestBody JoinDrawGuessRoomRequest request,
            Authentication authentication) {
        DrawGuessRoomResponse response = gameService.joinRoom(request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rooms/{roomCode}")
    public ResponseEntity<DrawGuessRoomResponse> getRoomDetails(
            @PathVariable String roomCode,
            Authentication authentication) {
        DrawGuessRoomResponse response = gameService.getRoomDetails(roomCode, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rooms/{roomCode}/state")
    public ResponseEntity<DrawGuessGameStateDto> getGameState(
            @PathVariable String roomCode,
            Authentication authentication) {
        DrawGuessGameStateDto response = gameService.getGameState(roomCode, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rooms/{roomCode}/start")
    public ResponseEntity<Map<String, String>> startGame(
            @PathVariable String roomCode,
            Authentication authentication) {
        gameService.startGame(roomCode, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Game starting"));
    }

    @PostMapping("/rooms/{roomCode}/select-word")
    public ResponseEntity<Map<String, String>> selectWord(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String word = body.get("word");
        gameService.selectWord(roomCode, word, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Word selected"));
    }

    @PostMapping("/rooms/{roomCode}/guess")
    public ResponseEntity<GuessResultDto> submitGuess(
            @PathVariable String roomCode,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String guess = body.get("guess");
        GuessResultDto result = gameService.submitGuess(roomCode, guess, authentication.getName());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(wordDictionaryService.getAvailableCategories());
    }
}
