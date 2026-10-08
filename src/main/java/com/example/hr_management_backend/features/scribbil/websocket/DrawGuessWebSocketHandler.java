package com.example.hr_management_backend.features.scribbil.websocket;

import com.example.hr_management_backend.core.security.JwtUtils;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.scribbil.repository.DrawGuessRoomRepository;
import com.example.hr_management_backend.features.scribbil.model.DrawGuessRoom;
import com.example.hr_management_backend.features.scribbil.dto.DrawStrokeDto;
import com.example.hr_management_backend.features.scribbil.model.StrokeType;
import com.example.hr_management_backend.features.scribbil.service.DrawGuessGameService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DrawGuessWebSocketHandler extends TextWebSocketHandler {

    private final DrawGuessWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;
    private final EmployeeRepository employeeRepository;
    private final DrawGuessRoomRepository roomRepository;
    private final DrawGuessGameService gameService;

    public DrawGuessWebSocketHandler(
            DrawGuessWebSocketSessionManager sessionManager,
            ObjectMapper objectMapper,
            JwtUtils jwtUtils,
            EmployeeRepository employeeRepository,
            DrawGuessRoomRepository roomRepository,
            @Lazy DrawGuessGameService gameService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.jwtUtils = jwtUtils;
        this.employeeRepository = employeeRepository;
        this.roomRepository = roomRepository;
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            log.warn("Rejected Draw & Guess WebSocket connection: missing query parameters");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> params = parseQueryParams(uri.getQuery());
        String roomCode = params.get("roomCode");
        String token = params.get("token");

        Long employeeId = null;
        String employeeEmail = null;

        if (token != null && !token.isBlank()) {
            try {
                if (jwtUtils.validateJwtToken(token)) {
                    employeeEmail = jwtUtils.getUserNameFromJwtToken(token);
                    employeeId = employeeRepository.findByEmail(employeeEmail)
                            .map(Employee::getId)
                            .orElse(null);
                }
            } catch (Exception e) {
                log.warn("Failed to extract employee from JWT token on Draw & Guess WS: {}", e.getMessage());
            }
        }

        if (employeeId == null && params.get("employeeId") != null) {
            try {
                employeeId = Long.parseLong(params.get("employeeId"));
            } catch (NumberFormatException ignored) {}
        }

        if (roomCode == null || roomCode.isBlank()) {
            log.warn("Rejected Draw & Guess WebSocket connection: missing roomCode");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sessionManager.registerSession(roomCode, employeeId, session);

        if (employeeId != null) {
            gameService.handlePlayerReconnect(roomCode, employeeId);
        }

        // Send connection acknowledgement
        Map<String, Object> ack = Map.of(
                "type", "CONNECTED",
                "roomCode", roomCode.toUpperCase(),
                "employeeId", employeeId != null ? employeeId : 0,
                "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));

        // Synchronize existing drawing strokes to connecting session
        List<DrawStrokeDto> history = gameService.getCanvasHistory(roomCode);
        if (history != null && !history.isEmpty()) {
            Map<String, Object> sync = Map.of(
                    "type", "SYNC_CANVAS",
                    "roomCode", roomCode.toUpperCase(),
                    "strokes", history
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sync)));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText();
            String roomCode = sessionManager.getRoomForSession(session.getId());
            Long employeeId = sessionManager.getEmployeeForSession(session.getId());

            if ("PING".equalsIgnoreCase(type)) {
                Map<String, Object> pong = Map.of(
                        "type", "PONG",
                        "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
                return;
            }

            if (roomCode == null) return;

            String employeeEmail = null;
            if (employeeId != null) {
                employeeEmail = employeeRepository.findById(employeeId)
                        .map(Employee::getEmail)
                        .orElse(null);
            }

            switch (type.toUpperCase()) {
                case "STROKE" -> {
                    DrawStrokeDto stroke = objectMapper.treeToValue(root.path("stroke"), DrawStrokeDto.class);
                    if (stroke != null) {
                        stroke.setRoomCode(roomCode);
                        gameService.handleStroke(stroke, employeeId);
                    }
                }
                case "CLEAR" -> gameService.clearCanvas(roomCode, employeeId);
                case "UNDO" -> gameService.undoStroke(roomCode, employeeId);
                case "GUESS" -> {
                    String guess = root.path("guess").asText("");
                    gameService.submitGuess(roomCode, guess, employeeEmail);
                }
                case "SELECT_WORD" -> {
                    String word = root.path("word").asText("");
                    gameService.selectWord(roomCode, word, employeeEmail);
                }
                case "START_GAME" -> gameService.startGame(roomCode, employeeEmail);
                case "CHAT" -> {
                    String chatText = root.path("message").asText("");
                    if (!chatText.isBlank()) {
                        String senderName = employeeId != null ? employeeRepository.findById(employeeId)
                                .map(e -> e.getFirstName() + " " + e.getLastName())
                                .orElse("Player") : "Player";

                        // Anti-spoiler filter: check if secret word is in message
                        String filteredMessage = chatText;
                        DrawGuessRoom room = roomRepository.findByRoomCode(roomCode).orElse(null);
                        if (room != null && room.getCurrentWord() != null) {
                            String secret = room.getCurrentWord().trim().toLowerCase();
                            if (chatText.toLowerCase().contains(secret)) {
                                filteredMessage = chatText.replaceAll("(?i)" + java.util.regex.Pattern.quote(secret), "***");
                            }
                        }

                        Map<String, Object> chatMsg = Map.of(
                                "type", "CHAT_MESSAGE",
                                "employeeId", employeeId != null ? employeeId : 0,
                                "senderName", senderName,
                                "message", filteredMessage,
                                "timestamp", System.currentTimeMillis()
                        );
                        sessionManager.broadcast(roomCode, chatMsg);
                    }
                }
                default -> log.debug("Unhandled WS message type {} for room {}", type, roomCode);
            }
        } catch (Exception e) {
            log.warn("Error processing Draw & Guess WebSocket text message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomCode = sessionManager.getRoomForSession(session.getId());
        Long empId = sessionManager.getEmployeeForSession(session.getId());
        sessionManager.removeSession(session);

        if (roomCode != null && empId != null) {
            gameService.handlePlayerDisconnect(roomCode, empId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Transport error on Draw & Guess WebSocket session {}: {}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (idx > 0) {
                params.put(pair.substring(0, idx), "");
            }
        }
        return params;
    }
}
