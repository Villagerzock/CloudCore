package net.villagerzock.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.villagerzock.backend.dto.MetricPushRequest;
import net.villagerzock.backend.service.ConsoleLogPushService;
import net.villagerzock.backend.websocket.ConsoleWebSocketHandler;
import net.villagerzock.backend.websocket.MetricConsoleMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/core/metrics")
public class CoreMetricPushController {
    private final ConsoleLogPushService nodeResolver;
    private final ConsoleWebSocketHandler webSockets;

    public CoreMetricPushController(
            ConsoleLogPushService nodeResolver,
            ConsoleWebSocketHandler webSockets
    ) {
        this.nodeResolver = nodeResolver;
        this.webSockets = webSockets;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void push(HttpServletRequest servletRequest, @Valid @RequestBody MetricPushRequest request) {
        long nodeId = nodeResolver.resolveNode(servletRequest.getRemoteAddr());
        webSockets.broadcastMetrics(nodeId, new MetricConsoleMessage(
                request.console(),
                request.playerCount(),
                request.network()));
    }
}
