package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.NodeResponse;
import net.villagerzock.backend.service.NodeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {
    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping
    public List<NodeResponse> getNodes(Authentication authentication) {
        return nodeService.getLinkedNodes(authentication.getName());
    }
}
