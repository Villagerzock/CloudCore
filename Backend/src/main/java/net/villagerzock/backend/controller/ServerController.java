package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<ServerDto> getRunningServers() {
        return serverService.getRunningServers();
    }

    @GetMapping("/{id}")
    public ServerDto getServerById(@PathVariable long id) {
        return serverService.getServerById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Server " + id + " not found"));
    }

    @GetMapping("/{id}/metrics/player-count")
    public List<ChartPointDto> getPlayerCount(@PathVariable long id) {
        return metricsService.getServerPlayerCount(id);
    }

    @GetMapping("/{id}/metrics/network")
    public List<NetworkPointDto> getNetwork(@PathVariable long id) {
        return metricsService.getServerNetwork(id);
    }
}
