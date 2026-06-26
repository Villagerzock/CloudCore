package net.villagerzock.backend.websocket;

import net.villagerzock.backend.repository.AuthTokenRepository;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.ConsoleService;
import net.villagerzock.backend.service.AuthService;
import net.villagerzock.backend.service.NodeHandshakeClient;
import net.villagerzock.backend.service.NodePermissionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConsoleWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConsoleService consoleService;
    private final NodePermissionService permissions;
    private final AuthTokenRepository tokens;
    private final Set<String> initializedConsoles = ConcurrentHashMap.newKeySet();
    private final Map<Subscription, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Subscription> sessionSubscriptions = new ConcurrentHashMap<>();

    public ConsoleWebSocketHandler(
            ObjectMapper objectMapper,
            ConsoleService consoleService,
            NodePermissionService permissions,
            AuthTokenRepository tokens
    ) {
        this.objectMapper = objectMapper;
        this.consoleService = consoleService;
        this.permissions = permissions;
        this.tokens = tokens;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long nodeId = getNodeId(session.getUri());
        String console = getQueryParameter(session.getUri(), "console");
        String username = authenticate(session.getUri());
        if (nodeId == null || console == null || console.isBlank() || username == null) {
            send(session, new ConsoleMessage("system", java.util.List.of(
                    "WebSocket query parameters 'node', 'console' and 'token' are required")));
            return;
        }

        register(session, nodeId, console);
        sendInitialLogs(session, username, nodeId, console);
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
        String username = authenticate(session.getUri());
        if (nodeId == null) {
            send(session, new ConsoleMessage("system", java.util.List.of(
                    "WebSocket query parameter 'node' is required")));
            return;
        }
        if (username == null) {
            send(session, new ConsoleMessage("system", java.util.List.of("Authentication required")));
            return;
        }

        register(session, nodeId, request.console());

        String consoleKey = session.getId() + ':' + request.console();
        if (initializedConsoles.add(consoleKey)) {
            sendInitialLogs(session, username, nodeId, request.console());
        }
        if (Boolean.TRUE.equals(request.subscribe())) {
            return;
        }
        if (request.command() == null || request.command().isBlank()) {
            send(session, new ConsoleMessage(request.console(), java.util.List.of("Command is required")));
            return;
        }
        try {
            permissions.require(username, nodeId, statusPermission(request.console()));
            consoleService.execute(nodeId, request.console(), request.command().trim());
        } catch (RuntimeException exception) {
            String console = NodeHandshakeClient.isNodeUnavailable(exception) ? request.console() : "system";
            send(session, new ConsoleMessage(console, java.util.List.of(messageFor(exception))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        initializedConsoles.removeIf(key -> key.startsWith(session.getId() + ':'));
        unregister(session);
    }

    private void send(WebSocketSession session, ConsoleMessage response) throws IOException {
        sendObject(session, response);
    }

    private void sendObject(WebSocketSession session, Object response) throws IOException {
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

    public void broadcastMetrics(long nodeId, MetricConsoleMessage message) {
        Subscription subscription = new Subscription(nodeId, message.console());
        Set<WebSocketSession> sessions = subscriptions.get(subscription);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : List.copyOf(sessions)) {
            if (!session.isOpen()) {
                unregister(session);
                continue;
            }
            try {
                sendObject(session, message);
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

    private void sendInitialLogs(WebSocketSession session, String username, long nodeId, String console) throws IOException {
        String consoleKey = session.getId() + ':' + console;
        initializedConsoles.add(consoleKey);
        try {
            permissions.require(username, nodeId, readPermission(console));
            send(session, new ConsoleMessage(console, consoleService.getLogs(nodeId, console)));
        } catch (RuntimeException exception) {
            String responseConsole = NodeHandshakeClient.isNodeUnavailable(exception) ? console : "system";
            send(session, new ConsoleMessage(responseConsole, java.util.List.of(messageFor(exception))));
        }
    }

    private String messageFor(RuntimeException exception) {
        if (NodeHandshakeClient.isNodeUnavailable(exception)) {
            return "Cannot connect to Node";
        }
        return exception.getMessage();
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

    private String authenticate(URI uri) {
        String rawToken = getQueryParameter(uri, "token");
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return tokens.findActiveByHash(AuthService.hashToken(rawToken), Instant.now())
                .map(token -> token.getUser().getUsername())
                .orElse(null);
    }

    private NodePermission readPermission(String console) {
        return "proxy".equalsIgnoreCase(console)
                ? NodePermission.PROXY_PAGE
                : NodePermission.SERVER_CONSOLE;
    }

    private NodePermission statusPermission(String console) {
        return "proxy".equalsIgnoreCase(console)
                ? NodePermission.PROXY_STATUS
                : NodePermission.SERVER_STATUS;
    }
}
