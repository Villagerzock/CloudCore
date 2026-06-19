package net.villagerzock.backend;

import net.villagerzock.backend.dto.ConsoleLogPushRequest;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.service.ConsoleLogPushService;
import net.villagerzock.backend.websocket.ConsoleWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleLogPushServiceTests {
    private final CloudCoreNodeRepository nodes = mock(CloudCoreNodeRepository.class);
    private final ConsoleWebSocketHandler webSockets = mock(ConsoleWebSocketHandler.class);

    @Test
    void routesExactSourceIpToItsNode() {
        when(nodes.findLinkedNodeIdsByIpAddress("203.0.113.10")).thenReturn(List.of(7L));
        ConsoleLogPushService service = new ConsoleLogPushService(nodes, webSockets, "127.0.0.1");

        service.push("203.0.113.10", new ConsoleLogPushRequest(
                "server-survival-1",
                List.of("Server started")));

        verify(webSockets).broadcast(7L, "server-survival-1", List.of("Server started"));
    }

    @Test
    void mapsConfiguredDockerGatewayToSingleLoopbackNode() {
        when(nodes.findLinkedNodeIdsByIpAddress("192.0.2.1")).thenReturn(List.of());
        when(nodes.findLinkedLoopbackNodeIds()).thenReturn(List.of(1L));
        ConsoleLogPushService service = new ConsoleLogPushService(nodes, webSockets, "192.0.2.1");

        service.push("192.0.2.1", new ConsoleLogPushRequest("proxy", List.of("Proxy started")));

        verify(webSockets).broadcast(1L, "proxy", List.of("Proxy started"));
    }

    @Test
    void rejectsUnknownSourceIp() {
        when(nodes.findLinkedNodeIdsByIpAddress("198.51.100.20")).thenReturn(List.of());
        ConsoleLogPushService service = new ConsoleLogPushService(nodes, webSockets, "127.0.0.1");

        assertThatThrownBy(() -> service.push(
                "198.51.100.20",
                new ConsoleLogPushRequest("proxy", List.of("forged"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
