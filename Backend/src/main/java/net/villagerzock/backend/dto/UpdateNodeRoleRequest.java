package net.villagerzock.backend.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateNodeRoleRequest(
        @Size(max = 50) String name,
        Map<String, Boolean> permissions
) {
}
