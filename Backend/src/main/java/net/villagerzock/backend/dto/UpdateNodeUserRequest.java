package net.villagerzock.backend.dto;

import jakarta.validation.constraints.Positive;

public record UpdateNodeUserRequest(
        @Positive Long roleId
) {
}
