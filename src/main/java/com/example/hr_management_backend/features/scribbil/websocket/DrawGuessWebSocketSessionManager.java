package com.example.hr_management_backend.features.scribbil.websocket;

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
public class DrawGuessWebSocketSessionManager {

    private final ObjectMapper objectMapper;

    // RoomCode -> Set of active WebSocket sessions
    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    // SessionId -> RoomCode
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();

    // SessionId -> EmployeeId
    private final Map<String, Long> sessionEmployees = new ConcurrentHashMap<>();

    public void registerSession(String roomCode, Long employeeId, WebSocketSession session) {
        if (roomCode == null || session == null) return;
        String normalizedCode = roomCode.trim().toUpperCase();
        roomSessions.computeIfAbsent(normalizedCode, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionRooms.put(session.getId(), normalizedCode);
        if (employeeId != null) {
            sessionEmployees.put(session.getId(), employeeId);
        }
        log.info("Registered Draw & Guess WebSocket session {} (empId={}) for room {}. Active: {}",
                session.getId(), employeeId, normalizedCode, roomSessions.get(normalizedCode).size());
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        String roomCode = sessionRooms.remove(session.getId());
        Long empId = sessionEmployees.remove(session.getId());
        if (roomCode != null) {
            Set<WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomCode);
                }
            }
            log.info("Removed Draw & Guess WebSocket session {} (empId={}) from room {}", session.getId(), empId, roomCode);
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

    public void sendToUser(String roomCode, Long targetEmployeeId, Object payload) {
        if (roomCode == null || targetEmployeeId == null || payload == null) return;
        String normalizedCode = roomCode.trim().toUpperCase();
        Set<WebSocketSession> sessions = roomSessions.get(normalizedCode);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                Long empId = sessionEmployees.get(session.getId());
                if (targetEmployeeId.equals(empId) && session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to send private message to employee {} in room {}: {}", targetEmployeeId, normalizedCode, e.getMessage());
        }
    }

    public int getActiveSessionCount(String roomCode) {
        if (roomCode == null) return 0;
        Set<WebSocketSession> sessions = roomSessions.get(roomCode.trim().toUpperCase());
        return sessions != null ? sessions.size() : 0;
    }

    public String getRoomForSession(String sessionId) {
        return sessionRooms.get(sessionId);
    }

    public Long getEmployeeForSession(String sessionId) {
        return sessionEmployees.get(sessionId);
    }
}
