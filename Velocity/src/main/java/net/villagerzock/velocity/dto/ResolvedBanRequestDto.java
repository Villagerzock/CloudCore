package net.villagerzock.velocity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ResolvedBanRequestDto(
        @NotNull UUID uuid,
        @Size(max = 16) String name,
        @Size(max = 300) String reason,
        Instant expiresAt
) {
}
