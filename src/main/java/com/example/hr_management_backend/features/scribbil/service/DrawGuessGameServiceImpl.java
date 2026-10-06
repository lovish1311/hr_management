package com.example.hr_management_backend.features.scribbil.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.scribbil.dto.*;
import com.example.hr_management_backend.features.scribbil.model.*;
import com.example.hr_management_backend.features.scribbil.repository.*;
import com.example.hr_management_backend.features.scribbil.websocket.DrawGuessWebSocketSessionManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrawGuessGameServiceImpl implements DrawGuessGameService {

    private final DrawGuessRoomRepository roomRepository;
    private final DrawGuessPlayerRepository playerRepository;
    private final DrawGuessRoundRepository roundRepository;
    private final DrawGuessScoreRepository scoreRepository;
    private final WordDictionaryService wordDictionaryService;
    private final EmployeeRepository employeeRepository;
    private final DrawGuessWebSocketSessionManager sessionManager;

    private final ScheduledExecutorService gameScheduler = Executors.newScheduledThreadPool(4);

    // In-Memory Active Room Runtime State
    private final Map<String, ActiveRoomRuntime> activeRooms = new ConcurrentHashMap<>();

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public DrawGuessRoomResponse createRoom(CreateDrawGuessRoomRequest request, String employeeEmail) {
        Employee host = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Host employee not found with email: " + employeeEmail));

        String roomCode = generateUniqueRoomCode();

        DrawGuessRoom room = DrawGuessRoom.builder()
                .roomCode(roomCode)
                .roomName(request.getRoomName() != null && !request.getRoomName().isBlank()
                        ? request.getRoomName()
                        : host.getFirstName() + "'s Draw Room")
                .hostEmployeeId(host.getId())
                .hostName(host.getFirstName() + " " + host.getLastName())
                .state(DrawGuessGameState.LOBBY)
                .maxRounds(request.getMaxRounds() != null ? Math.max(1, Math.min(10, request.getMaxRounds())) : 3)
                .drawTimeSeconds(request.getDrawTimeSeconds() != null ? Math.max(30, Math.min(180, request.getDrawTimeSeconds())) : 80)
                .wordChoiceCount(request.getWordChoiceCount() != null ? Math.max(2, Math.min(5, request.getWordChoiceCount())) : 3)
                .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
                .customWordsOnly(Boolean.TRUE.equals(request.getCustomWordsOnly()))
                .currentRound(1)
                .currentTurnIndex(0)
                .build();

        room = roomRepository.save(room);

        DrawGuessPlayer hostPlayer = DrawGuessPlayer.builder()
                .roomCode(roomCode)
                .employeeId(host.getId())
                .employeeName(host.getFirstName() + " " + host.getLastName())
                .avatarUrl(null)
                .score(0)
                .turnScore(0)
                .hasGuessedCorrectly(false)
                .isDrawer(false)
                .isHost(true)
                .isConnected(true)
                .turnOrder(0)
                .build();

        playerRepository.save(hostPlayer);

        activeRooms.put(roomCode, new ActiveRoomRuntime(roomCode));

        log.info("Created Draw & Guess room {} by host {} (empId={})", roomCode, hostPlayer.getEmployeeName(), host.getId());
        return mapToRoomResponse(room, List.of(mapToPlayerDto(hostPlayer)));
    }

    @Override
    @Transactional
    public DrawGuessRoomResponse joinRoom(JoinDrawGuessRoomRequest request, String employeeEmail) {
        if (request.getRoomCode() == null || request.getRoomCode().isBlank()) {
            throw new IllegalArgumentException("Room code is required");
        }
        String roomCode = request.getRoomCode().trim().toUpperCase();

        DrawGuessRoom room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Draw & Guess room not found: " + roomCode));

        if (room.getState() == DrawGuessGameState.COMPLETED || room.getState() == DrawGuessGameState.CANCELLED) {
            throw new IllegalStateException("Room " + roomCode + " is already closed.");
        }

        Employee emp = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with email: " + employeeEmail));

        Optional<DrawGuessPlayer> existingPlayer = playerRepository.findByRoomCodeAndEmployeeId(roomCode, emp.getId());
        DrawGuessPlayer player;

        if (existingPlayer.isPresent()) {
            player = existingPlayer.get();
            player.setIsConnected(true);
            playerRepository.save(player);
        } else {
            long currentCount = playerRepository.countByRoomCode(roomCode);
            player = DrawGuessPlayer.builder()
                    .roomCode(roomCode)
                    .employeeId(emp.getId())
                    .employeeName(emp.getFirstName() + " " + emp.getLastName())
                    .avatarUrl(null)
                    .score(0)
                    .turnScore(0)
                    .hasGuessedCorrectly(false)
                    .isDrawer(false)
                    .isHost(emp.getId().equals(room.getHostEmployeeId()))
                    .isConnected(true)
                    .turnOrder((int) currentCount)
                    .build();
            playerRepository.save(player);
        }

        activeRooms.computeIfAbsent(roomCode, ActiveRoomRuntime::new);

        List<DrawGuessPlayerDto> playerDtos = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode)
                .stream()
                .map(this::mapToPlayerDto)
                .toList();

        // Broadcast player joined to WebSocket room
        Map<String, Object> joinedEvent = Map.of(
                "type", "PLAYER_JOINED",
                "roomCode", roomCode,
                "player", mapToPlayerDto(player),
                "players", playerDtos,
                "totalPlayers", playerDtos.size()
        );
        sessionManager.broadcast(roomCode, joinedEvent);

        return mapToRoomResponse(room, playerDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public DrawGuessRoomResponse getRoomDetails(String roomCode, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + code));

        List<DrawGuessPlayerDto> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code)
                .stream()
                .map(this::mapToPlayerDto)
                .toList();

        return mapToRoomResponse(room, players);
    }

    @Override
    @Transactional
    public void startGame(String roomCode, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + code));

        Employee actor = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Actor not found: " + employeeEmail));

        if (!actor.getId().equals(room.getHostEmployeeId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the room host can start the game.");
        }

        List<DrawGuessPlayer> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code);
        if (players.size() < 2) {
            throw new IllegalStateException("Minimum 2 players required to start Draw & Guess game.");
        }

        // Shuffle turn order
        List<DrawGuessPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            DrawGuessPlayer p = shuffled.get(i);
            p.setTurnOrder(i);
            p.setScore(0);
            p.setTurnScore(0);
            p.setHasGuessedCorrectly(false);
            p.setIsDrawer(false);
            playerRepository.save(p);
        }

        room.setState(DrawGuessGameState.STARTING);
        room.setCurrentRound(1);
        room.setCurrentTurnIndex(0);
        roomRepository.save(room);

        ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(code, ActiveRoomRuntime::new);
        runtime.resetForNewGame(room.getMaxRounds());

        sessionManager.broadcast(code, Map.of(
                "type", "GAME_STARTING",
                "roomCode", code,
                "totalRounds", room.getMaxRounds(),
                "players", players.stream().map(this::mapToPlayerDto).toList()
        ));

        // Start first turn after 2-second brief countdown
        gameScheduler.schedule(() -> initiateTurn(code), 2, TimeUnit.SECONDS);
    }

    @Transactional
    public void initiateTurn(String roomCode) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null || room.getState() == DrawGuessGameState.COMPLETED || room.getState() == DrawGuessGameState.CANCELLED) {
                return;
            }

            List<DrawGuessPlayer> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode)
                    .stream()
                    .filter(DrawGuessPlayer::getIsConnected)
                    .toList();

            if (players.isEmpty()) {
                room.setState(DrawGuessGameState.CANCELLED);
                roomRepository.save(room);
                sessionManager.broadcast(roomCode, Map.of("type", "GAME_CANCELLED", "reason", "All players disconnected"));
                return;
            }

            int turnIndex = room.getCurrentTurnIndex();
            if (turnIndex >= players.size()) {
                // Next round
                int nextRound = room.getCurrentRound() + 1;
                if (nextRound > room.getMaxRounds()) {
                    concludeGame(roomCode);
                    return;
                }
                room.setCurrentRound(nextRound);
                room.setCurrentTurnIndex(0);
                turnIndex = 0;
            }

            DrawGuessPlayer drawer = players.get(turnIndex);

            // Reset player turn states
            for (DrawGuessPlayer p : players) {
                p.setIsDrawer(p.getId().equals(drawer.getId()));
                p.setHasGuessedCorrectly(false);
                p.setTurnScore(0);
                playerRepository.save(p);
            }

            room.setState(DrawGuessGameState.WORD_SELECTION);
            room.setActiveDrawerEmployeeId(drawer.getEmployeeId());
            room.setCurrentWord(null);
            room.setCurrentHint(null);
            room.setTurnExpiresAt(LocalDateTime.now().plusSeconds(15));
            roomRepository.save(room);

            // Fetch word choices
            List<WordOptionDto> wordOptions = wordDictionaryService.getRandomWordOptions(room.getCategory(), room.getWordChoiceCount());

            ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(roomCode, ActiveRoomRuntime::new);
            runtime.startWordSelection(drawer.getEmployeeId(), wordOptions);

            // Send private word options to drawer
            sessionManager.sendToUser(roomCode, drawer.getEmployeeId(), Map.of(
                    "type", "WORD_OPTIONS",
                    "roomCode", roomCode,
                    "options", wordOptions,
                    "selectionTimeSeconds", 15
            ));

            // Broadcast word selecting announcement to everyone else
            sessionManager.broadcast(roomCode, Map.of(
                    "type", "DRAWER_CHOOSING_WORD",
                    "roomCode", roomCode,
                    "drawerId", drawer.getEmployeeId(),
                    "drawerName", drawer.getEmployeeName(),
                    "currentRound", room.getCurrentRound(),
                    "maxRounds", room.getMaxRounds(),
                    "turnIndex", turnIndex + 1,
                    "totalTurnsInRound", players.size(),
                    "selectionTimeSeconds", 15
            ));

            // Auto-pick first word if drawer times out after 15 seconds
            ScheduledFuture<?> autoPickTask = gameScheduler.schedule(() -> {
                try {
                    ActiveRoomRuntime r = activeRooms.get(roomCode);
                    if (r != null && r.isAwaitingWordSelection()) {
                        String autoWord = wordOptions.get(0).getWord();
                        log.info("Auto-selecting word '{}' for timed-out drawer in room {}", autoWord, roomCode);
                        executeWordSelection(roomCode, autoWord, drawer.getEmployeeId());
                    }
                } catch (Exception e) {
                    log.error("Error auto-picking word in room {}: {}", roomCode, e.getMessage());
                }
            }, 15, TimeUnit.SECONDS);

            runtime.setAutoPickTask(autoPickTask);

        } catch (Exception e) {
            log.error("Failed to initiate turn for room {}: {}", roomCode, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void selectWord(String roomCode, String selectedWord, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        Employee actor = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeEmail));

        executeWordSelection(code, selectedWord, actor.getId());
    }

    private synchronized void executeWordSelection(String roomCode, String selectedWord, Long drawerEmployeeId) {
        DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
        if (room == null || room.getState() != DrawGuessGameState.WORD_SELECTION) {
            return;
        }

        if (!drawerEmployeeId.equals(room.getActiveDrawerEmployeeId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the active drawer can select a word.");
        }

        ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(roomCode, ActiveRoomRuntime::new);
        runtime.cancelAutoPickTask();

        String word = selectedWord.trim();
        String initialHint = generateMaskedHint(word, Collections.emptySet());

        room.setState(DrawGuessGameState.DRAWING);
        room.setCurrentWord(word);
        room.setCurrentHint(initialHint);
        room.setTurnExpiresAt(LocalDateTime.now().plusSeconds(room.getDrawTimeSeconds()));
        roomRepository.save(room);

        runtime.startDrawing(word, room.getDrawTimeSeconds());

        // Broadcast DRAWING_STARTED
        Map<String, Object> drawingEvent = Map.of(
                "type", "DRAWING_STARTED",
                "roomCode", roomCode,
                "drawerId", drawerEmployeeId,
                "wordLength", word.length(),
                "hintPattern", initialHint,
                "drawTimeSeconds", room.getDrawTimeSeconds(),
                "currentRound", room.getCurrentRound(),
                "maxRounds", room.getMaxRounds()
        );
        sessionManager.broadcast(roomCode, drawingEvent);

        // Send unmasked secret word privately to the drawer
        sessionManager.sendToUser(roomCode, drawerEmployeeId, Map.of(
                "type", "SECRET_WORD_REVEAL",
                "roomCode", roomCode,
                "word", word
        ));

        // Schedule Hint 1 (at 50% remaining time)
        long hint1Delay = room.getDrawTimeSeconds() / 2;
        gameScheduler.schedule(() -> revealHint(roomCode, 1), hint1Delay, TimeUnit.SECONDS);

        // Schedule Hint 2 (at 25% remaining time)
        long hint2Delay = (long) (room.getDrawTimeSeconds() * 0.75);
        gameScheduler.schedule(() -> revealHint(roomCode, 2), hint2Delay, TimeUnit.SECONDS);

        // Schedule Turn Timeout
        ScheduledFuture<?> timeoutTask = gameScheduler.schedule(() -> concludeTurn(roomCode, false),
                room.getDrawTimeSeconds(), TimeUnit.SECONDS);
        runtime.setTurnTimeoutTask(timeoutTask);
    }

    private void revealHint(String roomCode, int hintTier) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            ActiveRoomRuntime runtime = activeRooms.get(roomCode);
            if (room == null || runtime == null || room.getState() != DrawGuessGameState.DRAWING) {
                return;
            }

            String word = runtime.getActiveWord();
            if (word == null) return;

            Set<Integer> revealedIndices = runtime.getRevealedIndices();
            int unrevealedCount = word.length() - revealedIndices.size();

            if (unrevealedCount > 1) {
                // Reveal 1 random non-space character
                List<Integer> available = new ArrayList<>();
                for (int i = 0; i < word.length(); i++) {
                    if (!revealedIndices.contains(i) && word.charAt(i) != ' ') {
                        available.add(i);
                    }
                }
                if (!available.isEmpty()) {
                    int chosen = available.get(RANDOM.nextInt(available.size()));
                    revealedIndices.add(chosen);
                }
            }

            String updatedHint = generateMaskedHint(word, revealedIndices);
            room.setCurrentHint(updatedHint);
            roomRepository.save(room);

            sessionManager.broadcast(roomCode, Map.of(
                    "type", "HINT_REVEALED",
                    "roomCode", roomCode,
                    "hintPattern", updatedHint,
                    "hintTier", hintTier
            ));
        } catch (Exception e) {
            log.error("Failed to reveal hint for room {}: {}", roomCode, e.getMessage());
        }
    }

    @Override
    public void handleStroke(DrawStrokeDto stroke, String employeeEmail) {
        String code = stroke.getRoomCode().trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code).orElse(null);
        if (room == null || room.getState() != DrawGuessGameState.DRAWING) return;

        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        if (emp != null && !emp.getId().equals(room.getActiveDrawerEmployeeId())) {
            log.warn("Blocked non-drawer empId={} from submitting stroke in room {}", emp.getId(), code);
            return;
        }

        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime == null) return;

        runtime.addStroke(stroke);

        // Broadcast stroke to all players in the room
        sessionManager.broadcast(code, Map.of(
                "type", "STROKE",
                "roomCode", code,
                "stroke", stroke
        ));
    }

    @Override
    public void clearCanvas(String roomCode, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code).orElse(null);
        if (room == null || room.getState() != DrawGuessGameState.DRAWING) return;

        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        if (emp != null && !emp.getId().equals(room.getActiveDrawerEmployeeId())) {
            log.warn("Blocked non-drawer empId={} from clearing canvas in room {}", emp.getId(), code);
            return;
        }

        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime != null) {
            runtime.clearCanvas();
        }
        sessionManager.broadcast(code, Map.of("type", "CLEAR_CANVAS", "roomCode", code));
    }

    @Override
    public void undoStroke(String roomCode, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code).orElse(null);
        if (room == null || room.getState() != DrawGuessGameState.DRAWING) return;

        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        if (emp != null && !emp.getId().equals(room.getActiveDrawerEmployeeId())) {
            log.warn("Blocked non-drawer empId={} from undoing stroke in room {}", emp.getId(), code);
            return;
        }

        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime != null) {
            runtime.undoLastStroke();
        }
        sessionManager.broadcast(code, Map.of("type", "UNDO_STROKE", "roomCode", code));
    }

    @Override
    @Transactional
    public GuessResultDto submitGuess(String roomCode, String guess, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + code));

        if (guess == null || guess.trim().isEmpty()) {
            return GuessResultDto.builder()
                    .roomCode(code)
                    .guess("")
                    .isCorrect(false)
                    .isClose(false)
                    .message("Guess cannot be empty.")
                    .build();
        }

        if (room.getState() != DrawGuessGameState.DRAWING) {
            return GuessResultDto.builder()
                    .roomCode(code)
                    .guess(guess)
                    .isCorrect(false)
                    .isClose(false)
                    .message("Guesses are only accepted during an active drawing turn.")
                    .build();
        }

        Employee emp = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeEmail));

        if (emp.getId().equals(room.getActiveDrawerEmployeeId())) {
            return GuessResultDto.builder()
                    .roomCode(code)
                    .employeeId(emp.getId())
                    .employeeName(emp.getFirstName() + " " + emp.getLastName())
                    .guess(guess)
                    .isCorrect(false)
                    .isClose(false)
                    .message("The drawer cannot guess.")
                    .build();
        }

        DrawGuessPlayer player = playerRepository.findByRoomCodeAndEmployeeId(code, emp.getId())
                .orElseThrow(() -> new IllegalArgumentException("Player not in room: " + code));

        if (Boolean.TRUE.equals(player.getHasGuessedCorrectly())) {
            return GuessResultDto.builder()
                    .roomCode(code)
                    .employeeId(emp.getId())
                    .employeeName(player.getEmployeeName())
                    .guess(guess)
                    .isCorrect(false)
                    .isClose(false)
                    .message("You have already guessed the word for this turn.")
                    .build();
        }

        ActiveRoomRuntime runtime = activeRooms.get(code);
        String secretWord = runtime != null ? runtime.getActiveWord() : room.getCurrentWord();
        if (secretWord == null) {
            return GuessResultDto.builder().roomCode(code).guess(guess).isCorrect(false).isClose(false).build();
        }

        String normalizedGuess = guess.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        String normalizedSecret = secretWord.trim().toLowerCase().replaceAll("[^a-z0-9]", "");

        if (normalizedGuess.equals(normalizedSecret)) {
            // Correct Guess!
            int remainingSeconds = calculateRemainingSeconds(room.getTurnExpiresAt());
            int totalSeconds = room.getDrawTimeSeconds();
            boolean isFirstCorrect = runtime != null && runtime.getCorrectGuessers().isEmpty();

            int points = 100 + (int) Math.round(400.0 * ((double) Math.max(1, remainingSeconds) / (double) totalSeconds));
            if (isFirstCorrect) {
                points += 100; // First-guess bonus
            }

            player.setHasGuessedCorrectly(true);
            player.setTurnScore(points);
            player.setScore(player.getScore() + points);
            playerRepository.save(player);

            if (runtime != null) {
                runtime.recordCorrectGuess(emp.getId(), points);
            }

            // Record score
            DrawGuessScore scoreRecord = DrawGuessScore.builder()
                    .roomCode(code)
                    .employeeId(emp.getId())
                    .employeeName(player.getEmployeeName())
                    .pointsAwarded(points)
                    .isDrawerPoints(false)
                    .timeTakenSeconds((double) (totalSeconds - remainingSeconds))
                    .build();
            scoreRepository.save(scoreRecord);

            // Broadcast GUESS_CORRECT (hide the actual word text in chat for other guessers)
            sessionManager.broadcast(code, Map.of(
                    "type", "GUESS_CORRECT",
                    "roomCode", code,
                    "employeeId", emp.getId(),
                    "employeeName", player.getEmployeeName(),
                    "pointsAwarded", points,
                    "totalScore", player.getScore(),
                    "message", player.getEmployeeName() + " guessed the word!"
            ));

            // Check if all active non-drawer guessers have answered
            List<DrawGuessPlayer> activeGuessers = playerRepository.findByRoomCodeAndIsConnectedTrue(code)
                    .stream()
                    .filter(p -> !p.getEmployeeId().equals(room.getActiveDrawerEmployeeId()))
                    .toList();

            boolean allGuessed = activeGuessers.stream().allMatch(p -> Boolean.TRUE.equals(p.getHasGuessedCorrectly()));
            if (allGuessed && !activeGuessers.isEmpty()) {
                log.info("All guessers have solved word '{}' in room {}. Ending turn early.", secretWord, code);
                concludeTurn(code, true);
            }

            return GuessResultDto.builder()
                    .roomCode(code)
                    .employeeId(emp.getId())
                    .employeeName(player.getEmployeeName())
                    .guess(guess)
                    .isCorrect(true)
                    .isClose(false)
                    .pointsAwarded(points)
                    .totalScore(player.getScore())
                    .message("Correct guess!")
                    .build();
        }

        // Check Close Guess via Levenshtein Distance
        int dist = calculateLevenshteinDistance(normalizedGuess, normalizedSecret);
        boolean isClose = dist == 1 || (normalizedSecret.length() >= 6 && dist == 2);

        if (isClose) {
            // Private notification to guesser
            sessionManager.sendToUser(code, emp.getId(), Map.of(
                    "type", "CLOSE_GUESS",
                    "roomCode", code,
                    "guess", guess,
                    "message", "'" + guess + "' is close!"
            ));
        }

        // Broadcast normal guess attempt into chat feed
        sessionManager.broadcast(code, Map.of(
                "type", "GUESS_ATTEMPT",
                "roomCode", code,
                "employeeId", emp.getId(),
                "employeeName", player.getEmployeeName(),
                "guess", guess
        ));

        return GuessResultDto.builder()
                .roomCode(code)
                .employeeId(emp.getId())
                .employeeName(player.getEmployeeName())
                .guess(guess)
                .isCorrect(false)
                .isClose(isClose)
                .build();
    }

    @Transactional
    public synchronized void concludeTurn(String roomCode, boolean earlyAllGuessed) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            ActiveRoomRuntime runtime = activeRooms.get(roomCode);
            if (room == null || room.getState() != DrawGuessGameState.DRAWING) {
                return;
            }

            if (runtime != null) {
                runtime.cancelTurnTimeoutTask();
            }

            String secretWord = runtime != null ? runtime.getActiveWord() : room.getCurrentWord();
            Long drawerId = room.getActiveDrawerEmployeeId();

            DrawGuessPlayer drawer = playerRepository.findByRoomCodeAndEmployeeId(roomCode, drawerId).orElse(null);

            // Compute drawer points based on successful guesses
            int drawerPoints = 0;
            if (runtime != null && !runtime.getGuesserPoints().isEmpty()) {
                double avgGuesserPoints = runtime.getGuesserPoints().values().stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0);
                drawerPoints = (int) Math.round(avgGuesserPoints * 0.75);
            }

            if (drawer != null && drawerPoints > 0) {
                drawer.setTurnScore(drawerPoints);
                drawer.setScore(drawer.getScore() + drawerPoints);
                playerRepository.save(drawer);

                scoreRepository.save(DrawGuessScore.builder()
                        .roomCode(roomCode)
                        .employeeId(drawerId)
                        .employeeName(drawer.getEmployeeName())
                        .pointsAwarded(drawerPoints)
                        .isDrawerPoints(true)
                        .build());
            }

            // Save round record
            DrawGuessRound roundRecord = DrawGuessRound.builder()
                    .roomCode(roomCode)
                    .roundNumber(room.getCurrentRound())
                    .turnNumber(room.getCurrentTurnIndex() + 1)
                    .drawerEmployeeId(drawerId)
                    .drawerName(drawer != null ? drawer.getEmployeeName() : "Drawer")
                    .word(secretWord != null ? secretWord : "UNKNOWN")
                    .category(room.getCategory())
                    .durationSeconds(room.getDrawTimeSeconds())
                    .correctGuessesCount(runtime != null ? runtime.getCorrectGuessers().size() : 0)
                    .endedAt(LocalDateTime.now())
                    .build();
            roundRepository.save(roundRecord);

            room.setState(DrawGuessGameState.ROUND_RESULT);
            roomRepository.save(room);

            List<DrawGuessPlayer> allPlayers = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode);
            List<RoundResultDto.PlayerScoreDelta> scoreDeltas = allPlayers.stream()
                    .map(p -> RoundResultDto.PlayerScoreDelta.builder()
                            .employeeId(p.getEmployeeId())
                            .employeeName(p.getEmployeeName())
                            .pointsEarned(p.getTurnScore())
                            .totalScore(p.getScore())
                            .guessedCorrectly(p.getHasGuessedCorrectly())
                            .build())
                    .toList();

            RoundResultDto roundResult = RoundResultDto.builder()
                    .roomCode(roomCode)
                    .roundNumber(room.getCurrentRound())
                    .turnNumber(room.getCurrentTurnIndex() + 1)
                    .drawerEmployeeId(drawerId)
                    .drawerName(drawer != null ? drawer.getEmployeeName() : "Drawer")
                    .word(secretWord)
                    .drawerScoreAwarded(drawerPoints)
                    .scoreDeltas(scoreDeltas)
                    .nextTurnInSeconds(6)
                    .build();

            // Broadcast ROUND_ENDED with secret word reveal
            sessionManager.broadcast(roomCode, Map.of(
                    "type", "ROUND_ENDED",
                    "roomCode", roomCode,
                    "result", roundResult
            ));

            // Advance turn index
            room.setCurrentTurnIndex(room.getCurrentTurnIndex() + 1);
            roomRepository.save(room);

            // Schedule next turn in 6 seconds
            gameScheduler.schedule(() -> initiateTurn(roomCode), 6, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Failed to conclude turn in room {}: {}", roomCode, e.getMessage(), e);
        }
    }

    @Transactional
    public void concludeGame(String roomCode) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null) return;

            room.setState(DrawGuessGameState.FINAL_RESULTS);
            roomRepository.save(room);

            List<DrawGuessPlayer> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode);
            players.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

            List<FinalGameResultDto.PodiumPlayerDto> leaderboard = new ArrayList<>();
            for (int i = 0; i < players.size(); i++) {
                DrawGuessPlayer p = players.get(i);
                leaderboard.add(FinalGameResultDto.PodiumPlayerDto.builder()
                        .rank(i + 1)
                        .employeeId(p.getEmployeeId())
                        .employeeName(p.getEmployeeName())
                        .avatarUrl(p.getAvatarUrl())
                        .totalScore(p.getScore())
                        .build());
            }

            FinalGameResultDto finalResult = FinalGameResultDto.builder()
                    .roomCode(roomCode)
                    .totalRounds(room.getMaxRounds())
                    .leaderboard(leaderboard)
                    .build();

            sessionManager.broadcast(roomCode, Map.of(
                    "type", "FINAL_RESULTS",
                    "roomCode", roomCode,
                    "result", finalResult
            ));

            log.info("Draw & Guess game {} finalized with {} players.", roomCode, players.size());

        } catch (Exception e) {
            log.error("Failed to conclude game for room {}: {}", roomCode, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DrawGuessGameStateDto getGameState(String roomCode, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + code));

        Employee emp = employeeRepository.findByEmail(employeeEmail).orElse(null);
        Long requesterEmpId = emp != null ? emp.getId() : null;

        List<DrawGuessPlayerDto> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code)
                .stream()
                .map(this::mapToPlayerDto)
                .toList();

        ActiveRoomRuntime runtime = activeRooms.get(code);

        String drawerName = null;
        if (room.getActiveDrawerEmployeeId() != null) {
            drawerName = playerRepository.findByRoomCodeAndEmployeeId(code, room.getActiveDrawerEmployeeId())
                    .map(DrawGuessPlayer::getEmployeeName)
                    .orElse("Drawer");
        }

        List<WordOptionDto> wordOptions = null;
        if (room.getState() == DrawGuessGameState.WORD_SELECTION
                && requesterEmpId != null
                && requesterEmpId.equals(room.getActiveDrawerEmployeeId())
                && runtime != null) {
            wordOptions = runtime.getWordOptions();
        }

        return DrawGuessGameStateDto.builder()
                .roomCode(code)
                .state(room.getState())
                .currentRound(room.getCurrentRound())
                .maxRounds(room.getMaxRounds())
                .currentTurnIndex(room.getCurrentTurnIndex())
                .activeDrawerEmployeeId(room.getActiveDrawerEmployeeId())
                .activeDrawerName(drawerName)
                .hintPattern(room.getCurrentHint())
                .wordLength(room.getCurrentWord() != null ? room.getCurrentWord().length() : 0)
                .remainingSeconds(calculateRemainingSeconds(room.getTurnExpiresAt()))
                .players(players)
                .activeCanvasHistory(runtime != null ? runtime.getCanvasHistory() : Collections.emptyList())
                .wordOptions(wordOptions)
                .build();
    }

    @Override
    @Transactional
    public void handlePlayerDisconnect(String roomCode, Long employeeId) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code).orElse(null);
        if (room == null) return;

        playerRepository.findByRoomCodeAndEmployeeId(code, employeeId).ifPresent(p -> {
            p.setIsConnected(false);
            playerRepository.save(p);

            List<DrawGuessPlayerDto> playerDtos = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code)
                    .stream()
                    .map(this::mapToPlayerDto)
                    .toList();

            sessionManager.broadcast(code, Map.of(
                    "type", "PLAYER_DISCONNECTED",
                    "roomCode", code,
                    "employeeId", employeeId,
                    "employeeName", p.getEmployeeName(),
                    "player", mapToPlayerDto(p),
                    "players", playerDtos
            ));

            // Host migration if in lobby and host disconnects
            if (p.getIsHost() && room.getState() == DrawGuessGameState.LOBBY) {
                List<DrawGuessPlayer> remaining = playerRepository.findByRoomCodeAndIsConnectedTrue(code);
                if (!remaining.isEmpty()) {
                    DrawGuessPlayer newHost = remaining.get(0);
                    newHost.setIsHost(true);
                    playerRepository.save(newHost);
                    p.setIsHost(false);
                    playerRepository.save(p);
                    room.setHostEmployeeId(newHost.getEmployeeId());
                    room.setHostName(newHost.getEmployeeName());
                    roomRepository.save(room);

                    sessionManager.broadcast(code, Map.of(
                            "type", "HOST_MIGRATED",
                            "roomCode", code,
                            "newHostId", newHost.getEmployeeId(),
                            "newHostName", newHost.getEmployeeName()
                    ));
                }
            }

            // Drawer disconnected during active drawing / word selection
            if (employeeId.equals(room.getActiveDrawerEmployeeId())
                    && (room.getState() == DrawGuessGameState.DRAWING || room.getState() == DrawGuessGameState.WORD_SELECTION)) {
                log.info("Active drawer {} disconnected in room {}. Ending turn.", employeeId, code);
                sessionManager.broadcast(code, Map.of(
                        "type", "DRAWER_DISCONNECTED",
                        "roomCode", code,
                        "message", "Drawer disconnected. Advancing to next turn..."
                ));
                gameScheduler.schedule(() -> concludeTurn(code, false), 2, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    @Transactional
    public void handlePlayerReconnect(String roomCode, Long employeeId) {
        String code = roomCode.trim().toUpperCase();
        playerRepository.findByRoomCodeAndEmployeeId(code, employeeId).ifPresent(p -> {
            p.setIsConnected(true);
            playerRepository.save(p);

            List<DrawGuessPlayerDto> playerDtos = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code)
                    .stream()
                    .map(this::mapToPlayerDto)
                    .toList();

            sessionManager.broadcast(code, Map.of(
                    "type", "PLAYER_RECONNECTED",
                    "roomCode", code,
                    "employeeId", employeeId,
                    "employeeName", p.getEmployeeName(),
                    "player", mapToPlayerDto(p),
                    "players", playerDtos
            ));
        });
    }

    @PreDestroy
    public void shutdownScheduler() {
        gameScheduler.shutdown();
    }

    // --- Helper Methods ---

    private String generateUniqueRoomCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder("DG");
            for (int j = 0; j < 4; j++) {
                sb.append(ROOM_CODE_CHARS.charAt(RANDOM.nextInt(ROOM_CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (roomRepository.findByRoomCode(code).isEmpty()) {
                return code;
            }
        }
        return "DG" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String generateMaskedHint(String word, Set<Integer> revealedIndices) {
        if (word == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == ' ') {
                sb.append("   ");
            } else if (revealedIndices.contains(i)) {
                sb.append(c).append(" ");
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }

    private int calculateRemainingSeconds(LocalDateTime expiresAt) {
        if (expiresAt == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), expiresAt).toSeconds();
        return (int) Math.max(0, seconds);
    }

    private int calculateLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    private DrawGuessRoomResponse mapToRoomResponse(DrawGuessRoom room, List<DrawGuessPlayerDto> players) {
        return DrawGuessRoomResponse.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .hostEmployeeId(room.getHostEmployeeId())
                .hostName(room.getHostName())
                .state(room.getState())
                .maxRounds(room.getMaxRounds())
                .drawTimeSeconds(room.getDrawTimeSeconds())
                .wordChoiceCount(room.getWordChoiceCount())
                .category(room.getCategory())
                .customWordsOnly(room.getCustomWordsOnly())
                .currentRound(room.getCurrentRound())
                .currentTurnIndex(room.getCurrentTurnIndex())
                .activeDrawerEmployeeId(room.getActiveDrawerEmployeeId())
                .currentHint(room.getCurrentHint())
                .remainingSeconds(calculateRemainingSeconds(room.getTurnExpiresAt()))
                .players(players)
                .createdAt(room.getCreatedAt())
                .build();
    }

    private DrawGuessPlayerDto mapToPlayerDto(DrawGuessPlayer player) {
        return DrawGuessPlayerDto.builder()
                .id(player.getId())
                .roomCode(player.getRoomCode())
                .employeeId(player.getEmployeeId())
                .employeeName(player.getEmployeeName())
                .avatarUrl(player.getAvatarUrl())
                .score(player.getScore())
                .turnScore(player.getTurnScore())
                .hasGuessedCorrectly(player.getHasGuessedCorrectly())
                .isDrawer(player.getIsDrawer())
                .isHost(player.getIsHost())
                .isConnected(player.getIsConnected())
                .turnOrder(player.getTurnOrder())
                .joinedAt(player.getJoinedAt())
                .build();
    }

    // --- Active Room Runtime Memory Container ---

    private static class ActiveRoomRuntime {
        private final String roomCode;
        private String activeWord;
        private Long activeDrawerId;
        private List<WordOptionDto> wordOptions = new CopyOnWriteArrayList<>();
        private final Set<Integer> revealedIndices = ConcurrentHashMap.newKeySet();
        private final Set<Long> correctGuessers = ConcurrentHashMap.newKeySet();
        private final Map<Long, Integer> guesserPoints = new ConcurrentHashMap<>();
        private final List<DrawStrokeDto> canvasHistory = new CopyOnWriteArrayList<>();
        private ScheduledFuture<?> autoPickTask;
        private ScheduledFuture<?> turnTimeoutTask;

        public ActiveRoomRuntime(String roomCode) {
            this.roomCode = roomCode;
        }

        public void resetForNewGame(int maxRounds) {
            this.activeWord = null;
            this.activeDrawerId = null;
            this.wordOptions.clear();
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            cancelAutoPickTask();
            cancelTurnTimeoutTask();
        }

        public void startWordSelection(Long drawerId, List<WordOptionDto> options) {
            this.activeDrawerId = drawerId;
            this.wordOptions = new CopyOnWriteArrayList<>(options);
            this.activeWord = null;
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            cancelAutoPickTask();
            cancelTurnTimeoutTask();
        }

        public void startDrawing(String word, int drawTimeSeconds) {
            this.activeWord = word;
            this.wordOptions.clear();
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            cancelAutoPickTask();
        }

        public void addStroke(DrawStrokeDto stroke) {
            if (canvasHistory.size() > 2000) {
                canvasHistory.remove(0);
            }
            canvasHistory.add(stroke);
        }

        public void clearCanvas() {
            canvasHistory.clear();
        }

        public void undoLastStroke() {
            if (!canvasHistory.isEmpty()) {
                canvasHistory.remove(canvasHistory.size() - 1);
            }
        }

        public void recordCorrectGuess(Long empId, int points) {
            correctGuessers.add(empId);
            guesserPoints.put(empId, points);
        }

        public boolean isAwaitingWordSelection() {
            return activeWord == null && !wordOptions.isEmpty();
        }

        public void cancelAutoPickTask() {
            if (autoPickTask != null && !autoPickTask.isDone()) {
                autoPickTask.cancel(true);
            }
        }

        public void cancelTurnTimeoutTask() {
            if (turnTimeoutTask != null && !turnTimeoutTask.isDone()) {
                turnTimeoutTask.cancel(true);
            }
        }

        public String getActiveWord() { return activeWord; }
        public List<WordOptionDto> getWordOptions() { return wordOptions; }
        public Set<Integer> getRevealedIndices() { return revealedIndices; }
        public Set<Long> getCorrectGuessers() { return correctGuessers; }
        public Map<Long, Integer> getGuesserPoints() { return guesserPoints; }
        public List<DrawStrokeDto> getCanvasHistory() { return canvasHistory; }
        public void setAutoPickTask(ScheduledFuture<?> task) { this.autoPickTask = task; }
        public void setTurnTimeoutTask(ScheduledFuture<?> task) { this.turnTimeoutTask = task; }
    }
}
