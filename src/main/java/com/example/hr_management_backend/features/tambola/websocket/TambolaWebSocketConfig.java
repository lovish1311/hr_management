package com.example.hr_management_backend.features.tambola.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class TambolaWebSocketConfig implements WebSocketConfigurer {

    private final TambolaWebSocketHandler tambolaWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tambolaWebSocketHandler, "/ws/tambola")
                .setAllowedOrigins("*");
    }
}
