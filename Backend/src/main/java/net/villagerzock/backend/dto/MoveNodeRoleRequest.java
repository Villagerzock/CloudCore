package net.villagerzock.backend.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MoveNodeRoleRequest(
        @Positive long roleId,
        @PositiveOrZero long afterRoleId
) {
}
