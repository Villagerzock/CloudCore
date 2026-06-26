package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.NodeUserResponse;
import net.villagerzock.backend.entity.NodeRole;
import net.villagerzock.backend.entity.NodeUser;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.repository.NodeRoleRepository;
import net.villagerzock.backend.repository.NodeUserRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NodeUserService {
    private final CloudCoreNodeRepository nodes;
    private final NodeUserRepository nodeUsers;
    private final NodeRoleRepository nodeRoles;
    private final UserAccountRepository users;

    public NodeUserService(
            CloudCoreNodeRepository nodes,
            NodeUserRepository nodeUsers,
            NodeRoleRepository nodeRoles,
            UserAccountRepository users
    ) {
        this.nodes = nodes;
        this.nodeUsers = nodeUsers;
        this.nodeRoles = nodeRoles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<NodeUserResponse> getUsers(long nodeId) {
        return nodeUsers.findByNodeId(nodeId).stream()
                .map(nodeUser -> toResponse(nodeUser, hasAsterix(nodeUser)))
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeUserResponse getCurrentUser(long nodeId, String username) {
        NodeUser nodeUser = nodeUsers.findByNodeIdAndUsername(nodeId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Node is not accessible"));
        return toResponse(nodeUser, hasAsterix(nodeUser));
    }

    @Transactional(readOnly = true)
    public NodeUserResponse getUser(long nodeId, long userId) {
        NodeUser nodeUser = findUser(nodeId, userId);
        return toResponse(nodeUser, hasAsterix(nodeUser));
    }

    @Transactional
    public NodeUserResponse createUser(long nodeId, String email, long roleId) {
        UserAccount user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        if (nodeUsers.existsByNodeIdAndUserId(nodeId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already added to this node");
        }
        NodeRole role = nodeRoles.findByIdAndNodeId(roleId, nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        NodeUser nodeUser = new NodeUser(nodes.getReferenceById(nodeId), user, role);
        return toResponse(nodeUsers.save(nodeUser), hasAsterix(nodeUser));
    }

    @Transactional
    public NodeUserResponse updateUser(long nodeId, long userId, Long roleId) {
        NodeUser nodeUser = findUser(nodeId, userId);
        if (roleId != null) {
            NodeRole role = nodeRoles.findByIdAndNodeId(roleId, nodeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
            nodeUser.setRole(role);
        }
        return toResponse(nodeUser, hasAsterix(nodeUser));
    }

    private NodeUser findUser(long nodeId, long userId) {
        return nodeUsers.findByIdAndNodeId(userId, nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private boolean hasAsterix(NodeUser nodeUser) {
        UserAccount owner = nodeUser.getNode().getOwner();
        return owner != null && owner.getId().equals(nodeUser.getUser().getId());
    }

    private NodeUserResponse toResponse(NodeUser nodeUser, boolean hasAsterix) {
        return new NodeUserResponse(
                nodeUser.getId(),
                nodeUser.getUser().getUsername(),
                nodeUser.getUser().getEmail(),
                nodeUser.getRole().getId(),
                nodeUser.getRole().getName(),
                hasAsterix
        );
    }
}
