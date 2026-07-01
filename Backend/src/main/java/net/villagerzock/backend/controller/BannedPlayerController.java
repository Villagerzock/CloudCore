package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.BannedPlayerDto;
import net.villagerzock.backend.dto.CreateBannedPlayerRequest;
import net.villagerzock.backend.dto.UpdateBannedPlayerRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.ServerService;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bans")
public class BannedPlayerController {
    private final ServerService serverService;
    private final NodePermissionService permissions;

    public BannedPlayerController(ServerService serverService, NodePermissionService permissions) {
        this.serverService = serverService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<BannedPlayerDto> getBans(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.BANNED_PLAYERS_PAGE);
        return serverService.getBannedPlayers(nodeId);
    }

    @PostMapping
    public BannedPlayerDto createBan(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody CreateBannedPlayerRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.BANNED_PLAYERS_ADD);
        return serverService.createBan(nodeId, request);
    }

    @PatchMapping("/{uuid}")
    public BannedPlayerDto updateBan(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String uuid,
            @Valid @RequestBody UpdateBannedPlayerRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.BANNED_PLAYERS_EDIT);
        return serverService.updateBan(nodeId, uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBan(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String uuid
    ) {
        permissions.require(authentication, nodeId, NodePermission.BANNED_PLAYERS_EDIT);
        serverService.deleteBan(nodeId, uuid);
    }
}
