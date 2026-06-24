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
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class ServerController {
    private final ServerService serverService;
    private final MetricsService metricsService;

    public ServerController(ServerService serverService, MetricsService metricsService) {
        this.serverService = serverService;
        this.metricsService = metricsService;
    }

    @GetMapping
    public List<ServerDto> getRunningServers(@RequestAttribute("cloudcore.nodeId") long nodeId) {
        return serverService.getRunningServers(nodeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LaunchServerResponse launchServer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @Valid @RequestBody LaunchServerRequest request
    ) {
        return serverService.launchServer(nodeId, new LaunchServerRequest(
                request.template().trim(),
                request.singleton()));
    }

    @GetMapping("/{name}")
    public ServerDto getServerByName(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @PathVariable String name
    ) {
        return serverService.getServerByName(nodeId, name);
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
