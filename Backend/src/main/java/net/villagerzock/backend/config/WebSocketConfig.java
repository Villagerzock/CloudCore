package net.villagerzock.backend.config;

import net.villagerzock.backend.websocket.ConsoleWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ConsoleWebSocketHandler consoleWebSocketHandler;

    public WebSocketConfig(ConsoleWebSocketHandler consoleWebSocketHandler) {
        this.consoleWebSocketHandler = consoleWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consoleWebSocketHandler, "/ws/console")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
