package com.example.hr_management_backend.features.tambola.websocket;

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
public class TambolaWebSocketHandler extends TextWebSocketHandler {

    private final TambolaWebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            log.warn("Rejected Tambola WebSocket connection: missing query parameters");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> params = parseQueryParams(uri.getQuery());
        String roomCode = params.get("roomCode");

        if (roomCode == null || roomCode.isBlank()) {
            log.warn("Rejected Tambola WebSocket connection: missing roomCode parameter");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sessionManager.registerSession(roomCode, session);

        // Send connection acknowledgement
        Map<String, Object> ack = Map.of(
                "type", "CONNECTED",
                "roomCode", roomCode.toUpperCase(),
                "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
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
            log.debug("Received non-JSON or unsupported message: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Transport error on Tambola WebSocket session {}: {}", session.getId(), exception.getMessage());
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
