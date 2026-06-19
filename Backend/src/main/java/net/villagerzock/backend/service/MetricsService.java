package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricsService {
    private final NodeHandshakeClient handshakeClient;

    public MetricsService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId) {
        return handshakeClient.getProxyPlayerCount(nodeId);
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId) {
        return handshakeClient.getProxyNetwork(nodeId);
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, String serverName) {
        return handshakeClient.getServerPlayerCount(nodeId, serverName);
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, String serverName) {
        return handshakeClient.getServerNetwork(nodeId, serverName);
    }
}
