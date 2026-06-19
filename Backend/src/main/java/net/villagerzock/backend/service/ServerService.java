package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class ServerService {
    private final NodeHandshakeClient handshakeClient;

    public ServerService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    public List<ServerDto> getRunningServers(long nodeId) {
        return handshakeClient.getServers(nodeId);
    }

    public ServerDto getServerByName(long nodeId, String serverName) {
        return handshakeClient.getServer(nodeId, serverName);
    }

    public List<ServerTemplateDto> getTemplates(long nodeId) {
        return handshakeClient.getTemplates(nodeId);
    }
}
