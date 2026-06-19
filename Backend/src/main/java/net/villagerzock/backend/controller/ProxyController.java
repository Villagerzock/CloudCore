package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/proxy/metrics")
public class ProxyController {
    private final MetricsService metricsService;

    public ProxyController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/player-count")
    public List<ChartPointDto> getPlayerCount(@RequestAttribute("cloudcore.nodeId") long nodeId) {
        return metricsService.getProxyPlayerCount(nodeId);
    }

    @GetMapping("/network")
    public List<NetworkPointDto> getNetwork(@RequestAttribute("cloudcore.nodeId") long nodeId) {
        return metricsService.getProxyNetwork(nodeId);
    }
}
