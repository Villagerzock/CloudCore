package net.villagerzock.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateNodeRoleRequest(
        @NotBlank @Size(max = 50) String name,
        @PositiveOrZero int permissions
) {
}
