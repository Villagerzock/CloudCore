package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.LaunchServerRequest;
import net.villagerzock.backend.dto.LaunchServerResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class ServerController {
    private final ServerService serverService;
    private final MetricsService metricsService;
    private final NodePermissionService permissions;

    public ServerController(
            ServerService serverService,
            MetricsService metricsService,
            NodePermissionService permissions
    ) {
        this.serverService = serverService;
        this.metricsService = metricsService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<ServerDto> getRunningServers(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.SERVERS_PAGE);
        return serverService.getRunningServers(nodeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LaunchServerResponse launchServer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody LaunchServerRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        return serverService.launchServer(nodeId, new LaunchServerRequest(
                request.template().trim(),
                request.singleton()));
    }

    @GetMapping("/{name}")
    public ServerDto getServerByName(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String name
    ) {
        permissions.require(authentication, nodeId, NodePermission.SERVERS_PAGE);
        return serverService.getServerByName(nodeId, name);
    }

    @PostMapping("/{name}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopServer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String name
    ) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        serverService.stopServer(nodeId, name);
    }

    @PostMapping("/{name}/restart")
    public LaunchServerResponse restartServer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String name
    ) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        return serverService.restartServer(nodeId, name);
    }

    @GetMapping("/{name}/metrics/player-count")
    public List<ChartPointDto> getPlayerCount(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @PathVariable String name
    ) {
        return metricsService.getServerPlayerCount(nodeId, name);
    }

    @GetMapping("/{name}/metrics/network")
    public List<NetworkPointDto> getNetwork(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @PathVariable String name
    ) {
        return metricsService.getServerNetwork(nodeId, name);
    }
}
