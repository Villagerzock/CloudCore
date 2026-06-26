package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
public class MetricsService {
    private final NodeHandshakeClient handshakeClient;

    public MetricsService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId, String range) {
        requireRange(range, "days", "hours", "minutes");
        return playerCountWhenNodeUnavailable(() -> handshakeClient.getProxyPlayerCount(nodeId, range));
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId, String range) {
        requireRange(range, "days", "minutes");
        return networkWhenNodeUnavailable(() -> handshakeClient.getProxyNetwork(nodeId, range));
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, String serverName) {
        return playerCountWhenNodeUnavailable(() -> handshakeClient.getServerPlayerCount(nodeId, serverName));
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, String serverName) {
        return networkWhenNodeUnavailable(() -> handshakeClient.getServerNetwork(nodeId, serverName));
    }

    private List<ChartPointDto> playerCountWhenNodeUnavailable(Supplier<List<ChartPointDto>> action) {
        try {
            return action.get();
        } catch (ResponseStatusException exception) {
            if (NodeHandshakeClient.isNodeUnavailable(exception)) {
                return List.of(new ChartPointDto(now(), 0));
            }
            throw exception;
        }
    }

    private List<NetworkPointDto> networkWhenNodeUnavailable(Supplier<List<NetworkPointDto>> action) {
        try {
            return action.get();
        } catch (ResponseStatusException exception) {
            if (NodeHandshakeClient.isNodeUnavailable(exception)) {
                return List.of(new NetworkPointDto(now(), 0, 0));
            }
            throw exception;
        }
    }

    private String now() {
        return Instant.now().toString();
    }

    private void requireRange(String range, String... allowed) {
        if (java.util.Arrays.stream(allowed).noneMatch(range::equals)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid metric range");
        }
    }
}
