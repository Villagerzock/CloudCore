package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.CommandRequest;
import net.villagerzock.backend.dto.AddMaintenancePlayerRequest;
import net.villagerzock.backend.dto.BannedPlayerDto;
import net.villagerzock.backend.dto.CreateBannedPlayerRequest;
import net.villagerzock.backend.dto.CreateTemplateRequest;
import net.villagerzock.backend.dto.FileSystemDelegatorResponse;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.LaunchServerRequest;
import net.villagerzock.backend.dto.LaunchServerResponse;
import net.villagerzock.backend.dto.MatchmakingConfigurationDto;
import net.villagerzock.backend.dto.MaintenanceStatusDto;
import net.villagerzock.backend.dto.NodeMetadataDto;
import net.villagerzock.backend.dto.SaveTemplateFileRequest;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.dto.TemplatePathRequest;
import net.villagerzock.backend.dto.UpdateBannedPlayerRequest;
import net.villagerzock.backend.dto.UploadTemplateFileRequest;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Service
public class NodeHandshakeClient {
    private static final ParameterizedTypeReference<List<ServerDto>> SERVER_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ServerTemplateDto>> TEMPLATE_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> STRING_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ChartPointDto>> CHART_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<NetworkPointDto>> NETWORK_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<MatchmakingConfigurationDto>> MATCHMAKING_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<BannedPlayerDto>> BANNED_PLAYER_LIST =
            new ParameterizedTypeReference<>() {};

    private final CloudCoreNodeRepository nodes;
    private final RestClient.Builder restClientBuilder;
    private final int handshakePort;
    private final String loopbackHost;

    public NodeHandshakeClient(
            CloudCoreNodeRepository nodes,
            RestClient.Builder restClientBuilder,
            @Value("${cloudcore.handshake-port:8081}") int handshakePort,
            @Value("${cloudcore.loopback-host:127.0.0.1}") String loopbackHost
    ) {
        this.nodes = nodes;
        this.restClientBuilder = restClientBuilder;
        this.handshakePort = handshakePort;
        this.loopbackHost = loopbackHost;
    }

    public List<ServerDto> getServers(long nodeId) {
        return get(nodeId, "/servers", SERVER_LIST);
    }

    public LaunchServerResponse launchServer(long nodeId, LaunchServerRequest request) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/servers")
                    .body(request)
                    .retrieve()
                    .body(LaunchServerResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public ServerDto getServer(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}", serverName, ServerDto.class);
    }

    public void stopServer(long nodeId, String serverName) {
        post(nodeId, "/servers/" + encodePathSegment(serverName) + "/stop", null);
    }

