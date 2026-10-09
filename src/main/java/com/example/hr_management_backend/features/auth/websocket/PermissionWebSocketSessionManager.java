package com.example.hr_management_backend.features.auth.websocket;

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
public class PermissionWebSocketSessionManager {

    private final ObjectMapper objectMapper;

    // employeeId -> Set of active WebSocket sessions
    private final Map<Long, Set<WebSocketSession>> employeeSessions = new ConcurrentHashMap<>();

    // sessionId -> employeeId
    private final Map<String, Long> sessionEmployees = new ConcurrentHashMap<>();

    public void registerSession(Long employeeId, WebSocketSession session) {
        if (employeeId == null || session == null) return;
        employeeSessions.computeIfAbsent(employeeId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionEmployees.put(session.getId(), employeeId);
        log.info("Registered WebSocket session {} for employeeId {}. Active sessions for employee: {}",
                session.getId(), employeeId, employeeSessions.get(employeeId).size());
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) return;
        Long employeeId = sessionEmployees.remove(session.getId());
        if (employeeId != null) {
            Set<WebSocketSession> sessions = employeeSessions.get(employeeId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    employeeSessions.remove(employeeId);
                }
            }
            log.info("Removed WebSocket session {} for employeeId {}", session.getId(), employeeId);
        }
    }

    public void sendPermissionUpdate(Long employeeId, Map<String, Object> payload) {
        if (employeeId == null || payload == null) return;
        Set<WebSocketSession> sessions = employeeSessions.get(employeeId);
        if (sessions == null || sessions.isEmpty()) {
            log.info("No active WebSocket sessions found for employeeId {}. Update persisted in DB.", employeeId);
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
                        log.info("Dispatched live permission event to session {} for employeeId {}", session.getId(), employeeId);
                    } catch (IOException e) {
                        log.warn("Failed to send permission message to session {}: {}", session.getId(), e.getMessage());
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
            log.error("Failed to serialize permission event for employeeId {}: {}", employeeId, e.getMessage(), e);
        }
    }

    public int getActiveSessionCount(Long employeeId) {
        if (employeeId == null) return 0;
        Set<WebSocketSession> sessions = employeeSessions.get(employeeId);
        return sessions != null ? sessions.size() : 0;
    }
}
