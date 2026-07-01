package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ResolvedBanRequest(
        @NotNull UUID uuid,
        @Size(max = 16) String name,
        @Size(max = 300) String reason,
        Instant expiresAt
) {
}
