package com.example.hr_management_backend.features.scribbil.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DrawGuessWebSocketConfig implements WebSocketConfigurer {

    private final DrawGuessWebSocketHandler drawGuessWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(drawGuessWebSocketHandler, "/ws/scribbil", "/ws/draw-and-guess")
                .setAllowedOrigins("*");
    }
}
