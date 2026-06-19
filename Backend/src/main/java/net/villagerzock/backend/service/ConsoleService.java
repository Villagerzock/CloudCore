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
        return handshakeClient.getServerLogs(nodeId, serverName(console));
    }

    public void execute(long nodeId, String console, String command) {
        if ("proxy".equalsIgnoreCase(console)) {
            handshakeClient.executeProxyCommand(nodeId, command);
            return;
        }
        handshakeClient.executeServerCommand(nodeId, serverName(console), command);
    }

    private String serverName(String console) {
        if (!console.startsWith("server-") || console.length() == "server-".length()) {
            throw new IllegalArgumentException("Console must be 'proxy' or 'server-{server-name}'");
        }
        return console.substring("server-".length());
    }
}
