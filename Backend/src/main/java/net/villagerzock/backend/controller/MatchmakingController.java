package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.MatchmakingConfigurationDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {
    private final ServerService serverService;
    private final NodePermissionService permissions;

    public MatchmakingController(ServerService serverService, NodePermissionService permissions) {
        this.serverService = serverService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<MatchmakingConfigurationDto> getMatchmakingConfigurations(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.MATCHMAKING_PAGE);
        return serverService.getMatchmakingConfigurations(nodeId);
    }

    @GetMapping("/templates")
    public List<ServerTemplateDto> getAvailableTemplates(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.MATCHMAKING_PAGE);
        return serverService.getTemplates(nodeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchmakingConfigurationDto createMatchmakingConfiguration(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody MatchmakingConfigurationDto configuration
    ) {
        permissions.require(authentication, nodeId, NodePermission.MATCHMAKING_PAGE);
        return serverService.saveMatchmakingConfiguration(nodeId, configuration);
    }

    @PatchMapping("/{name}")
    public MatchmakingConfigurationDto updateMatchmakingConfiguration(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String name,
            @Valid @RequestBody MatchmakingConfigurationDto configuration
    ) {
        permissions.require(authentication, nodeId, NodePermission.MATCHMAKING_PAGE);
        return serverService.saveMatchmakingConfiguration(nodeId, configuration.withName(name));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatchmakingConfiguration(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String name
    ) {
        permissions.require(authentication, nodeId, NodePermission.MATCHMAKING_PAGE);
        serverService.deleteMatchmakingConfiguration(nodeId, name);
    }
}
