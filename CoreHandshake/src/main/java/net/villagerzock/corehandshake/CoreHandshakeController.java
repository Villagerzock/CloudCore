package net.villagerzock.corehandshake;

import jakarta.validation.Valid;
import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.CommandRequest;
import net.villagerzock.corehandshake.dto.NetworkPoint;
import net.villagerzock.corehandshake.dto.NodeMetadata;
import net.villagerzock.corehandshake.dto.ServerInfo;
import net.villagerzock.corehandshake.dto.ServerTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping("/servers/{serverId}")
    public ServerInfo getServer(@PathVariable long serverId) {
        return provider.getServer(serverId).orElseThrow(() -> notFound("Server", serverId));
    }

    @GetMapping("/templates")
    public List<ServerTemplate> getTemplates() {
        return provider.getTemplates();
    }

    @GetMapping("/proxy/logs")
    public List<String> getProxyLogs() {
        return provider.getProxyLogs();
    }

    @GetMapping("/servers/{serverId}/logs")
    public List<String> getServerLogs(@PathVariable String serverId) {
        return provider.getServerLogs(serverId);
    }

    @PostMapping("/proxy/commands")
    public List<String> executeProxyCommand(@Valid @RequestBody CommandRequest request) {
        return provider.executeProxyCommand(request.command().trim());
    }

    @PostMapping("/servers/{serverId}/commands")
    public List<String> executeServerCommand(
            @PathVariable String serverId,
            @Valid @RequestBody CommandRequest request
    ) {
        return provider.executeServerCommand(serverId, request.command().trim());
    }

    @GetMapping("/metadata")
    public NodeMetadata getMetadata() {
        return provider.getMetadata();
    }

    @GetMapping("/proxy/metrics/player-count")
    public List<ChartPoint> getProxyPlayerCount() {
        return provider.getProxyPlayerCount();
    }

    @GetMapping("/proxy/metrics/network")
    public List<NetworkPoint> getProxyNetwork() {
        return provider.getProxyNetwork();
    }

    @GetMapping("/servers/{serverId}/metrics/player-count")
    public List<ChartPoint> getServerPlayerCount(@PathVariable long serverId) {
        return provider.getServerPlayerCount(serverId);
    }

    @GetMapping("/servers/{serverId}/metrics/network")
    public List<NetworkPoint> getServerNetwork(@PathVariable long serverId) {
        return provider.getServerNetwork(serverId);
    }

    private ResponseStatusException notFound(String resource, long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " " + id + " not found");
    }
}
