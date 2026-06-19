package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.NodeMetadataDto;
import net.villagerzock.backend.service.NodeHandshakeClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {
    private final NodeHandshakeClient handshakeClient;

    public MetadataController(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
    }

    @GetMapping
    public NodeMetadataDto getMetadata(@RequestAttribute("cloudcore.nodeId") long nodeId) {
        return handshakeClient.getMetadata(nodeId);
    }
}
