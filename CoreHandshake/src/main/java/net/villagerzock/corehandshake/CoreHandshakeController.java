package net.villagerzock.corehandshake;

import jakarta.validation.Valid;
import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.CommandRequest;
import net.villagerzock.corehandshake.dto.NetworkPoint;
import net.villagerzock.corehandshake.dto.NodeMetadata;
import net.villagerzock.corehandshake.dto.ServerInfo;
import net.villagerzock.corehandshake.dto.ServerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/core-handshake/v1")
public class CoreHandshakeController {
    private final CoreHandshakeProvider provider;

    public CoreHandshakeController(CoreHandshakeProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/servers")
    public List<ServerInfo> getRunningServers() {
        return provider.getRunningServers();
    }

    @GetMapping("/servers/{serverName}")
    public ServerInfo getServer(@PathVariable String serverName) {
        return provider.getServer(serverName).orElseThrow(() -> notFound("Server", serverName));
    }

    @GetMapping("/templates")
    public List<ServerTemplate> getTemplates() {
        return provider.getTemplates();
    }

    @GetMapping("/proxy/logs")
    public List<String> getProxyLogs() {
        return provider.getProxyLogs();
    }

    @GetMapping("/servers/{serverName}/logs")
    public List<String> getServerLogs(@PathVariable String serverName) {
        return provider.getServerLogs(serverName);
    }

    @PostMapping("/proxy/commands")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void executeProxyCommand(@Valid @RequestBody CommandRequest request) {
        provider.executeProxyCommand(request.command().trim());
    }

    @PostMapping("/servers/{serverName}/commands")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void executeServerCommand(
            @PathVariable String serverName,
            @Valid @RequestBody CommandRequest request
    ) {
        provider.executeServerCommand(serverName, request.command().trim());
    }

    @GetMapping("/metadata")
    public NodeMetadata getMetadata() {
        return provider.getMetadata();
    }

    @GetMapping("/proxy/metrics/player-count")
    public List<ChartPoint> getProxyPlayerCount(
            @RequestParam(defaultValue = "days") String range
    ) {
        MetricRange metricRange = MetricRange.parse(range);
        if (metricRange != MetricRange.DAYS && metricRange != MetricRange.HOURS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player range must be days or hours");
        }
        return provider.getProxyPlayerCount(metricRange);
    }

    @GetMapping("/proxy/metrics/network")
    public List<NetworkPoint> getProxyNetwork(
            @RequestParam(defaultValue = "days") String range
    ) {
        MetricRange metricRange = MetricRange.parse(range);
        if (metricRange != MetricRange.DAYS && metricRange != MetricRange.MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Network range must be days or minutes");
        }
        return provider.getProxyNetwork(metricRange);
    }

    @GetMapping("/servers/{serverName}/metrics/player-count")
    public List<ChartPoint> getServerPlayerCount(@PathVariable String serverName) {
        return provider.getServerPlayerCount(serverName);
    }

    @GetMapping("/servers/{serverName}/metrics/network")
    public List<NetworkPoint> getServerNetwork(@PathVariable String serverName) {
        return provider.getServerNetwork(serverName);
    }

    private ResponseStatusException notFound(String resource, String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " " + name + " not found");
    }
}
