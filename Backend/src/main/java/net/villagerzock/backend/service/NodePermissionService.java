package net.villagerzock.backend.service;

import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.repository.NodeUserRepository;
import net.villagerzock.backend.security.NodePermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NodePermissionService {
    private final CloudCoreNodeRepository nodes;
    private final NodeUserRepository nodeUsers;

    public NodePermissionService(CloudCoreNodeRepository nodes, NodeUserRepository nodeUsers) {
        this.nodes = nodes;
        this.nodeUsers = nodeUsers;
    }

    public void require(Authentication authentication, long nodeId, NodePermission permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        require(authentication.getName(), nodeId, permission);
    }

    public void require(String username, long nodeId, NodePermission permission) {
        if (nodes.isOwnedByUser(nodeId, username)) {
            return;
        }
        int permissions = nodeUsers.findPermissionValues(nodeId, username).stream()
                .reduce(0, (left, right) -> left | right);
        if (permissions == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Node is not accessible");
        }
        if (!NodePermission.has(permissions, permission.getFlag())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing node permission");
        }
    }

    public boolean hasWildcard(String username, long nodeId) {
        return nodes.isOwnedByUser(nodeId, username);
    }
}
