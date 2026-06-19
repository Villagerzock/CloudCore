package net.villagerzock.velocity.controller;

import net.villagerzock.velocity.dto.NetworkMetricPoint;
import net.villagerzock.velocity.dto.PlayerMetricPoint;
import net.villagerzock.velocity.service.MetricCollectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricController {
    private final MetricCollectionService metrics;

    public MetricController(MetricCollectionService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/proxy/player-count")
    public List<PlayerMetricPoint> proxyPlayers(@RequestParam(defaultValue = "days") String range) {
        if (!range.equals("days") && !range.equals("hours")) {
            throw new IllegalArgumentException("Player range must be days or hours");
        }
        return metrics.playerMetrics("proxy", range);
    }

    @GetMapping("/proxy/network")
    public List<NetworkMetricPoint> proxyNetwork(@RequestParam(defaultValue = "days") String range) {
        if (!range.equals("days") && !range.equals("minutes")) {
            throw new IllegalArgumentException("Network range must be days or minutes");
        }
        return metrics.networkMetrics(range);
    }

    @GetMapping("/servers/{serverName}/player-count")
    public List<PlayerMetricPoint> serverPlayers(@PathVariable String serverName) {
        return metrics.playerMetrics(serverName, "minutes");
    }
}
