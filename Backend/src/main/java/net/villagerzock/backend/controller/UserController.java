package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.CreateNodeUserRequest;
import net.villagerzock.backend.dto.NodeUserResponse;
import net.villagerzock.backend.dto.UpdateNodeUserRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.NodeUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final NodeUserService nodeUserService;
    private final NodePermissionService permissions;

    public UserController(NodeUserService nodeUserService, NodePermissionService permissions) {
        this.nodeUserService = nodeUserService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<NodeUserResponse> getUsers(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_PAGE);
        return nodeUserService.getUsers(nodeId);
    }

    @GetMapping("/{userId}")
    public NodeUserResponse getUser(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable long userId
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_PAGE);
        return nodeUserService.getUser(nodeId, userId);
    }

    @PostMapping
    public NodeUserResponse createUser(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody CreateNodeUserRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_ADD);
        return nodeUserService.createUser(nodeId, request.email(), request.roleId());
    }

    @PatchMapping("/{userId}")
    public NodeUserResponse updateUser(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable long userId,
            @Valid @RequestBody UpdateNodeUserRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_ADD);
        return nodeUserService.updateUser(nodeId, userId, request.roleId());
    }
}
