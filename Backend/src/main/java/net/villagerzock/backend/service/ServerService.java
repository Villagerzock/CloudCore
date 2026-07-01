package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.FileSystemResponse;
import net.villagerzock.backend.dto.AddMaintenancePlayerRequest;
import net.villagerzock.backend.dto.BannedPlayerDto;
import net.villagerzock.backend.dto.CreateBannedPlayerRequest;
import net.villagerzock.backend.dto.CreateTemplateRequest;
import net.villagerzock.backend.dto.SaveTemplateFileRequest;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.dto.TemplatePathRequest;
import net.villagerzock.backend.dto.LaunchServerRequest;
import net.villagerzock.backend.dto.LaunchServerResponse;
import net.villagerzock.backend.dto.MatchmakingConfigurationDto;
import net.villagerzock.backend.dto.MaintenanceStatusDto;
import net.villagerzock.backend.dto.UpdateBannedPlayerRequest;
import net.villagerzock.backend.dto.UploadTemplateFileRequest;
import net.villagerzock.backend.entity.Username;
import net.villagerzock.backend.repository.UsernameRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Base64;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServerService {
    private final NodeHandshakeClient handshakeClient;
    private final UsernameRepository usernameRepository;

    public ServerService(NodeHandshakeClient handshakeClient, UsernameRepository usernameRepository) {
        this.handshakeClient = handshakeClient;
        this.usernameRepository = usernameRepository;
    }

    public List<ServerDto> getRunningServers(long nodeId) {
        return handshakeClient.getServers(nodeId);
    }

    public LaunchServerResponse launchServer(long nodeId, LaunchServerRequest request) {
        return handshakeClient.launchServer(nodeId, request);
    }

    public ServerDto getServerByName(long nodeId, String serverName) {
        return handshakeClient.getServer(nodeId, serverName);
    }

    public void stopServer(long nodeId, String serverName) {
        handshakeClient.stopServer(nodeId, serverName);
    }

    public LaunchServerResponse restartServer(long nodeId, String serverName) {
        return handshakeClient.restartServer(nodeId, serverName);
    }

    public void startProxy(long nodeId) {
        handshakeClient.startProxy(nodeId);
    }

    public void stopProxy(long nodeId) {
        handshakeClient.stopProxy(nodeId);
    }

    public void restartProxy(long nodeId) {
        handshakeClient.restartProxy(nodeId);
    }

    public List<ServerTemplateDto> getTemplates(long nodeId) {
        return handshakeClient.getTemplates(nodeId);
    }

    public ServerTemplateDto createTemplate(long nodeId, CreateTemplateRequest request) {
        return handshakeClient.createTemplate(nodeId, request);
    }

    public List<MatchmakingConfigurationDto> getMatchmakingConfigurations(long nodeId) {
        return handshakeClient.getMatchmakingConfigurations(nodeId);
    }

    public MatchmakingConfigurationDto saveMatchmakingConfiguration(
            long nodeId,
            MatchmakingConfigurationDto configuration
    ) {
        return handshakeClient.saveMatchmakingConfiguration(nodeId, configuration);
    }

    public void deleteMatchmakingConfiguration(long nodeId, String name) {
        handshakeClient.deleteMatchmakingConfiguration(nodeId, name);
    }

    public MaintenanceStatusDto getMaintenanceStatus(long nodeId) {
        return withMaintenancePlayerNames(handshakeClient.getMaintenanceStatus(nodeId));
    }

    private MaintenanceStatusDto withMaintenancePlayerNames(MaintenanceStatusDto dto) {
        for (int i = 0; i < dto.players().size(); i++) {
            if (dto.players().get(i).name() == null){
                MaintenanceStatusDto.PlayerEntry p = dto.players().get(i);
                dto.players().set(i, new MaintenanceStatusDto.PlayerEntry(p.uuid(), getUsernameFromUuid(p.uuid()).orElse(null)));
            }
        }

        return dto;
    }

    public MaintenanceStatusDto setMaintenanceActive(long nodeId, boolean active) {
        return withMaintenancePlayerNames(handshakeClient.setMaintenanceActive(nodeId, active));
    }

    public MaintenanceStatusDto addMaintenancePlayer(long nodeId, AddMaintenancePlayerRequest request) {
        return withMaintenancePlayerNames(handshakeClient.addMaintenancePlayer(nodeId, request));
    }

    public MaintenanceStatusDto removeMaintenancePlayer(long nodeId, String uuid) {
        return withMaintenancePlayerNames(handshakeClient.removeMaintenancePlayer(nodeId, uuid));
    }

    public List<BannedPlayerDto> getBannedPlayers(long nodeId) {
        return withBannedPlayerNames(handshakeClient.getBannedPlayers(nodeId));
    }

    public BannedPlayerDto createBan(long nodeId, CreateBannedPlayerRequest request) {
        return withBannedPlayerName(handshakeClient.createBan(nodeId, request));
    }

    public BannedPlayerDto updateBan(long nodeId, String uuid, UpdateBannedPlayerRequest request) {
        return withBannedPlayerName(handshakeClient.updateBan(nodeId, uuid, request));
    }

    public void deleteBan(long nodeId, String uuid) {
        handshakeClient.deleteBan(nodeId, uuid);
    }

    private List<BannedPlayerDto> withBannedPlayerNames(List<BannedPlayerDto> bans) {
        return bans.stream().map(this::withBannedPlayerName).toList();
    }

    private BannedPlayerDto withBannedPlayerName(BannedPlayerDto ban) {
        if (ban.name() != null) {
            return ban;
        }
        return new BannedPlayerDto(
                ban.uuid(),
                getUsernameFromUuid(ban.uuid()).orElse(null),
                ban.reason(),
                ban.bannedAt(),
                ban.expiresAt());
    }

    public FileSystemResponse getTemplateFileSystemPath(long nodeId, String template, String path) {
        return handshakeClient.getTemplateFileSystemPath(nodeId, template, path)
                .toResponse(contentUrl(nodeId, template, path), downloadUrl(nodeId, template, path));
    }

    public ResponseEntity<byte[]> getTemplateFileContent(long nodeId, String template, String path) {
        return handshakeClient.getTemplateFileContent(nodeId, template, path);
    }

    public ResponseEntity<byte[]> downloadTemplateFile(long nodeId, String template, String path) {
        return handshakeClient.downloadTemplateFile(nodeId, template, path);
    }

    public FileSystemResponse saveTemplateFile(
            long nodeId,
            String template,
            String path,
            SaveTemplateFileRequest request
    ) {
        return handshakeClient.saveTemplateFile(nodeId, template, path, request)
                .toResponse(contentUrl(nodeId, template, path), downloadUrl(nodeId, template, path));
    }

    public FileSystemResponse uploadTemplateFile(
            long nodeId,
            String template,
            String folderPath,
            MultipartFile file,
            String relativePath
    ) {
        try {
            String uploadPath = relativePath == null || relativePath.isBlank()
                    ? file.getOriginalFilename()
                    : relativePath;
            if (uploadPath == null || uploadPath.isBlank()) {
                throw new IllegalArgumentException("File name is required");
            }
            UploadTemplateFileRequest request = new UploadTemplateFileRequest(
                    uploadPath,
                    Base64.getEncoder().encodeToString(file.getBytes()));
            return handshakeClient.uploadTemplateFile(nodeId, template, folderPath, request)
                    .toResponse(contentUrl(nodeId, template, folderPath), downloadUrl(nodeId, template, folderPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read uploaded file", exception);
        }
    }

    public void deleteTemplatePath(long nodeId, String template, String path) {
        handshakeClient.deleteTemplatePath(nodeId, template, path);
    }

    public FileSystemResponse createTemplateFolder(
            long nodeId,
            String template,
            String folderPath,
            TemplatePathRequest request
    ) {
        return handshakeClient.createTemplateFolder(nodeId, template, folderPath, request)
                .toResponse(contentUrl(nodeId, template, folderPath), downloadUrl(nodeId, template, folderPath));
    }

    public FileSystemResponse copyTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        return handshakeClient.copyTemplatePath(nodeId, template, sourcePath, request)
                .toResponse(contentUrl(nodeId, template, sourcePath), downloadUrl(nodeId, template, sourcePath));
    }

    public FileSystemResponse moveTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        return handshakeClient.moveTemplatePath(nodeId, template, sourcePath, request)
                .toResponse(contentUrl(nodeId, template, sourcePath), downloadUrl(nodeId, template, sourcePath));
    }

    public FileSystemResponse renameTemplatePath(
            long nodeId,
            String template,
            String sourcePath,
            TemplatePathRequest request
    ) {
        return handshakeClient.renameTemplatePath(nodeId, template, sourcePath, request)
                .toResponse(contentUrl(nodeId, template, sourcePath), downloadUrl(nodeId, template, sourcePath));
    }

    private String contentUrl(long nodeId, String template, String path) {
        return templateUrl(nodeId, template, path, "content");
    }

    private String downloadUrl(long nodeId, String template, String path) {
        return templateUrl(nodeId, template, path, "download");
    }

    private String templateUrl(long nodeId, String template, String path, String action) {
        return UriComponentsBuilder.fromPath("/api/templates")
                .pathSegment(template)
                .path("/" + action)
                .path(path.startsWith("/") ? path : "/" + path)
                .queryParam("node", nodeId)
                .build()
                .toUriString();
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<String> getUsernameFromUuid(UUID uuid) {
        Optional<Username> usernameOpt = usernameRepository.findById(uuid);
        if (usernameOpt.isPresent()) {
            Username username = usernameOpt.get();
            if (Instant.now().isBefore(username.getCreated().plus(2, ChronoUnit.DAYS))) {
                return Optional.of(username.getUsername());
            }
        }

        String url = "https://sessionserver.mojang.com/session/minecraft/profile/"
                + uuid.toString().replace("-", "");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            String username = json.get("name").asString();
            Username cache = new Username();
            cache.setUsername(username);
            cache.setCreated(Instant.now());
            cache.setUuid(uuid);
            usernameRepository.save(cache);
            return Optional.of(username);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
