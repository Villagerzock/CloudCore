package net.villagerzock.corehandshake;

import jakarta.validation.Valid;
import net.villagerzock.corehandshake.dto.AddMaintenancePlayerRequest;
import net.villagerzock.corehandshake.dto.BannedPlayer;
import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.CommandRequest;
import net.villagerzock.corehandshake.dto.CreateBannedPlayerRequest;
import net.villagerzock.corehandshake.dto.CreateTemplateRequest;
import net.villagerzock.corehandshake.dto.FileDownload;
import net.villagerzock.corehandshake.dto.FileSystemResponse;
import net.villagerzock.corehandshake.dto.MatchmakingConfiguration;
import net.villagerzock.corehandshake.dto.MaintenanceStatus;
import net.villagerzock.corehandshake.dto.NetworkPoint;
import net.villagerzock.corehandshake.dto.LaunchServerRequest;
import net.villagerzock.corehandshake.dto.LaunchServerResponse;
import net.villagerzock.corehandshake.dto.NodeMetadata;
import net.villagerzock.corehandshake.dto.SaveTemplateFileRequest;
import net.villagerzock.corehandshake.dto.ServerInfo;
import net.villagerzock.corehandshake.dto.ServerTemplate;
import net.villagerzock.corehandshake.dto.TemplatePathRequest;
import net.villagerzock.corehandshake.dto.UpdateBannedPlayerRequest;
import net.villagerzock.corehandshake.dto.UploadTemplateFileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

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

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    public LaunchServerResponse launchServer(@Valid @RequestBody LaunchServerRequest request) {
        return new LaunchServerResponse(provider.launchServer(
                request.template().trim(),
                request.singleton()));
    }

    @GetMapping("/servers/{serverName}")
    public ServerInfo getServer(@PathVariable String serverName) {
        return provider.getServer(serverName).orElseThrow(() -> notFound("Server", serverName));
    }

    @PostMapping("/servers/{serverName}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopServer(@PathVariable String serverName) {
        provider.stopServer(serverName);
    }

    @PostMapping("/servers/{serverName}/restart")
    public LaunchServerResponse restartServer(@PathVariable String serverName) {
        return new LaunchServerResponse(provider.restartServer(serverName));
    }

    @PostMapping("/proxy/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startProxy() {
        provider.startProxy();
    }

    @PostMapping("/proxy/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopProxy() {
        provider.stopProxy();
    }

    @PostMapping("/proxy/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartProxy() {
        provider.restartProxy();
    }

    @GetMapping("/templates")
    public List<ServerTemplate> getTemplates() {
        return provider.getTemplates();
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ServerTemplate createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        try {
            return provider.createTemplate(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @GetMapping("/matchmaking")
    public List<MatchmakingConfiguration> getMatchmakingConfigurations() {
        return provider.getMatchmakingConfigurations();
    }

    @PostMapping("/matchmaking")
    public MatchmakingConfiguration saveMatchmakingConfiguration(
            @Valid @RequestBody MatchmakingConfiguration configuration
    ) {
        try {
            return provider.saveMatchmakingConfiguration(configuration);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/matchmaking/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatchmakingConfiguration(@PathVariable String name) {
        try {
            provider.deleteMatchmakingConfiguration(name);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/maintenance")
    public MaintenanceStatus getMaintenanceStatus() {
        return provider.getMaintenanceStatus();
    }

    @PostMapping("/maintenance/{state}")
    public MaintenanceStatus setMaintenanceActive(@PathVariable String state) {
        if (!state.equals("on") && !state.equals("off")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maintenance state must be on or off");
        }
        return provider.setMaintenanceActive(state.equals("on"));
    }

    @PostMapping("/maintenance/players")
    public MaintenanceStatus addMaintenancePlayer(@Valid @RequestBody AddMaintenancePlayerRequest request) {
        try {
            return provider.addMaintenancePlayer(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/maintenance/players/{uuid}")
    public MaintenanceStatus removeMaintenancePlayer(@PathVariable String uuid) {
        try {
            return provider.removeMaintenancePlayer(uuid);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/bans")
    public List<BannedPlayer> getBannedPlayers() {
        return provider.getBannedPlayers();
    }

    @PostMapping("/bans")
    @ResponseStatus(HttpStatus.CREATED)
    public BannedPlayer createBan(@Valid @RequestBody CreateBannedPlayerRequest request) {
        try {
            return provider.createBan(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PatchMapping("/bans/{uuid}")
    public BannedPlayer updateBan(
            @PathVariable String uuid,
            @Valid @RequestBody UpdateBannedPlayerRequest request
    ) {
        try {
            return provider.updateBan(uuid, request);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/bans/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBan(@PathVariable String uuid) {
        try {
            provider.deleteBan(uuid);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/templates/{template}/{*path}")
    public ResponseEntity<FileSystemResponse> getFileSystemPath(
            @PathVariable String template,
            @PathVariable String path
    ) {
        try {
            FileSystemResponse response = provider.getTemplateFileSystemPath(template, path);
            return ResponseEntity.status(response.isFile() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .body(response);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/templates/{template}/download/{*path}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String template,
            @PathVariable String path
    ) {
        try {
            FileDownload download = provider.downloadTemplateFile(template, path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.name() + "\"")
                    .contentType(MediaType.parseMediaType(download.type()))
                    .body(download.content());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/templates/{template}/content/{*path}")
    public ResponseEntity<byte[]> getFileContent(
            @PathVariable String template,
            @PathVariable String path
    ) {
        try {
            FileDownload content = provider.getTemplateFileContent(template, path);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(content.type()))
                    .body(content.content());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PatchMapping("/templates/{template}/{*path}")
    public FileSystemResponse saveFile(
            @PathVariable String template,
            @PathVariable String path,
            @Valid @RequestBody SaveTemplateFileRequest request
    ) {
        try {
            return provider.saveTemplateFile(template, path, request);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/templates/{template}/upload/{*path}")
    public FileSystemResponse uploadFile(
            @PathVariable String template,
            @PathVariable String path,
            @Valid @RequestBody UploadTemplateFileRequest request
    ) {
        try {
            return provider.uploadTemplateFile(template, path, request);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/templates/{template}/{*path}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePath(
            @PathVariable String template,
            @PathVariable String path
    ) {
        try {
            provider.deleteTemplatePath(template, path);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/templates/{template}/folders/{*path}")
    @ResponseStatus(HttpStatus.CREATED)
    public FileSystemResponse createFolder(
            @PathVariable String template,
            @PathVariable String path,
        @Valid @RequestBody TemplatePathRequest request
    ) {
        try {
            return provider.createTemplateFolder(template, path, requestPath(request));
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/templates/{template}/copy/{*path}")
    public FileSystemResponse copyPath(
            @PathVariable String template,
            @PathVariable String path,
        @Valid @RequestBody TemplatePathRequest request
    ) {
        try {
            return provider.copyTemplatePath(template, path, requestPath(request));
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/templates/{template}/move/{*path}")
    public FileSystemResponse movePath(
            @PathVariable String template,
            @PathVariable String path,
        @Valid @RequestBody TemplatePathRequest request
    ) {
        try {
            return provider.moveTemplatePath(template, path, requestPath(request));
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/templates/{template}/rename/{*path}")
    public FileSystemResponse renamePath(
            @PathVariable String template,
            @PathVariable String path,
        @Valid @RequestBody TemplatePathRequest request
    ) {
        try {
            return provider.renameTemplatePath(template, path, requestPath(request));
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
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
        if (metricRange != MetricRange.DAYS
                && metricRange != MetricRange.HOURS
                && metricRange != MetricRange.MINUTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Player range must be days, hours or minutes");
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

    private String requestPath(TemplatePathRequest request) {
        return request.path() == null ? "" : request.path().trim();
    }
}
