package com.example.hr_management_backend.features.auth.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class PermissionWebSocketConfig implements WebSocketConfigurer {

    private final PermissionWebSocketHandler permissionWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(permissionWebSocketHandler, "/ws/permissions")
                .setAllowedOrigins("*");
    }
}
