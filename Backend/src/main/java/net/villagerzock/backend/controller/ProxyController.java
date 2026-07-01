package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.MetricsService;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/proxy")
public class ProxyController {
    private final MetricsService metricsService;
    private final ServerService serverService;
    private final NodePermissionService permissions;

    public ProxyController(MetricsService metricsService, ServerService serverService, NodePermissionService permissions) {
        this.metricsService = metricsService;
        this.serverService = serverService;
        this.permissions = permissions;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startProxy(@RequestAttribute("cloudcore.nodeId") long nodeId, Authentication authentication) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        serverService.startProxy(nodeId);
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopProxy(@RequestAttribute("cloudcore.nodeId") long nodeId, Authentication authentication) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        serverService.stopProxy(nodeId);
    }

    @PostMapping("/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartProxy(@RequestAttribute("cloudcore.nodeId") long nodeId, Authentication authentication) {
        permissions.require(authentication, nodeId, NodePermission.SERVER_STATUS);
        serverService.restartProxy(nodeId);
    }

    @GetMapping("/metrics/player-count")
    public List<ChartPointDto> getPlayerCount(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @RequestParam(defaultValue = "days") String range
    ) {
        return metricsService.getProxyPlayerCount(nodeId, range);
    }

    @GetMapping("/metrics/network")
    public List<NetworkPointDto> getNetwork(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @RequestParam(defaultValue = "days") String range
    ) {
        return metricsService.getProxyNetwork(nodeId, range);
    }
}
