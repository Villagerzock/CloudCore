package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.FileSystemResponse;
import net.villagerzock.backend.dto.CreateTemplateRequest;
import net.villagerzock.backend.dto.SaveTemplateFileRequest;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.dto.TemplatePathRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final ServerService serverService;
    private final NodePermissionService permissions;

    public TemplateController(ServerService serverService, NodePermissionService permissions) {
        this.serverService = serverService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<ServerTemplateDto> getTemplates(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_PAGE);
        return serverService.getTemplates(nodeId);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ServerTemplateDto createTemplate(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody CreateTemplateRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_CREATE);
        return serverService.createTemplate(nodeId, request);
    }

    @GetMapping("/{template}/{*path}")
    public ResponseEntity<FileSystemResponse> getFileSystemPath(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_READ_DIR);
        FileSystemResponse response = serverService.getTemplateFileSystemPath(nodeId, template, path);
        return ResponseEntity.status(response.isFile()
                        ? org.springframework.http.HttpStatus.PARTIAL_CONTENT
                        : org.springframework.http.HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/{template}/content/{*path}")
    public ResponseEntity<byte[]> getFileContent(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_DOWNLOAD);
        return serverService.getTemplateFileContent(nodeId, template, path);
    }

    @GetMapping("/{template}/download/{*path}")
    public ResponseEntity<byte[]> downloadFile(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_DOWNLOAD);
        return serverService.downloadTemplateFile(nodeId, template, path);
    }

    @PatchMapping("/{template}/{*path}")
    public FileSystemResponse saveFile(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @Valid @RequestBody SaveTemplateFileRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_WRITE);
        return serverService.saveTemplateFile(nodeId, template, path, request);
    }

    @PostMapping("/{template}/upload/{*path}")
    public FileSystemResponse uploadFile(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "relativePath", required = false) String relativePath
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_CREATE);
        return serverService.uploadTemplateFile(nodeId, template, path, file, relativePath);
    }

    @DeleteMapping("/{template}/{*path}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deletePath(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_WRITE);
        serverService.deleteTemplatePath(nodeId, template, path);
    }

    @PostMapping("/{template}/folders/{*path}")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public FileSystemResponse createFolder(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @Valid @RequestBody TemplatePathRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_CREATE);
        return serverService.createTemplateFolder(nodeId, template, path, request);
    }

    @PostMapping("/{template}/copy/{*path}")
    public FileSystemResponse copyPath(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @Valid @RequestBody TemplatePathRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_CREATE);
        return serverService.copyTemplatePath(nodeId, template, path, request);
    }

    @PostMapping("/{template}/move/{*path}")
    public FileSystemResponse movePath(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @Valid @RequestBody TemplatePathRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_WRITE);
        return serverService.moveTemplatePath(nodeId, template, path, request);
    }

    @PostMapping("/{template}/rename/{*path}")
    public FileSystemResponse renamePath(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String path,
            @PathVariable String template,
            @Valid @RequestBody TemplatePathRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.TEMPLATES_FILE_WRITE);
        return serverService.renameTemplatePath(nodeId, template, path, request);
    }
}
