package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.NodeResponse;
import net.villagerzock.backend.entity.CloudCoreNode;
import net.villagerzock.backend.entity.CloudCoreServer;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NodeService {
    private final CloudCoreNodeRepository nodes;

    public NodeService(CloudCoreNodeRepository nodes) {
        this.nodes = nodes;
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> getLinkedNodes(String username) {
        return nodes.findLinkedByUsername(username).stream()
                .map(this::toResponse)
                .toList();
    }

    private NodeResponse toResponse(CloudCoreNode node) {
        CloudCoreServer server = node.getServer();
        return new NodeResponse(
                node.getId(),
                server.getId(),
                server.getName(),
                server.getIpAddress(),
                server.getLinkedAt());
    }
}
