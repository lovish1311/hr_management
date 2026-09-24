package com.example.hr_management_backend.features.tambola.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class TambolaWebSocketSessionManager {

    private final ObjectMapper objectMapper;

    // RoomCode -> Set of active WebSocket sessions
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    // SessionId -> RoomCode
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();

    public void registerSession(String roomCode, WebSocketSession session) {
        if (roomCode == null || session == null) return;
        String normalizedCode = roomCode.trim().toUpperCase();
        roomSessions.computeIfAbsent(normalizedCode, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionRooms.put(session.getId(), normalizedCode);
        log.info("Registered WebSocket session {} for Tambola room {}. Total in room: {}",
                session.getId(), normalizedCode, roomSessions.get(normalizedCode).size());
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        String roomCode = sessionRooms.remove(session.getId());
        if (roomCode != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomCode);
                }
            }
            log.info("Removed WebSocket session {} from Tambola room {}", session.getId(), roomCode);
        }
    }

    public void broadcast(String roomCode, Object payload) {
        if (roomCode == null || payload == null) return;
        String normalizedCode = roomCode.trim().toUpperCase();
        Set<WebSocketSession> sessions = roomSessions.get(normalizedCode);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active WebSocket sessions to broadcast in room {}", normalizedCode);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                        try {
                            session.close();
                        } catch (IOException ignored) {}
                        removeSession(session);
                    }
                } else {
                    removeSession(session);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket broadcast payload for room {}: {}", normalizedCode, e.getMessage(), e);
        }
    }

    public int getActiveSessionCount(String roomCode) {
        if (roomCode == null) return 0;
        Set<WebSocketSession> sessions = roomSessions.get(roomCode.trim().toUpperCase());
        return sessions != null ? sessions.size() : 0;
    }
}
