package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.CreateNodeRoleRequest;
import net.villagerzock.backend.dto.MoveNodeRoleRequest;
import net.villagerzock.backend.dto.NodeRoleResponse;
import net.villagerzock.backend.dto.UpdateNodeRoleRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.NodePermissionService;
import net.villagerzock.backend.service.NodeRoleService;
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
@RequestMapping("/api/roles")
public class NodeRoleController {
    private final NodeRoleService nodeRoleService;
    private final NodePermissionService permissions;

    public NodeRoleController(NodeRoleService nodeRoleService, NodePermissionService permissions) {
        this.nodeRoleService = nodeRoleService;
        this.permissions = permissions;
    }

    @GetMapping
    public List<NodeRoleResponse> getRoles(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_PAGE);
        return nodeRoleService.getRoles(nodeId);
    }

    @GetMapping("/{roleId}")
    public NodeRoleResponse getRole(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable long roleId
    ) {
        permissions.require(authentication, nodeId, NodePermission.USERS_PAGE);
        return nodeRoleService.getRole(nodeId, roleId);
    }

    @PostMapping
    public NodeRoleResponse createRole(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody CreateNodeRoleRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.ROLES_ADD);
        return nodeRoleService.createRole(nodeId, request.name(), request.permissions());
    }

    @PatchMapping("/{roleId}")
    public NodeRoleResponse updateRole(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable long roleId,
            @Valid @RequestBody UpdateNodeRoleRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.ROLES_ADD);
        return nodeRoleService.updateRole(nodeId, roleId, request.name(), request.permissions());
    }

    @PatchMapping("/order")
    public List<NodeRoleResponse> moveRole(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @Valid @RequestBody MoveNodeRoleRequest request
    ) {
        permissions.require(authentication, nodeId, NodePermission.ROLES_MOVE);
        return nodeRoleService.moveRole(
                authentication.getName(),
                nodeId,
                request.roleId(),
                request.afterRoleId());
    }
}
