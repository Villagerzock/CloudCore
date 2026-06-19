package net.villagerzock.backend.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsoleService {
    private final NodeHandshakeClient handshakeClient;

    public ConsoleService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    public List<String> getLogs(long nodeId, String console) {
        if ("proxy".equalsIgnoreCase(console)) {
            return handshakeClient.getProxyLogs(nodeId);
        }
        return handshakeClient.getServerLogs(nodeId, console);
    }

    public void execute(long nodeId, String console, String command) {
        if ("proxy".equalsIgnoreCase(console)) {
            handshakeClient.executeProxyCommand(nodeId, command);
            return;
        }
        handshakeClient.executeServerCommand(nodeId, console, command);
    }
}
