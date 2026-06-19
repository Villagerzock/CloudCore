package net.villagerzock.backend.websocket;

import net.villagerzock.backend.service.ConsoleService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConsoleWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConsoleService consoleService;
    private final Set<String> initializedConsoles = ConcurrentHashMap.newKeySet();
    private final Map<Subscription, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Subscription> sessionSubscriptions = new ConcurrentHashMap<>();

    public ConsoleWebSocketHandler(ObjectMapper objectMapper, ConsoleService consoleService) {
        this.objectMapper = objectMapper;
        this.consoleService = consoleService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long nodeId = getNodeId(session.getUri());
        String console = getQueryParameter(session.getUri(), "console");
        if (nodeId == null || console == null || console.isBlank()) {
            send(session, new ConsoleMessage("system", java.util.List.of(
                    "WebSocket query parameters 'node' and 'console' are required")));
            return;
        }

        register(session, nodeId, console);
        sendInitialLogs(session, nodeId, console);
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

        Long nodeId = getNodeId(session.getUri());
        if (nodeId == null) {
            send(session, new ConsoleMessage("system", java.util.List.of(
                    "WebSocket query parameter 'node' is required")));
            return;
        }

        register(session, nodeId, request.console());

        String consoleKey = session.getId() + ':' + request.console();
        if (initializedConsoles.add(consoleKey)) {
            sendInitialLogs(session, nodeId, request.console());
        }
        if (Boolean.TRUE.equals(request.subscribe())) {
            return;
        }
        if (request.command() == null || request.command().isBlank()) {
            send(session, new ConsoleMessage(request.console(), java.util.List.of("Command is required")));
            return;
        }
        try {
            consoleService.execute(nodeId, request.console(), request.command().trim());
        } catch (RuntimeException exception) {
            send(session, new ConsoleMessage("system", java.util.List.of(exception.getMessage())));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        initializedConsoles.removeIf(key -> key.startsWith(session.getId() + ':'));
        unregister(session);
    }

    private void send(WebSocketSession session, ConsoleMessage response) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    public void broadcast(long nodeId, String console, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Subscription subscription = new Subscription(nodeId, console);
        Set<WebSocketSession> sessions = subscriptions.get(subscription);
        if (sessions == null) {
            return;
        }
        ConsoleMessage message = new ConsoleMessage(console, lines);
        for (WebSocketSession session : List.copyOf(sessions)) {
            if (!session.isOpen()) {
                unregister(session);
                continue;
            }
            try {
                send(session, message);
            } catch (IOException exception) {
                unregister(session);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Long getNodeId(URI uri) {
        String value = getQueryParameter(uri, "node");
        if (value == null) {
            return null;
        }
        try {
            long nodeId = Long.parseLong(value);
            return nodeId > 0 ? nodeId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getQueryParameter(URI uri, String name) {
        if (uri == null || uri.getRawQuery() == null) {
            return null;
        }
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && name.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendInitialLogs(WebSocketSession session, long nodeId, String console) throws IOException {
        String consoleKey = session.getId() + ':' + console;
        initializedConsoles.add(consoleKey);
        try {
            send(session, new ConsoleMessage(console, consoleService.getLogs(nodeId, console)));
        } catch (RuntimeException exception) {
            send(session, new ConsoleMessage("system", java.util.List.of(exception.getMessage())));
        }
    }

    private void register(WebSocketSession session, long nodeId, String console) {
        Subscription subscription = new Subscription(nodeId, console);
        Subscription previous = sessionSubscriptions.put(session.getId(), subscription);
        if (previous != null && !previous.equals(subscription)) {
            removeSession(previous, session);
        }
        subscriptions.computeIfAbsent(subscription, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private void unregister(WebSocketSession session) {
        Subscription subscription = sessionSubscriptions.remove(session.getId());
        if (subscription != null) {
            removeSession(subscription, session);
        }
    }

    private void removeSession(Subscription subscription, WebSocketSession session) {
        subscriptions.computeIfPresent(subscription, (key, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private record Subscription(long nodeId, String console) {
    }
}
