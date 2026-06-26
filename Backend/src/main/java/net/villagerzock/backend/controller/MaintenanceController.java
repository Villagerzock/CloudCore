package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.AddMaintenancePlayerRequest;
import net.villagerzock.backend.dto.MaintenanceStatusDto;
import net.villagerzock.backend.dto.UpdateMaintenanceRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final ServerService serverService;
    private final NodePermissionService permissions;

    public MaintenanceController(ServerService serverService, NodePermissionService permissions) {
        this.serverService = serverService;
        this.permissions = permissions;
    }

    @GetMapping
    public MaintenanceStatusDto getStatus(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.MAINTENANCE_PAGE);
        return serverService.getMaintenanceStatus(nodeId);
    }

    @PatchMapping
    public MaintenanceStatusDto setActive(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @RequestBody UpdateMaintenanceRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.MAINTENANCE_PAGE);
        return serverService.setMaintenanceActive(nodeId, request.active());
    }

    @PostMapping("/players")
    public MaintenanceStatusDto addPlayer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody AddMaintenancePlayerRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.MAINTENANCE_PAGE);
        return serverService.addMaintenancePlayer(nodeId, request);
    }

    @DeleteMapping("/players/{uuid}")
    public MaintenanceStatusDto removePlayer(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String uuid
    ) {
        permissions.require(authentication, nodeId, NodePermission.MAINTENANCE_PAGE);
        return serverService.removeMaintenancePlayer(nodeId, uuid);
    }
}
