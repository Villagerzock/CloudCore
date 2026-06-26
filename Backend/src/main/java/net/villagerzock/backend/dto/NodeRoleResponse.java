package net.villagerzock.backend.dto;

import java.util.Map;

public record NodeRoleResponse(
        long id,
        String name,
        int permissions,
        Map<String, Boolean> permissionOptions,
        Map<String, Integer> permissionValues,
        Long previousRoleId
) {
}
