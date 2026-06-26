package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.NodeUserResponse;
import net.villagerzock.backend.service.NodeUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final NodeUserService nodeUserService;

    public MeController(NodeUserService nodeUserService) {
        this.nodeUserService = nodeUserService;
    }

    @GetMapping
    public NodeUserResponse getCurrentUser(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        return nodeUserService.getCurrentUser(nodeId, authentication.getName());
    }
}
