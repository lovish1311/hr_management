package com.example.hr_management_backend.features.tambola.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.games.service.CompanyGameService;
import com.example.hr_management_backend.features.tambola.dto.*;
import com.example.hr_management_backend.features.tambola.model.*;
import com.example.hr_management_backend.features.tambola.repository.*;
import com.example.hr_management_backend.features.tambola.websocket.TambolaWebSocketSessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TambolaGameServiceImpl implements TambolaGameService {

    private final TambolaGameRepository gameRepository;
    private final TambolaPlayerRepository playerRepository;
    private final TambolaTicketRepository ticketRepository;
    private final TambolaDrawRepository drawRepository;
    private final TambolaWinnerRepository winnerRepository;
    private final EmployeeRepository employeeRepository;
    private final TambolaTicketGenerator ticketGenerator;
    private final TambolaWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final CompanyGameService companyGameService;

    private final SecureRandom random = new SecureRandom();
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Override
    @Transactional
    public TambolaGameResponseDto createGame(Long employeeId, CreateTambolaGameDto dto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        assertCanHost(employee);

        if (!companyGameService.isGameAvailableForRole("TAMBOLA", employee.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access restricted: Tambola is currently disabled by Admin");
        }

        String roomCode = generateUniqueRoomCode();
        String title = (dto != null && dto.getTitle() != null && !dto.getTitle().isBlank())
                ? dto.getTitle().trim()
                : "HR Tambola - " + roomCode;

        TambolaGame game = TambolaGame.builder()
                .roomCode(roomCode)
                .title(title)
                .status("WAITING")
                .autoDrawIntervalSeconds(dto != null ? dto.getAutoDrawIntervalSeconds() : null)
                .totalNumbersDrawn(0)
                .managerId(employee.getId())
                .managerName(employee.getFirstName() + " " + employee.getLastName())
                .build();

        game = gameRepository.save(game);
        log.info("Created Tambola game [{}] with code {}", game.getId(), roomCode);

        // Creator automatically joins and receives ticket
        joinGame(employeeId, roomCode);

        return mapToGameDto(game, employeeId);
    }

    @Override
    @Transactional
    public TambolaTicketDto joinGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found with room code: " + roomCode));

        if ("COMPLETED".equalsIgnoreCase(game.getStatus()) || "CANCELLED".equalsIgnoreCase(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game has already ended or been cancelled.");
        }

        Employee employee = getEmployeeOrThrow(employeeId);

        if (!companyGameService.isGameAvailableForRole("TAMBOLA", employee.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access restricted: Tambola is currently disabled by Admin");
        }

        // Check if player record exists
        TambolaPlayer player = playerRepository.findByGameIdAndEmployeeId(game.getId(), employeeId)
                .orElseGet(() -> {
                    TambolaPlayer newPlayer = TambolaPlayer.builder()
                            .gameId(game.getId())
                            .employeeId(employee.getId())
                            .employeeName(employee.getFirstName() + " " + employee.getLastName())
                            .build();
                    return playerRepository.save(newPlayer);
                });

        // Check if ticket already exists
        TambolaTicket ticket = ticketRepository.findByGameIdAndEmployeeId(game.getId(), employeeId)
                .orElseGet(() -> {
                    TambolaTicketGenerator.GeneratedTicket generated = ticketGenerator.generateTicket();
                    TambolaTicket newTicket = TambolaTicket.builder()
                            .gameId(game.getId())
                            .playerId(player.getId())
                            .employeeId(employee.getId())
                            .numbersJson(generated.getNumbersJson())
                            .row1(generated.getRow1())
                            .row2(generated.getRow2())
                            .row3(generated.getRow3())
                            .allNumbers(generated.getAllNumbersStr())
                            .build();
                    return ticketRepository.save(newTicket);
                });

        // Broadcast player joined
        long totalPlayers = playerRepository.countByGameId(game.getId());
        Map<String, Object> joinBroadcast = Map.of(
                "type", "PLAYER_JOINED",
                "employeeId", employee.getId(),
                "employeeName", employee.getFirstName() + " " + employee.getLastName(),
                "totalPlayers", totalPlayers
        );
        sessionManager.broadcast(normalizedCode, joinBroadcast);

        return mapToTicketDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TambolaTicketDto getGameTicket(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found with code: " + roomCode));

        TambolaTicket ticket = ticketRepository.findByGameIdAndEmployeeId(game.getId(), employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not joined this game yet."));

        return mapToTicketDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TambolaStateDto getGameState(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found with code: " + roomCode));

        List<TambolaPlayerDto> players = playerRepository.findByGameIdOrderByJoinedAtAsc(game.getId())
                .stream()
                .map(p -> TambolaPlayerDto.builder()
                        .playerId(p.getId())
                        .employeeId(p.getEmployeeId())
                        .employeeName(p.getEmployeeName())
                        .joinedAt(p.getJoinedAt())
                        .build())
                .collect(Collectors.toList());

        List<TambolaDrawResponseDto> draws = drawRepository.findByGameIdOrderByDrawOrderAsc(game.getId())
                .stream()
                .map(d -> TambolaDrawResponseDto.builder()
                        .drawOrder(d.getDrawOrder())
                        .number(d.getNumber())
                        .drawnAt(d.getDrawnAt())
                        .build())
                .collect(Collectors.toList());

        List<TambolaWinnerDto> winners = winnerRepository.findByGameIdOrderByClaimedAtAsc(game.getId())
                .stream()
                .map(w -> TambolaWinnerDto.builder()
                        .id(w.getId())
                        .employeeId(w.getEmployeeId())
                        .employeeName(w.getEmployeeName())
                        .prizeType(w.getPrizeType())
                        .prizeRank(w.getPrizeRank())
                        .claimNumber(w.getClaimNumber())
                        .winningDetails(w.getWinningDetails())
                        .claimedAt(w.getClaimedAt())
                        .build())
                .collect(Collectors.toList());

        TambolaTicketDto myTicket = null;
        if (employeeId != null) {
            myTicket = ticketRepository.findByGameIdAndEmployeeId(game.getId(), employeeId)
                    .map(this::mapToTicketDto)
                    .orElse(null);
        }

        boolean isHost = employeeId != null && employeeId.equals(game.getManagerId());

        return TambolaStateDto.builder()
                .game(mapToGameDto(game, employeeId))
                .players(players)
                .draws(draws)
                .winners(winners)
                .myTicket(myTicket)
                .isHost(isHost)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TambolaGameResponseDto> getActiveGames() {
        return gameRepository.findByStatusInOrderByCreatedAtDesc(List.of("WAITING", "RUNNING", "PAUSED"))
                .stream()
                .map(g -> mapToGameDto(g, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TambolaGameResponseDto startGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);

        if (!"WAITING".equalsIgnoreCase(game.getStatus()) && !"PAUSED".equalsIgnoreCase(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game cannot be started from current status: " + game.getStatus());
        }

        game.setStatus("RUNNING");
        game = gameRepository.save(game);

        Map<String, Object> broadcast = Map.of(
                "type", "GAME_STARTED",
                "roomCode", normalizedCode,
                "status", "RUNNING"
        );
        sessionManager.broadcast(normalizedCode, broadcast);

        return mapToGameDto(game, employeeId);
    }

    @Override
    @Transactional
    public TambolaDrawResponseDto drawNextNumber(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCodeWithLock(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);

        if (!"RUNNING".equalsIgnoreCase(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot draw numbers. Game status is " + game.getStatus());
        }

        List<TambolaDraw> existingDraws = drawRepository.findByGameIdOrderByDrawOrderAsc(game.getId());
        if (existingDraws.size() >= 90) {
            game.setStatus("COMPLETED");
            gameRepository.save(game);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All 90 numbers have already been drawn.");
        }

        Set<Integer> drawnSet = existingDraws.stream().map(TambolaDraw::getNumber).collect(Collectors.toSet());
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= 90; i++) {
            if (!drawnSet.contains(i)) {
                pool.add(i);
            }
        }

        int nextNumber = pool.get(random.nextInt(pool.size()));
        int nextOrder = existingDraws.size() + 1;

        TambolaDraw draw = TambolaDraw.builder()
                .gameId(game.getId())
                .drawOrder(nextOrder)
                .number(nextNumber)
                .build();
        draw = drawRepository.save(draw);

        game.setLastDrawnNumber(nextNumber);
        game.setTotalNumbersDrawn(nextOrder);
        if (nextOrder >= 90) {
            game.setStatus("COMPLETED");
        }
        gameRepository.save(game);

        Map<String, Object> payload = Map.of(
                "type", "NUMBER_DRAWN",
                "drawOrder", nextOrder,
                "number", nextNumber,
                "totalNumbersDrawn", nextOrder,
                "timestamp", draw.getDrawnAt() != null ? draw.getDrawnAt().toString() : LocalDateTime.now().toString()
        );
        sessionManager.broadcast(normalizedCode, payload);

        return TambolaDrawResponseDto.builder()
                .drawOrder(nextOrder)
                .number(nextNumber)
                .drawnAt(draw.getDrawnAt())
                .build();
    }

    @Override
    @Transactional
    public TambolaClaimResultDto claimPrize(Long employeeId, String roomCode, String prizeType) {
        String normalizedCode = roomCode.trim().toUpperCase();
        String normalizedPrize = (prizeType != null ? prizeType.trim().toUpperCase() : "");

        if (!List.of("FIRST_HOUSE", "SECOND_HOUSE", "THIRD_HOUSE", "FULL_HOUSE").contains(normalizedPrize)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prize type: " + prizeType + ". Allowed: FIRST_HOUSE, SECOND_HOUSE, THIRD_HOUSE, FULL_HOUSE");
        }

        // Acquire pessimistic write lock on the game row to serialize concurrent claims
        TambolaGame game = gameRepository.findByRoomCodeWithLock(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        if (!"RUNNING".equalsIgnoreCase(game.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot claim prizes. Game is not running.");
        }

        // Step 1: Check if already claimed (concurrency collision protection)
        if (winnerRepository.existsByGameIdAndPrizeType(game.getId(), normalizedPrize)) {
            log.warn("Prize {} in game {} was already claimed", normalizedPrize, normalizedCode);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prize " + normalizedPrize + " has already been claimed by another player!");
        }

        // Step 2: Validate claimant's ticket
        Employee employee = getEmployeeOrThrow(employeeId);
        TambolaTicket ticket = ticketRepository.findByGameIdAndEmployeeId(game.getId(), employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "You do not have a ticket in this game."));

        List<TambolaDraw> draws = drawRepository.findByGameIdOrderByDrawOrderAsc(game.getId());
        Set<Integer> drawnSet = draws.stream().map(TambolaDraw::getNumber).collect(Collectors.toSet());

        List<Integer> requiredNumbers;
        switch (normalizedPrize) {
            case "FIRST_HOUSE":
                requiredNumbers = parseNumberList(ticket.getRow1());
                break;
            case "SECOND_HOUSE":
                requiredNumbers = parseNumberList(ticket.getRow2());
                break;
            case "THIRD_HOUSE":
                requiredNumbers = parseNumberList(ticket.getRow3());
                break;
            case "FULL_HOUSE":
                requiredNumbers = parseNumberList(ticket.getAllNumbers());
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid prize type");
        }

        if (!drawnSet.containsAll(requiredNumbers)) {
            List<Integer> missing = requiredNumbers.stream().filter(n -> !drawnSet.contains(n)).collect(Collectors.toList());
            log.warn("Bogus claim by employee {} ({}) for {}: missing numbers {}",
                    employee.getId(), employee.getFirstName(), normalizedPrize, missing);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bogus claim: your ticket is missing drawn numbers: " + missing);
        }

        // Step 3: Persist winner
        TambolaWinner winner = TambolaWinner.builder()
                .gameId(game.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .prizeType(normalizedPrize)
                .prizeRank(1)
                .claimNumber(game.getLastDrawnNumber())
                .winningDetails(normalizedPrize + " successfully claimed at Draw #" + game.getTotalNumbersDrawn())
                .build();
        winner = winnerRepository.save(winner);

        // If FULL_HOUSE, complete game; otherwise pause game until host resumes it
        if ("FULL_HOUSE".equals(normalizedPrize)) {
            game.setStatus("COMPLETED");
        } else {
            game.setStatus("PAUSED");
        }
        gameRepository.save(game);

        // Step 4: Broadcast prize won to all connected clients
        Map<String, Object> broadcast = Map.of(
                "type", "PRIZE_CLAIMED",
                "prizeType", normalizedPrize,
                "winnerId", employee.getId(),
                "winnerName", employee.getFirstName() + " " + employee.getLastName(),
                "claimNumber", game.getLastDrawnNumber() != null ? game.getLastDrawnNumber() : 0,
                "gameStatus", game.getStatus(),
                "gameCompleted", "COMPLETED".equalsIgnoreCase(game.getStatus())
        );
        sessionManager.broadcast(normalizedCode, broadcast);

        return TambolaClaimResultDto.builder()
                .success(true)
                .message("Congratulations! You won " + normalizedPrize)
                .prizeType(normalizedPrize)
                .winnerEmployeeId(employee.getId())
                .winnerEmployeeName(employee.getFirstName() + " " + employee.getLastName())
                .prizeRank(1)
                .claimNumber(game.getLastDrawnNumber())
                .claimedAt(winner.getClaimedAt())
                .build();
    }

    @Override
    @Transactional
    public TambolaGameResponseDto pauseGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);
        game.setStatus("PAUSED");
        game = gameRepository.save(game);

        sessionManager.broadcast(normalizedCode, Map.of("type", "GAME_PAUSED", "status", "PAUSED"));
        return mapToGameDto(game, employeeId);
    }

    @Override
    @Transactional
    public TambolaGameResponseDto resumeGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);
        game.setStatus("RUNNING");
        game = gameRepository.save(game);

        sessionManager.broadcast(normalizedCode, Map.of("type", "GAME_RESUMED", "status", "RUNNING"));
        return mapToGameDto(game, employeeId);
    }

    @Override
    @Transactional
    public TambolaGameResponseDto endGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);
        game.setStatus("COMPLETED");
        game = gameRepository.save(game);

        sessionManager.broadcast(normalizedCode, Map.of("type", "GAME_ENDED", "status", "COMPLETED"));
        return mapToGameDto(game, employeeId);
    }

    @Override
    @Transactional
    public TambolaGameResponseDto restartGame(Long employeeId, String roomCode) {
        String normalizedCode = roomCode.trim().toUpperCase();
        TambolaGame game = gameRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        assertIsHostOrAdmin(employeeId, game);

        // Clear draws and winners
        drawRepository.deleteByGameId(game.getId());
        winnerRepository.deleteByGameId(game.getId());

        // Regenerate fresh tickets for all existing players in room
        List<TambolaTicket> existingTickets = ticketRepository.findByGameId(game.getId());
        for (TambolaTicket ticket : existingTickets) {
            TambolaTicketGenerator.GeneratedTicket gen = ticketGenerator.generateTicket();
            ticket.setNumbersJson(gen.getNumbersJson());
            ticket.setRow1(gen.getRow1());
            ticket.setRow2(gen.getRow2());
            ticket.setRow3(gen.getRow3());
            ticket.setAllNumbers(gen.getAllNumbersStr());
            ticketRepository.save(ticket);
        }

        // Reset game status
        game.setStatus("RUNNING");
        game.setTotalNumbersDrawn(0);
        game.setLastDrawnNumber(null);
        game = gameRepository.save(game);

        log.info("Restarted Tambola game [{}] with code {}", game.getId(), normalizedCode);

        // Broadcast restart event over WebSocket
        Map<String, Object> restartBroadcast = Map.of(
                "type", "GAME_RESTARTED",
                "roomCode", normalizedCode,
                "gameStatus", "RUNNING",
                "message", "Game restarted! New tickets generated."
        );
        sessionManager.broadcast(normalizedCode, restartBroadcast);

        return mapToGameDto(game, employeeId);
    }

    private String generateUniqueRoomCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!gameRepository.existsByRoomCode(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + employeeId));
    }

    private void assertCanHost(Employee employee) {
        String role = employee.getRole() != null ? employee.getRole().toUpperCase() : "";
        boolean isPrivileged = "SUPER_ADMIN".equals(role) || "HR".equals(role);
        boolean hasTambolaAccess = Boolean.TRUE.equals(employee.getHasTambolaAccess());

        if (!isPrivileged && !hasTambolaAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to host or manage Tambola games.");
        }
    }

    private void assertIsHostOrAdmin(Long employeeId, TambolaGame game) {
        if (employeeId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (employeeId.equals(game.getManagerId())) {
            return;
        }
        Employee employee = getEmployeeOrThrow(employeeId);
        assertCanHost(employee);
    }

    private List<Integer> parseNumberList(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    private TambolaTicketDto mapToTicketDto(TambolaTicket ticket) {
        List<List<Integer>> grid = new ArrayList<>();
        try {
            grid = objectMapper.readValue(ticket.getNumbersJson(), new TypeReference<List<List<Integer>>>() {});
        } catch (Exception e) {
            log.error("Failed to parse ticket JSON: {}", ticket.getNumbersJson());
        }

        return TambolaTicketDto.builder()
                .ticketId(ticket.getId())
                .gameId(ticket.getGameId())
                .employeeId(ticket.getEmployeeId())
                .grid(grid)
                .row1(parseNumberList(ticket.getRow1()))
                .row2(parseNumberList(ticket.getRow2()))
                .row3(parseNumberList(ticket.getRow3()))
                .allNumbers(parseNumberList(ticket.getAllNumbers()))
                .build();
    }

    private TambolaGameResponseDto mapToGameDto(TambolaGame game, Long currentEmployeeId) {
        int playerCount = (int) playerRepository.countByGameId(game.getId());
        boolean isHost = currentEmployeeId != null && currentEmployeeId.equals(game.getManagerId());

        return TambolaGameResponseDto.builder()
                .id(game.getId())
                .roomCode(game.getRoomCode())
                .title(game.getTitle())
                .status(game.getStatus())
                .autoDrawIntervalSeconds(game.getAutoDrawIntervalSeconds())
                .totalNumbersDrawn(game.getTotalNumbersDrawn())
                .lastDrawnNumber(game.getLastDrawnNumber())
                .createdById(game.getManagerId())
                .createdByName(game.getManagerName())
                .createdAt(game.getCreatedAt())
                .playerCount(playerCount)
                .isHost(isHost)
                .build();
    }
}
