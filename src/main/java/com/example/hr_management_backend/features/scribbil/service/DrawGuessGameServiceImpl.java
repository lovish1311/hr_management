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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final DrawGuessScoringEngine scoringEngine;

    private static final int SCHEDULER_CORE_POOL_SIZE = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    private final ScheduledExecutorService gameScheduler = Executors.newScheduledThreadPool(
            SCHEDULER_CORE_POOL_SIZE,
            new ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "draw-guess-scheduler-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

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

        List<DrawGuessPlayerDto> players = mapToPlayerDtosWithRanks(
                playerRepository.findByRoomCodeOrderByTurnOrderAsc(code));

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

        if (room.getState() != DrawGuessGameState.LOBBY) {
            log.warn("Room {} is already in state {}, ignoring duplicate startGame request", code, room.getState());
            return;
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
        }
        playerRepository.saveAll(shuffled);

        room.setState(DrawGuessGameState.STARTING);
        room.setCurrentRound(1);
        room.setCurrentTurnIndex(0);
        roomRepository.save(room);

        ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(code, ActiveRoomRuntime::new);
        runtime.resetForNewGame(room.getMaxRounds());

        // Generate 45 words using Gemini AI (with fallback) asynchronously for this game session
        List<String> gameWords = wordDictionaryService.generateWordsForGame(room.getCategory(), 45);
        runtime.initWordPool(gameWords);
        log.info("Initialized room {} with {} words in pool for category '{}'", code, gameWords.size(), room.getCategory());

        sessionManager.broadcast(code, Map.of(
                "type", "GAME_STARTING",
                "roomCode", code,
                "totalRounds", room.getMaxRounds(),
                "players", mapToPlayerDtosWithRanks(shuffled)
        ));

        // Start first turn after 2-second brief countdown
        gameScheduler.schedule(() -> initiateTurn(code), 2, TimeUnit.SECONDS);
    }

    @Override
    @Transactional
    public void restartGame(String roomCode, RestartDrawGuessRoomRequest request, String employeeEmail) {
        String code = roomCode.trim().toUpperCase();
        DrawGuessRoom room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + code));

        Employee actor = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new IllegalArgumentException("Actor not found: " + employeeEmail));

        if (!actor.getId().equals(room.getHostEmployeeId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the room host can restart the game.");
        }

        if (request != null) {
            if (request.getMaxRounds() != null) {
                room.setMaxRounds(Math.max(1, Math.min(10, request.getMaxRounds())));
            }
            if (request.getDrawTimeSeconds() != null) {
                room.setDrawTimeSeconds(Math.max(30, Math.min(180, request.getDrawTimeSeconds())));
            }
            if (request.getWordChoiceCount() != null) {
                room.setWordChoiceCount(Math.max(2, Math.min(5, request.getWordChoiceCount())));
            }
            if (request.getCategory() != null && !request.getCategory().isBlank()) {
                room.setCategory(request.getCategory().trim().toUpperCase());
            }
            if (request.getCustomWordsOnly() != null) {
                room.setCustomWordsOnly(request.getCustomWordsOnly());
            }
        }

        List<DrawGuessPlayer> players = playerRepository.findByRoomCodeOrderByTurnOrderAsc(code);
        if (players.size() < 2) {
            throw new IllegalStateException("Minimum 2 players required to restart Draw & Guess game.");
        }

        // Shuffle turn order and reset per-game scores
        List<DrawGuessPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            DrawGuessPlayer p = shuffled.get(i);
            p.setTurnOrder(i);
            p.setScore(0);
            p.setTurnScore(0);
            p.setHasGuessedCorrectly(false);
            p.setIsDrawer(false);
        }
        playerRepository.saveAll(shuffled);

        room.setState(DrawGuessGameState.STARTING);
        room.setCurrentRound(1);
        room.setCurrentTurnIndex(0);
        room.setActiveDrawerEmployeeId(null);
        room.setCurrentWord(null);
        room.setCurrentHint(null);
        roomRepository.save(room);

        ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(code, ActiveRoomRuntime::new);
        runtime.resetForNewGame(room.getMaxRounds());

        // Generate new word pool for selected category
        List<String> gameWords = wordDictionaryService.generateWordsForGame(room.getCategory(), 45);
        runtime.initWordPool(gameWords);
        log.info("Restarted room {} with {} words in pool for category '{}'", code, gameWords.size(), room.getCategory());

        // Broadcast clear canvas and restart event
        sessionManager.broadcast(code, Map.of(
                "type", "CLEAR_CANVAS",
                "roomCode", code
        ));

        sessionManager.broadcast(code, Map.of(
                "type", "GAME_RESTARTED",
                "roomCode", code,
                "totalRounds", room.getMaxRounds(),
                "drawTimeSeconds", room.getDrawTimeSeconds(),
                "category", room.getCategory(),
                "players", mapToPlayerDtosWithRanks(shuffled)
        ));

        gameScheduler.schedule(() -> initiateTurn(code), 2, TimeUnit.SECONDS);
    }

    @Transactional
    public void initiateTurn(String roomCode) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null || room.getState() == DrawGuessGameState.COMPLETED || room.getState() == DrawGuessGameState.CANCELLED) {
                return;
            }

            List<DrawGuessPlayer> allPlayers = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode);
            if (allPlayers.isEmpty()) {
                room.setState(DrawGuessGameState.CANCELLED);
                roomRepository.save(room);
                sessionManager.broadcast(roomCode, Map.of("type", "GAME_CANCELLED", "reason", "All players disconnected"));
                return;
            }

            // Determine active drawer using turn order across all registered players
            int turnIndex = room.getCurrentTurnIndex();
            if (turnIndex >= allPlayers.size()) {
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

            DrawGuessPlayer drawer = allPlayers.get(turnIndex);

            // Reset player turn states
            for (DrawGuessPlayer p : allPlayers) {
                p.setIsDrawer(p.getId().equals(drawer.getId()));
                p.setHasGuessedCorrectly(false);
                p.setTurnScore(0);
            }
            playerRepository.saveAll(allPlayers);

            room.setState(DrawGuessGameState.WORD_SELECTION);
            room.setActiveDrawerEmployeeId(drawer.getEmployeeId());
            room.setCurrentWord(null);
            room.setCurrentHint(null);
            room.setTurnExpiresAt(LocalDateTime.now().plusSeconds(15));
            roomRepository.save(room);

            ActiveRoomRuntime runtime = activeRooms.computeIfAbsent(roomCode, ActiveRoomRuntime::new);

            // Select 3 random word options from room's remaining word pool
            List<WordOptionDto> wordOptions = new ArrayList<>();
            List<String> pool = runtime.getRemainingWordPool();
            if (pool.size() < room.getWordChoiceCount()) {
                // Top up pool from dictionary if exhausted
                List<String> extra = wordDictionaryService.getFallbackWords(room.getCategory(), 30);
                runtime.initWordPool(extra);
                pool = runtime.getRemainingWordPool();
            }

            List<String> candidatePool = new ArrayList<>(pool);
            Collections.shuffle(candidatePool);
            int choiceCount = Math.min(room.getWordChoiceCount(), candidatePool.size());
            for (int i = 0; i < choiceCount; i++) {
                String w = candidatePool.get(i);
                wordOptions.add(WordOptionDto.builder()
                        .word(w)
                        .category(room.getCategory())
                        .difficulty("MEDIUM")
                        .hint("")
                        .build());
            }

            List<WordOptionDto> finalWordOptions = wordOptions.isEmpty()
                    ? wordDictionaryService.getRandomWordOptions(room.getCategory(), room.getWordChoiceCount())
                    : wordOptions;

            runtime.startWordSelection(drawer.getEmployeeId(), finalWordOptions);

            // Send private word options to drawer
            sessionManager.sendToUser(roomCode, drawer.getEmployeeId(), Map.of(
                    "type", "WORD_OPTIONS",
                    "roomCode", roomCode,
                    "options", finalWordOptions,
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
                    "totalTurnsInRound", allPlayers.size(),
                    "selectionTimeSeconds", 15
            ));

            // Auto-pick first word if drawer times out after 15 seconds
            ScheduledFuture<?> autoPickTask = gameScheduler.schedule(() -> {
                try {
                    ActiveRoomRuntime r = activeRooms.get(roomCode);
                    if (r != null && r.isAwaitingWordSelection()) {
                        String autoWord = finalWordOptions.get(0).getWord();
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
        runtime.cancelDrawerDisconnectGraceTask();
        runtime.cancelHintTasks();
        runtime.getIsConcludingTurn().set(false);

        String word = selectedWord.trim();
        String initialHint = generateMaskedHint(word, Collections.emptySet());

        // Evict chosen word from room's word pool so it is not repeated
        runtime.removeWordFromPool(word);

        room.setState(DrawGuessGameState.DRAWING);
        room.setCurrentWord(word);
        room.setCurrentHint(initialHint);
        room.setTurnExpiresAt(LocalDateTime.now().plusSeconds(room.getDrawTimeSeconds()));
        roomRepository.save(room);

        runtime.startDrawing(word, room.getDrawTimeSeconds());
        runtime.setTurnStartedAt(Instant.now());

        // Broadcast explicit CLEAR_CANVAS to wipe all clients clean
        sessionManager.broadcast(roomCode, Map.of(
                "type", "CLEAR_CANVAS",
                "roomCode", roomCode
        ));

        // Broadcast DRAWING_STARTED
        Map<String, Object> drawingEvent = Map.of(
                "type", "DRAWING_STARTED",
                "roomCode", roomCode,
                "drawerId", drawerEmployeeId,
                "wordLength", word.length(),
                "hintPattern", initialHint,
                "drawTimeSeconds", room.getDrawTimeSeconds(),
                "turnStartedAtEpochMs", runtime.getTurnStartedAt().toEpochMilli(),
                "turnExpiresAtEpochMs", runtime.getTurnStartedAt().toEpochMilli() + (room.getDrawTimeSeconds() * 1000L),
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

        // Schedule progressive hints at 30%, 60%, 80% elapsed time
        int totalSec = room.getDrawTimeSeconds();
        long hint1Delay = Math.max(1, (long) (totalSec * 0.30));
        long hint2Delay = Math.max(2, (long) (totalSec * 0.60));
        long hint3Delay = Math.max(3, (long) (totalSec * 0.80));

        runtime.addHintTask(gameScheduler.schedule(() -> revealHint(roomCode, 1), hint1Delay, TimeUnit.SECONDS));
        runtime.addHintTask(gameScheduler.schedule(() -> revealHint(roomCode, 2), hint2Delay, TimeUnit.SECONDS));
        runtime.addHintTask(gameScheduler.schedule(() -> revealHint(roomCode, 3), hint3Delay, TimeUnit.SECONDS));

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
        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        handleStroke(stroke, emp != null ? emp.getId() : null);
    }

    @Override
    public void handleStroke(DrawStrokeDto stroke, Long employeeId) {
        String code = stroke.getRoomCode().trim().toUpperCase();
        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime == null || !runtime.isDrawingActive()) return;

        if (employeeId != null && !employeeId.equals(runtime.getActiveDrawerId())) {
            log.warn("Blocked non-drawer empId={} from submitting stroke in room {}", employeeId, code);
            return;
        }

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
        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        clearCanvas(roomCode, emp != null ? emp.getId() : null);
    }

    @Override
    public void clearCanvas(String roomCode, Long employeeId) {
        String code = roomCode.trim().toUpperCase();
        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime == null || !runtime.isDrawingActive()) return;

        if (employeeId != null && !employeeId.equals(runtime.getActiveDrawerId())) {
            log.warn("Blocked non-drawer empId={} from clearing canvas in room {}", employeeId, code);
            return;
        }

        runtime.clearCanvas();
        sessionManager.broadcast(code, Map.of("type", "CLEAR_CANVAS", "roomCode", code));
    }

    @Override
    public void undoStroke(String roomCode, String employeeEmail) {
        Employee emp = employeeEmail != null ? employeeRepository.findByEmail(employeeEmail).orElse(null) : null;
        undoStroke(roomCode, emp != null ? emp.getId() : null);
    }

    @Override
    public void undoStroke(String roomCode, Long employeeId) {
        String code = roomCode.trim().toUpperCase();
        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime == null || !runtime.isDrawingActive()) return;

        if (employeeId != null && !employeeId.equals(runtime.getActiveDrawerId())) {
            log.warn("Blocked non-drawer empId={} from undoing stroke in room {}", employeeId, code);
            return;
        }

        String undoneStrokeId = runtime.undoLastStroke();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "UNDO_STROKE");
        payload.put("roomCode", code);
        if (undoneStrokeId != null) {
            payload.put("strokeId", undoneStrokeId);
        }
        sessionManager.broadcast(code, payload);
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
            Instant turnStart = runtime != null ? runtime.getTurnStartedAt() : null;
            double elapsedSeconds = 0.0;
            if (turnStart != null) {
                long elapsedMs = Duration.between(turnStart, Instant.now()).toMillis();
                elapsedSeconds = Math.max(0.0, elapsedMs / 1000.0);
            } else {
                int remainingSeconds = calculateRemainingSeconds(room.getTurnExpiresAt());
                elapsedSeconds = Math.max(0.0, (double) (room.getDrawTimeSeconds() - remainingSeconds));
            }

            int points = scoringEngine.calculateGuessScore(elapsedSeconds, (double) room.getDrawTimeSeconds());

            player.setHasGuessedCorrectly(true);
            player.setTurnScore(points);
            // Cumulative player.score remains frozen mid-round! Points are committed only upon concludeTurn().
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
                    .timeTakenSeconds(elapsedSeconds)
                    .build();
            scoreRepository.save(scoreRecord);

            // Broadcast GUESS_CORRECT (notice: totalScore remains frozen at cumulative player.getScore())
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
                log.info("All active guessers have solved word '{}' in room {}. Ending turn early.", secretWord, code);
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
                if (!runtime.getIsConcludingTurn().compareAndSet(false, true)) {
                    log.info("Turn in room {} is already concluding. Skipping duplicate trigger.", roomCode);
                    return;
                }
                runtime.cancelTurnTimeoutTask();
                runtime.cancelHintTasks();
                runtime.cancelDrawerDisconnectGraceTask();
            }

            String secretWord = runtime != null ? runtime.getActiveWord() : room.getCurrentWord();
            Long drawerId = room.getActiveDrawerEmployeeId();

            // Compute drawer points based on 50% of total guesser scores using scoring engine
            int drawerPoints = 0;
            if (runtime != null && !runtime.getGuesserPoints().isEmpty()) {
                drawerPoints = scoringEngine.calculateDrawerScore(runtime.getGuesserPoints().values(), DrawGuessScoringEngine.DEFAULT_DRAWER_SHARE);
            }

            DrawGuessPlayer drawer = playerRepository.findByRoomCodeAndEmployeeId(roomCode, drawerId).orElse(null);
            if (drawer != null) {
                drawer.setTurnScore(drawerPoints);
                drawer.setScore(drawer.getScore() + drawerPoints);
                playerRepository.save(drawer);

                if (drawerPoints > 0) {
                    scoreRepository.save(DrawGuessScore.builder()
                            .roomCode(roomCode)
                            .employeeId(drawerId)
                            .employeeName(drawer.getEmployeeName())
                            .pointsAwarded(drawerPoints)
                            .isDrawerPoints(true)
                            .build());
                }
            }

            // Commit guesser turn points to cumulative totals
            List<DrawGuessPlayer> allPlayers = playerRepository.findByRoomCodeOrderByTurnOrderAsc(roomCode);
            for (DrawGuessPlayer p : allPlayers) {
                if (!p.getEmployeeId().equals(drawerId)) {
                    int turnPoints = p.getTurnScore() != null ? p.getTurnScore() : 0;
                    p.setScore(p.getScore() + turnPoints);
                }
            }

            // Determine authoritative leaderboard ranks: sorted by score DESC, turnOrder ASC
            List<DrawGuessPlayer> rankedPlayers = new ArrayList<>(allPlayers);
            rankedPlayers.sort((a, b) -> {
                int cmp = Integer.compare(b.getScore(), a.getScore());
                if (cmp != 0) return cmp;
                return Integer.compare(a.getTurnOrder(), b.getTurnOrder());
            });

            Map<Long, Integer> rankMap = new HashMap<>();
            for (int i = 0; i < rankedPlayers.size(); i++) {
                rankMap.put(rankedPlayers.get(i).getEmployeeId(), i + 1);
            }

            // Batch update all players
            playerRepository.saveAll(allPlayers);

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

            List<RoundResultDto.PlayerScoreDelta> scoreDeltas = allPlayers.stream()
                    .map(p -> RoundResultDto.PlayerScoreDelta.builder()
                            .employeeId(p.getEmployeeId())
                            .employeeName(p.getEmployeeName())
                            .pointsEarned(p.getTurnScore())
                            .totalScore(p.getScore())
                            .rank(rankMap.getOrDefault(p.getEmployeeId(), 1))
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

            // Broadcast ROUND_ENDED with secret word reveal and finalized leaderboard
            List<DrawGuessPlayerDto> playerDtos = allPlayers.stream()
                    .map(p -> {
                        DrawGuessPlayerDto dto = mapToPlayerDto(p);
                        dto.setRank(rankMap.getOrDefault(p.getEmployeeId(), 1));
                        return dto;
                    })
                    .toList();

            sessionManager.broadcast(roomCode, Map.of(
                    "type", "ROUND_ENDED",
                    "roomCode", roomCode,
                    "result", roundResult,
                    "players", playerDtos
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
            room.setActiveDrawerEmployeeId(null);
            room.setCurrentWord(null);
            room.setCurrentHint(null);
            roomRepository.save(room);

            ActiveRoomRuntime runtime = activeRooms.get(roomCode);
            if (runtime != null) {
                runtime.concludeGame();
            }

            sessionManager.broadcast(roomCode, Map.of(
                    "type", "CLEAR_CANVAS",
                    "roomCode", roomCode
            ));

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

            List<DrawGuessPlayerDto> playerDtos = mapToPlayerDtosWithRanks(
                    playerRepository.findByRoomCodeOrderByTurnOrderAsc(code));

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
                log.info("Active drawer {} disconnected in room {}. Starting 10-second grace period.", employeeId, code);
                ActiveRoomRuntime runtime = activeRooms.get(code);
                if (runtime != null) {
                    sessionManager.broadcast(code, Map.of(
                            "type", "DRAWER_DISCONNECTED",
                            "roomCode", code,
                            "message", "Drawer disconnected. Waiting 10s for reconnect..."
                    ));
                    ScheduledFuture<?> grace = gameScheduler.schedule(() -> {
                        try {
                            DrawGuessRoom currentRoom = roomRepository.findByRoomCode(code).orElse(null);
                            if (currentRoom == null) return;

                            boolean stillDisconnected = playerRepository.findByRoomCodeAndEmployeeId(code, employeeId)
                                    .map(pl -> !Boolean.TRUE.equals(pl.getIsConnected()))
                                    .orElse(true);

                            if (!stillDisconnected) {
                                log.info("Drawer empId {} reconnected before grace expired in room {}", employeeId, code);
                                return;
                            }

                            log.info("Drawer reconnect grace expired for empId {} in room {}. State: {}", employeeId, code, currentRoom.getState());

                            if (currentRoom.getState() == DrawGuessGameState.WORD_SELECTION) {
                                skipTurnDueToDrawerDisconnect(code, employeeId);
                            } else if (currentRoom.getState() == DrawGuessGameState.DRAWING) {
                                concludeTurn(code, false);
                            }
                        } catch (Exception e) {
                            log.error("Error executing drawer disconnect grace expiry in room {}: {}", code, e.getMessage(), e);
                        }
                    }, 10, TimeUnit.SECONDS);
                    runtime.setDrawerDisconnectGraceTask(grace);
                }
            }
        });
    }

    @Transactional
    public synchronized void skipTurnDueToDrawerDisconnect(String roomCode, Long disconnectedDrawerId) {
        try {
            DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null || room.getState() == DrawGuessGameState.COMPLETED || room.getState() == DrawGuessGameState.CANCELLED) {
                return;
            }

            ActiveRoomRuntime runtime = activeRooms.get(roomCode);
            if (runtime != null) {
                runtime.cancelAutoPickTask();
                runtime.cancelTurnTimeoutTask();
                runtime.cancelHintTasks();
                runtime.cancelDrawerDisconnectGraceTask();
                runtime.getIsConcludingTurn().set(false);
            }

            log.info("Skipping turn in room {} due to disconnected drawer empId={}", roomCode, disconnectedDrawerId);

            sessionManager.broadcast(roomCode, Map.of(
                    "type", "TURN_SKIPPED",
                    "roomCode", roomCode,
                    "reason", "Active drawer disconnected. Advancing to next turn.",
                    "nextTurnInSeconds", 4
            ));

            room.setState(DrawGuessGameState.ROUND_RESULT);
            room.setCurrentTurnIndex(room.getCurrentTurnIndex() + 1);
            room.setCurrentWord(null);
            room.setCurrentHint(null);
            room.setActiveDrawerEmployeeId(null);
            roomRepository.save(room);

            gameScheduler.schedule(() -> initiateTurn(roomCode), 4, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to skip turn for room {}: {}", roomCode, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void handlePlayerReconnect(String roomCode, Long employeeId) {
        String code = roomCode.trim().toUpperCase();
        ActiveRoomRuntime runtime = activeRooms.get(code);
        if (runtime != null) {
            runtime.cancelDrawerDisconnectGraceTask();
        }

        DrawGuessRoom room = roomRepository.findByRoomCode(code).orElse(null);
        if (room == null) return;

        playerRepository.findByRoomCodeAndEmployeeId(code, employeeId).ifPresent(p -> {
            p.setIsConnected(true);
            playerRepository.save(p);

            List<DrawGuessPlayerDto> playerDtos = mapToPlayerDtosWithRanks(
                    playerRepository.findByRoomCodeOrderByTurnOrderAsc(code));

            sessionManager.broadcast(code, Map.of(
                    "type", "PLAYER_RECONNECTED",
                    "roomCode", code,
                    "employeeId", employeeId,
                    "employeeName", p.getEmployeeName(),
                    "player", mapToPlayerDto(p),
                    "players", playerDtos
            ));

            // Authoritative state push for reconnecting client
            // 1. If active drawer during DRAWING: restore secret word
            if (employeeId.equals(room.getActiveDrawerEmployeeId()) && room.getState() == DrawGuessGameState.DRAWING) {
                String word = runtime != null && runtime.getActiveWord() != null ? runtime.getActiveWord() : room.getCurrentWord();
                if (word != null) {
                    sessionManager.sendToUser(code, employeeId, Map.of(
                            "type", "SECRET_WORD_REVEAL",
                            "roomCode", code,
                            "word", word
                    ));
                }
            }

            // 2. If active drawer during WORD_SELECTION: restore word options
            if (employeeId.equals(room.getActiveDrawerEmployeeId()) && room.getState() == DrawGuessGameState.WORD_SELECTION) {
                List<WordOptionDto> options = runtime != null ? runtime.getWordOptions() : Collections.emptyList();
                if (!options.isEmpty()) {
                    sessionManager.sendToUser(code, employeeId, Map.of(
                            "type", "WORD_OPTIONS",
                            "roomCode", code,
                            "options", options,
                            "selectionTimeSeconds", calculateRemainingSeconds(room.getTurnExpiresAt())
                    ));
                }
            }

            // 3. If guesser already guessed correctly: restore secret word reveal
            if (!employeeId.equals(room.getActiveDrawerEmployeeId())
                    && Boolean.TRUE.equals(p.getHasGuessedCorrectly())
                    && room.getState() == DrawGuessGameState.DRAWING) {
                String word = runtime != null && runtime.getActiveWord() != null ? runtime.getActiveWord() : room.getCurrentWord();
                if (word != null) {
                    sessionManager.sendToUser(code, employeeId, Map.of(
                            "type", "SECRET_WORD_REVEAL",
                            "roomCode", code,
                            "word", word
                    ));
                }
            }
        });
    }

    @Override
    public List<DrawStrokeDto> getCanvasHistory(String roomCode) {
        ActiveRoomRuntime runtime = activeRooms.get(roomCode.trim().toUpperCase());
        return runtime != null ? new ArrayList<>(runtime.getCanvasHistory()) : Collections.emptyList();
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

    private List<DrawGuessPlayerDto> mapToPlayerDtosWithRanks(List<DrawGuessPlayer> players) {
        List<DrawGuessPlayer> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(b.getScore(), a.getScore());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getTurnOrder(), b.getTurnOrder());
        });
        Map<Long, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            rankMap.put(sorted.get(i).getEmployeeId(), i + 1);
        }
        return players.stream().map(p -> {
            DrawGuessPlayerDto dto = mapToPlayerDto(p);
            dto.setRank(rankMap.getOrDefault(p.getEmployeeId(), 1));
            return dto;
        }).toList();
    }

    // --- Active Room Runtime Memory Container ---

    private static class ActiveRoomRuntime {
        private final String roomCode;
        private String activeWord;
        private Long activeDrawerId;
        private List<WordOptionDto> wordOptions = new CopyOnWriteArrayList<>();
        private final List<String> remainingWordPool = new CopyOnWriteArrayList<>();
        private final Set<Integer> revealedIndices = ConcurrentHashMap.newKeySet();
        private final Set<Long> correctGuessers = ConcurrentHashMap.newKeySet();
        private final Map<Long, Integer> guesserPoints = new ConcurrentHashMap<>();
        private final List<DrawStrokeDto> canvasHistory = new CopyOnWriteArrayList<>();
        private ScheduledFuture<?> autoPickTask;
        private ScheduledFuture<?> turnTimeoutTask;

        private Instant turnStartedAt;
        private final AtomicBoolean isConcludingTurn = new AtomicBoolean(false);
        private ScheduledFuture<?> drawerDisconnectGraceTask;
        private final Map<Long, Instant> guessTimestamps = new ConcurrentHashMap<>();
        private final List<ScheduledFuture<?>> hintTasks = new CopyOnWriteArrayList<>();

        public ActiveRoomRuntime(String roomCode) {
            this.roomCode = roomCode;
        }

        public void initWordPool(List<String> words) {
            this.remainingWordPool.clear();
            if (words != null) {
                this.remainingWordPool.addAll(words);
            }
        }

        public List<String> getRemainingWordPool() {
            return remainingWordPool;
        }

        public void removeWordFromPool(String chosenWord) {
            if (chosenWord != null) {
                remainingWordPool.removeIf(w -> w.equalsIgnoreCase(chosenWord.trim()));
            }
        }

        public void resetForNewGame(int maxRounds) {
            this.activeWord = null;
            this.activeDrawerId = null;
            this.wordOptions.clear();
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            this.guessTimestamps.clear();
            this.isConcludingTurn.set(false);
            cancelAutoPickTask();
            cancelTurnTimeoutTask();
            cancelDrawerDisconnectGraceTask();
            cancelHintTasks();
        }

        public void concludeGame() {
            resetForNewGame(0);
        }

        public void startWordSelection(Long drawerId, List<WordOptionDto> options) {
            this.activeDrawerId = drawerId;
            this.wordOptions = new CopyOnWriteArrayList<>(options);
            this.activeWord = null;
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            this.guessTimestamps.clear();
            this.isConcludingTurn.set(false);
            cancelAutoPickTask();
            cancelTurnTimeoutTask();
            cancelDrawerDisconnectGraceTask();
            cancelHintTasks();
        }

        public void startDrawing(String word, int drawTimeSeconds) {
            this.activeWord = word;
            this.wordOptions.clear();
            this.revealedIndices.clear();
            this.correctGuessers.clear();
            this.guesserPoints.clear();
            this.canvasHistory.clear();
            this.guessTimestamps.clear();
            this.isConcludingTurn.set(false);
            cancelAutoPickTask();
            cancelDrawerDisconnectGraceTask();
            cancelHintTasks();
        }

        public Long getActiveDrawerId() {
            return activeDrawerId;
        }

        public boolean isDrawingActive() {
            return activeWord != null;
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

        public String undoLastStroke() {
            if (canvasHistory.isEmpty()) return null;
            DrawStrokeDto last = canvasHistory.get(canvasHistory.size() - 1);
            String lastId = last.getStrokeId();
            if (lastId != null && !lastId.isBlank()) {
                canvasHistory.removeIf(s -> lastId.equals(s.getStrokeId()));
                return lastId;
            } else {
                canvasHistory.remove(canvasHistory.size() - 1);
                return null;
            }
        }

        public void recordCorrectGuess(Long empId, int points) {
            correctGuessers.add(empId);
            guesserPoints.put(empId, points);
            guessTimestamps.put(empId, Instant.now());
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

        public void cancelDrawerDisconnectGraceTask() {
            if (drawerDisconnectGraceTask != null && !drawerDisconnectGraceTask.isDone()) {
                drawerDisconnectGraceTask.cancel(true);
            }
        }

        public void setDrawerDisconnectGraceTask(ScheduledFuture<?> task) {
            cancelDrawerDisconnectGraceTask();
            this.drawerDisconnectGraceTask = task;
        }

        public void cancelHintTasks() {
            for (ScheduledFuture<?> task : hintTasks) {
                if (task != null && !task.isDone()) {
                    task.cancel(true);
                }
            }
            hintTasks.clear();
        }

        public void addHintTask(ScheduledFuture<?> task) {
            hintTasks.add(task);
        }

        public String getActiveWord() { return activeWord; }
        public List<WordOptionDto> getWordOptions() { return wordOptions; }
        public Set<Integer> getRevealedIndices() { return revealedIndices; }
        public Set<Long> getCorrectGuessers() { return correctGuessers; }
        public Map<Long, Integer> getGuesserPoints() { return guesserPoints; }
        public List<DrawStrokeDto> getCanvasHistory() { return canvasHistory; }
        public void setAutoPickTask(ScheduledFuture<?> task) { this.autoPickTask = task; }
        public void setTurnTimeoutTask(ScheduledFuture<?> task) { this.turnTimeoutTask = task; }
        public Instant getTurnStartedAt() { return turnStartedAt; }
        public void setTurnStartedAt(Instant turnStartedAt) { this.turnStartedAt = turnStartedAt; }
        public AtomicBoolean getIsConcludingTurn() { return isConcludingTurn; }
        public Map<Long, Instant> getGuessTimestamps() { return guessTimestamps; }
    }
}
