package com.example.hr_management_backend.features.tambola.controller;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.tambola.dto.*;
import com.example.hr_management_backend.features.tambola.service.TambolaGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tambola", "/api/tambola"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TambolaController {

    private final TambolaGameService gameService;
    private final EmployeeRepository employeeRepository;

    @PostMapping("/create")
    public ResponseEntity<TambolaGameResponseDto> createGame(
            @RequestBody(required = false) CreateTambolaGameDto dto,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.createGame(empId, dto));
    }

    @PostMapping("/join/{roomCode}")
    public ResponseEntity<TambolaTicketDto> joinGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.joinGame(empId, roomCode));
    }

    @GetMapping("/games")
    public ResponseEntity<List<TambolaGameResponseDto>> getActiveGames() {
        return ResponseEntity.ok(gameService.getActiveGames());
    }

    @GetMapping("/game/{roomCode}/state")
    public ResponseEntity<TambolaStateDto> getGameState(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = null;
        try {
            empId = resolveEmployeeId(headerEmpId);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(gameService.getGameState(empId, roomCode));
    }

    @GetMapping("/game/{roomCode}/ticket")
    public ResponseEntity<TambolaTicketDto> getTicket(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.getGameTicket(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/start")
    public ResponseEntity<TambolaGameResponseDto> startGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.startGame(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/draw")
    public ResponseEntity<TambolaDrawResponseDto> drawNumber(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.drawNextNumber(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/claim")
    public ResponseEntity<TambolaClaimResultDto> claimPrize(
            @PathVariable String roomCode,
            @Valid @RequestBody TambolaClaimRequestDto request,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.claimPrize(empId, roomCode, request.getPrizeType()));
    }

    @PostMapping("/game/{roomCode}/pause")
    public ResponseEntity<TambolaGameResponseDto> pauseGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.pauseGame(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/resume")
    public ResponseEntity<TambolaGameResponseDto> resumeGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.resumeGame(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/end")
    public ResponseEntity<TambolaGameResponseDto> endGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.endGame(empId, roomCode));
    }

    @PostMapping("/game/{roomCode}/restart")
    public ResponseEntity<TambolaGameResponseDto> restartGame(
            @PathVariable String roomCode,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(headerEmpId);
        return ResponseEntity.ok(gameService.restartGame(empId, roomCode));
    }

    private Long resolveEmployeeId(Long headerEmpId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
            String email = auth.getName();
            Employee employee = employeeRepository.findByEmail(email).orElse(null);
            if (employee != null) {
                return employee.getId();
            }
        }
        if (headerEmpId != null) {
            return headerEmpId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated to perform this action.");
    }
}
