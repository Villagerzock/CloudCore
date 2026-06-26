package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.FileSystemResponse;
import net.villagerzock.backend.dto.AddMaintenancePlayerRequest;
import net.villagerzock.backend.dto.CreateTemplateRequest;
import net.villagerzock.backend.dto.SaveTemplateFileRequest;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.dto.TemplatePathRequest;
import net.villagerzock.backend.dto.LaunchServerRequest;
import net.villagerzock.backend.dto.LaunchServerResponse;
import net.villagerzock.backend.dto.MatchmakingConfigurationDto;
import net.villagerzock.backend.dto.MaintenanceStatusDto;
import net.villagerzock.backend.dto.UploadTemplateFileRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class ServerService {
    private final NodeHandshakeClient handshakeClient;

    public ServerService(NodeHandshakeClient handshakeClient) {
        this.handshakeClient = handshakeClient;
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
        return handshakeClient.getMaintenanceStatus(nodeId);
    }

    public MaintenanceStatusDto setMaintenanceActive(long nodeId, boolean active) {
        return handshakeClient.setMaintenanceActive(nodeId, active);
    }

    public MaintenanceStatusDto addMaintenancePlayer(long nodeId, AddMaintenancePlayerRequest request) {
        return handshakeClient.addMaintenancePlayer(nodeId, request);
    }

    public MaintenanceStatusDto removeMaintenancePlayer(long nodeId, String uuid) {
        return handshakeClient.removeMaintenancePlayer(nodeId, uuid);
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
}
