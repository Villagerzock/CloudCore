package net.villagerzock.backend.dto;

public record NodeUserResponse(
        long id,
        String username,
        String email,
        long roleId,
        String role,
        boolean hasAsterix
) {
}
