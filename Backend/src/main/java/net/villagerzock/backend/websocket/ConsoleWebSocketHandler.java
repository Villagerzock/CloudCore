package net.villagerzock.backend.websocket;

import net.villagerzock.backend.service.ConsoleService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConsoleWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConsoleService consoleService;
    private final Set<String> initializedConsoles = ConcurrentHashMap.newKeySet();

    public ConsoleWebSocketHandler(ObjectMapper objectMapper, ConsoleService consoleService) {
        this.objectMapper = objectMapper;
        this.consoleService = consoleService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        ConsoleCommand request;
        try {
            request = objectMapper.readValue(message.getPayload(), ConsoleCommand.class);
        } catch (Exception exception) {
            send(session, new ConsoleMessage("system", java.util.List.of("Invalid console message")));
            return;
        }

        if (request.console() == null || request.console().isBlank()) {
            send(session, new ConsoleMessage("system", java.util.List.of("Console is required")));
            return;
        }

        String consoleKey = session.getId() + ':' + request.console();
        if (initializedConsoles.add(consoleKey)) {
            send(session, new ConsoleMessage(request.console(), consoleService.getWelcomeLines(request.console())));
        }
        if (request.subscribe()) {
            return;
        }
        if (request.command() == null || request.command().isBlank()) {
            send(session, new ConsoleMessage(request.console(), java.util.List.of("Command is required")));
            return;
        }
        send(session, new ConsoleMessage(
                request.console(),
                consoleService.execute(request.console(), request.command().trim())));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        initializedConsoles.removeIf(key -> key.startsWith(session.getId() + ':'));
    }

    private void send(WebSocketSession session, ConsoleMessage response) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
}