    public LaunchServerResponse restartServer(long nodeId, String serverName) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/servers/{serverName}/restart", serverName)
                    .retrieve()
                    .body(LaunchServerResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public void startProxy(long nodeId) {
        post(nodeId, "/proxy/start", null);
    }

    public void stopProxy(long nodeId) {
        post(nodeId, "/proxy/stop", null);
    }

    public void restartProxy(long nodeId) {
        post(nodeId, "/proxy/restart", null);
    }

    public List<ServerTemplateDto> getTemplates(long nodeId) {
        return get(nodeId, "/templates", TEMPLATE_LIST);
    }

    public ServerTemplateDto createTemplate(long nodeId, CreateTemplateRequest request) {
        try {
            return client(nodeId, Duration.ofMinutes(5)).post()
                    .uri("/templates")
                    .body(request)
                    .retrieve()
                    .body(ServerTemplateDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public List<MatchmakingConfigurationDto> getMatchmakingConfigurations(long nodeId) {
        return get(nodeId, "/matchmaking", MATCHMAKING_LIST);
    }

    public MatchmakingConfigurationDto saveMatchmakingConfiguration(
            long nodeId,
            MatchmakingConfigurationDto configuration
    ) {
        try {
            return client(nodeId).post()
                    .uri("/matchmaking")
                    .body(configuration)
                    .retrieve()
                    .body(MatchmakingConfigurationDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public void deleteMatchmakingConfiguration(long nodeId, String name) {
        try {
            client(nodeId).method(HttpMethod.DELETE)
                    .uri("/matchmaking/{name}", name)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public MaintenanceStatusDto getMaintenanceStatus(long nodeId) {
        return get(nodeId, "/maintenance", MaintenanceStatusDto.class);
    }

    public MaintenanceStatusDto setMaintenanceActive(long nodeId, boolean active) {
        try {
            return client(nodeId).post()
                    .uri(active ? "/maintenance/on" : "/maintenance/off")
                    .retrieve()
                    .body(MaintenanceStatusDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public MaintenanceStatusDto addMaintenancePlayer(long nodeId, AddMaintenancePlayerRequest request) {
        try {
            return client(nodeId).post()
                    .uri("/maintenance/players")
                    .body(request)
                    .retrieve()
                    .body(MaintenanceStatusDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public MaintenanceStatusDto removeMaintenancePlayer(long nodeId, String uuid) {
        try {
            return client(nodeId).method(HttpMethod.DELETE)
                    .uri("/maintenance/players/{uuid}", uuid)
                    .retrieve()
                    .body(MaintenanceStatusDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public List<BannedPlayerDto> getBannedPlayers(long nodeId) {
        return get(nodeId, "/bans", BANNED_PLAYER_LIST);
    }

    public BannedPlayerDto createBan(long nodeId, CreateBannedPlayerRequest request) {
        try {
            return client(nodeId).post()
                    .uri("/bans")
                    .body(request)
                    .retrieve()
                    .body(BannedPlayerDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public BannedPlayerDto updateBan(long nodeId, String uuid, UpdateBannedPlayerRequest request) {
        try {
            return client(nodeId).method(HttpMethod.PATCH)
                    .uri("/bans/{uuid}", uuid)
                    .body(request)
                    .retrieve()
                    .body(BannedPlayerDto.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public void deleteBan(long nodeId, String uuid) {
        try {
            client(nodeId).method(HttpMethod.DELETE)
                    .uri("/bans/{uuid}", uuid)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse getTemplateFileSystemPath(long nodeId, String template, String path) {
        return get(
                nodeId,
                "/templates/" + encodePathSegment(template) + normalizeDelegatedPath(path),
                FileSystemDelegatorResponse.class);
    }

    public ResponseEntity<byte[]> getTemplateFileContent(long nodeId, String template, String path) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).get()
                    .uri("/templates/" + encodePathSegment(template) + "/content" + normalizeDelegatedPath(path))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public ResponseEntity<byte[]> downloadTemplateFile(long nodeId, String template, String path) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).get()
                    .uri("/templates/" + encodePathSegment(template) + "/download" + normalizeDelegatedPath(path))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse saveTemplateFile(
            long nodeId,
            String template,
            String path,
            SaveTemplateFileRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).method(HttpMethod.PATCH)
                    .uri("/templates/" + encodePathSegment(template) + normalizeDelegatedPath(path))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse uploadTemplateFile(
            long nodeId,
            String template,
            String folderPath,
            UploadTemplateFileRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/templates/" + encodePathSegment(template) + "/upload" + normalizeDelegatedPath(folderPath))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public void deleteTemplatePath(long nodeId, String template, String path) {
        try {
            client(nodeId, Duration.ofSeconds(40)).method(HttpMethod.DELETE)
                    .uri("/templates/" + encodePathSegment(template) + normalizeDelegatedPath(path))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse createTemplateFolder(
            long nodeId,
            String template,
            String folderPath,
            TemplatePathRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/templates/" + encodePathSegment(template) + "/folders" + normalizeDelegatedPath(folderPath))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse copyTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/templates/" + encodePathSegment(template) + "/copy" + normalizeDelegatedPath(sourcePath))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse moveTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/templates/" + encodePathSegment(template) + "/move" + normalizeDelegatedPath(sourcePath))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public FileSystemDelegatorResponse renameTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/templates/" + encodePathSegment(template) + "/rename" + normalizeDelegatedPath(sourcePath))
                    .body(request)
                    .retrieve()
                    .body(FileSystemDelegatorResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public List<String> getProxyLogs(long nodeId) {
        return get(nodeId, "/proxy/logs", STRING_LIST);
    }

    public List<String> getServerLogs(long nodeId, String server) {
        return get(nodeId, "/servers/{server}/logs", server, STRING_LIST);
    }

    public void executeProxyCommand(long nodeId, String command) {
        post(nodeId, "/proxy/commands", command);
    }

    public void executeServerCommand(long nodeId, String server, String command) {
        post(nodeId, "/servers/{server}/commands", server, command);
    }

    public NodeMetadataDto getMetadata(long nodeId) {
        return get(nodeId, "/metadata", NodeMetadataDto.class);
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId, String range) {
        return get(nodeId, "/proxy/metrics/player-count?range=" + range, CHART_LIST);
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId, String range) {
        return get(nodeId, "/proxy/metrics/network?range=" + range, NETWORK_LIST);
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}/metrics/player-count", serverName, CHART_LIST);
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}/metrics/network", serverName, NETWORK_LIST);
    }

    public static boolean isNodeUnavailable(Throwable exception) {
        return exception instanceof ResponseStatusException responseStatusException
                && responseStatusException.getStatusCode().isSameCodeAs(HttpStatus.BAD_GATEWAY);
    }

    private <T> T get(long nodeId, String path, Class<T> responseType) {
        try {
            return client(nodeId).get().uri(path).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T get(long nodeId, String path, ParameterizedTypeReference<T> responseType) {
        try {
            return client(nodeId).get().uri(path).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T get(
            long nodeId,
            String path,
            Object uriVariable,
            ParameterizedTypeReference<T> responseType
    ) {
        try {
            return client(nodeId).get().uri(path, uriVariable).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T get(long nodeId, String path, Object uriVariable, Class<T> responseType) {
        try {
            return client(nodeId).get().uri(path, uriVariable).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T post(long nodeId, String path, String command, ParameterizedTypeReference<T> responseType) {
        try {
            return client(nodeId).post()
                    .uri(path)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T post(
            long nodeId,
            String path,
            Object uriVariable,
            String command,
            ParameterizedTypeReference<T> responseType
    ) {
        try {
            return client(nodeId).post()
                    .uri(path, uriVariable)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private void post(long nodeId, String path, String command) {
        try {
            client(nodeId).post()
                    .uri(path)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private void post(long nodeId, String path, Object uriVariable, String command) {
        try {
            client(nodeId).post()
                    .uri(path, uriVariable)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private RestClient client(long nodeId) {
        return client(nodeId, Duration.ofSeconds(5));
    }

    private String normalizeDelegatedPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encodePathSegment(String segment) {
        return UriComponentsBuilder.newInstance()
                .pathSegment(segment)
                .build()
                .toUriString();
    }

    private RestClient client(long nodeId, Duration readTimeout) {
        String ipAddress = nodes.findIpAddressById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found"));
        String baseUrl = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(resolveHost(ipAddress))
                .port(handshakePort)
                .path("/core-handshake/v1")
                .build()
                .toUriString();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private String resolveHost(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).isLoopbackAddress() ? loopbackHost : ipAddress;
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Node has an invalid IP address", exception);
        }
    }

    private ResponseStatusException unavailable(long nodeId, RestClientException cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "CoreHandshake on node " + nodeId + " is unavailable",
                cause);
    }
}
