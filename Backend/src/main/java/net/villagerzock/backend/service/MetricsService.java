package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MetricsService {
    private final NodeHandshakeClient handshakeClient;

    public MetricsService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId, String range) {
        requireRange(range, "days", "hours", "minutes");
        return handshakeClient.getProxyPlayerCount(nodeId, range);
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId, String range) {
        requireRange(range, "days", "minutes");
        return handshakeClient.getProxyNetwork(nodeId, range);
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, String serverName) {
        return handshakeClient.getServerPlayerCount(nodeId, serverName);
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, String serverName) {
        return handshakeClient.getServerNetwork(nodeId, serverName);
    }

    private void requireRange(String range, String... allowed) {
        if (java.util.Arrays.stream(allowed).noneMatch(range::equals)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid metric range");
        }
    }
}
