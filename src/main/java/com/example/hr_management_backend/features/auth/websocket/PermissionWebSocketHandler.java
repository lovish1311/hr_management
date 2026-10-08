package com.example.hr_management_backend.features.auth.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionWebSocketHandler extends TextWebSocketHandler {

    private final PermissionWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            log.warn("Rejected Permission WebSocket connection: missing query parameters");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> params = parseQueryParams(uri.getQuery());
        String employeeIdStr = params.get("employeeId");

        if (employeeIdStr == null || employeeIdStr.isBlank()) {
            log.warn("Rejected Permission WebSocket connection: missing employeeId parameter");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            Long employeeId = Long.parseLong(employeeIdStr.trim());
            sessionManager.registerSession(employeeId, session);

            // Send connection acknowledgement
            Map<String, Object> ack = Map.of(
                    "type", "CONNECTED",
                    "employeeId", employeeId,
                    "sessionId", session.getId()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (NumberFormatException e) {
            log.warn("Rejected Permission WebSocket connection: invalid employeeId '{}'", employeeIdStr);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText();

            if ("PING".equalsIgnoreCase(type)) {
                Map<String, Object> pong = Map.of(
                        "type", "PONG",
                        "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            }
        } catch (Exception e) {
            log.debug("Received non-JSON or unsupported message in Permission WS: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Transport error on Permission WebSocket session {}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (idx > 0) {
                map.put(pair.substring(0, idx), "");
            }
        }
        return map;
    }
}
