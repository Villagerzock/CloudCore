package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.NodeRoleResponse;
import net.villagerzock.backend.entity.NodeRole;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.repository.NodeRoleRepository;
import net.villagerzock.backend.repository.NodeUserRepository;
import net.villagerzock.backend.security.NodePermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NodeRoleService {
    private final CloudCoreNodeRepository nodes;
    private final NodeRoleRepository nodeRoles;
    private final NodeUserRepository nodeUsers;
    private final NodePermissionService permissions;

    public NodeRoleService(
            CloudCoreNodeRepository nodes,
            NodeRoleRepository nodeRoles,
            NodeUserRepository nodeUsers,
            NodePermissionService permissions
    ) {
        this.nodes = nodes;
        this.nodeRoles = nodeRoles;
        this.nodeUsers = nodeUsers;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<NodeRoleResponse> getRoles(long nodeId) {
        return ordered(nodeRoles.findByNodeId(nodeId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeRoleResponse getRole(long nodeId, long roleId) {
        return toResponse(findRole(nodeId, roleId));
    }

    @Transactional
    public NodeRoleResponse createRole(long nodeId, String name, int rolePermissions) {
        List<NodeRole> orderedRoles = ordered(nodeRoles.findByNodeIdForUpdate(nodeId));
        NodeRole role = new NodeRole(nodes.getReferenceById(nodeId), name.trim(), rolePermissions);
        if (!orderedRoles.isEmpty()) {
            role.setPreviousRole(orderedRoles.getLast());
        }
        return toResponse(nodeRoles.save(role));
    }

    @Transactional
    public NodeRoleResponse updateRole(long nodeId, long roleId, String name, Map<String, Boolean> permissionChanges) {
        NodeRole role = findRole(nodeId, roleId);
        if (name != null) {
            String trimmedName = name.trim();
            if (trimmedName.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name is required");
            }
            role.setName(trimmedName);
        }
        if (permissionChanges != null) {
            role.setPermissions(applyPermissionChanges(role.getPermissions(), permissionChanges));
        }
        return toResponse(role);
    }

    @Transactional
    public List<NodeRoleResponse> moveRole(String username, long nodeId, long roleId, long afterRoleId) {
        if (roleId == afterRoleId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role cannot be moved behind itself");
        }

        List<NodeRole> orderedRoles = ordered(nodeRoles.findByNodeIdForUpdate(nodeId));
        Map<Long, NodeRole> rolesById = rolesById(orderedRoles);
        NodeRole role = rolesById.get(roleId);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (afterRoleId != 0 && !rolesById.containsKey(afterRoleId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target role not found");
        }

        if (!permissions.hasWildcard(username, nodeId)) {
            requireMoveAllowed(username, nodeId, roleId, afterRoleId, orderedRoles);
        }

        orderedRoles.remove(role);
        if (afterRoleId == 0) {
            orderedRoles.add(0, role);
        } else {
            int afterIndex = indexOf(orderedRoles, afterRoleId);
            orderedRoles.add(afterIndex + 1, role);
        }
        relink(orderedRoles);
        return orderedRoles.stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireMoveAllowed(
            String username,
            long nodeId,
            long roleId,
            long afterRoleId,
            List<NodeRole> orderedRoles
    ) {
        Map<Long, Integer> ranks = ranks(orderedRoles);
        Integer movingRank = ranks.get(roleId);
        int userRank = nodeUsers.findRoleIds(nodeId, username).stream()
                .map(ranks::get)
                .filter(rank -> rank != null)
                .min(Integer::compareTo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Node is not accessible"));

        if (movingRank == null || movingRank <= userRank) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot move your own role or a higher role");
        }
        if (afterRoleId == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot move roles above your own role");
        }
        Integer afterRank = ranks.get(afterRoleId);
        if (afterRank == null || afterRank < userRank) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot move roles above your own role");
        }
    }

    private List<NodeRole> ordered(List<NodeRole> roles) {
        List<NodeRole> sorted = new ArrayList<>(roles);
        sorted.sort(Comparator.comparing(NodeRole::getId));

        Map<Long, NodeRole> nextByPrevious = new HashMap<>();
        List<NodeRole> heads = new ArrayList<>();
        List<NodeRole> leftovers = new ArrayList<>();
        for (NodeRole role : sorted) {
            NodeRole previous = role.getPreviousRole();
            if (previous == null) {
                heads.add(role);
                continue;
            }
            if (nextByPrevious.putIfAbsent(previous.getId(), role) != null) {
                leftovers.add(role);
            }
        }

        List<NodeRole> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (NodeRole head : heads) {
            appendChain(head, nextByPrevious, ordered, seen);
        }
        for (NodeRole role : sorted) {
            if (!seen.contains(role.getId())) {
                appendChain(role, nextByPrevious, ordered, seen);
            }
        }
        for (NodeRole role : leftovers) {
            if (seen.add(role.getId())) {
                ordered.add(role);
            }
        }
        return ordered;
    }

    private void appendChain(
            NodeRole start,
            Map<Long, NodeRole> nextByPrevious,
            List<NodeRole> ordered,
            Set<Long> seen
    ) {
        NodeRole current = start;
        while (current != null && seen.add(current.getId())) {
            ordered.add(current);
            current = nextByPrevious.remove(current.getId());
        }
    }

    private Map<Long, NodeRole> rolesById(List<NodeRole> roles) {
        Map<Long, NodeRole> rolesById = new HashMap<>();
        for (NodeRole role : roles) {
            rolesById.put(role.getId(), role);
        }
        return rolesById;
    }

    private Map<Long, Integer> ranks(List<NodeRole> roles) {
        Map<Long, Integer> ranks = new HashMap<>();
        for (int index = 0; index < roles.size(); index++) {
            ranks.put(roles.get(index).getId(), index);
        }
        return ranks;
    }

    private int indexOf(List<NodeRole> roles, long roleId) {
        for (int index = 0; index < roles.size(); index++) {
            if (roles.get(index).getId() == roleId) {
                return index;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target role not found");
    }

    private void relink(List<NodeRole> orderedRoles) {
        NodeRole previous = null;
        for (NodeRole role : orderedRoles) {
            role.setPreviousRole(previous);
            previous = role;
        }
        nodeRoles.saveAll(orderedRoles);
    }

    private NodeRole findRole(long nodeId, long roleId) {
        return nodeRoles.findByIdAndNodeId(roleId, nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private int applyPermissionChanges(int permissions, Map<String, Boolean> changes) {
        int result = permissions;
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            int permission;
            try {
                permission = NodePermission.valueOfName(change.getKey());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
            }
            if (Boolean.TRUE.equals(change.getValue())) {
                result |= permission;
            } else {
                result &= ~permission;
            }
        }
        return result;
    }

    private NodeRoleResponse toResponse(NodeRole role) {
        NodeRole previous = role.getPreviousRole();
        return new NodeRoleResponse(
                role.getId(),
                role.getName(),
                role.getPermissions(),
                NodePermission.options(role.getPermissions()),
                NodePermission.valuesByName(),
                previous == null ? null : previous.getId());
    }
}
