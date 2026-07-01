package net.villagerzock.corehandshake.dto;

import java.time.Instant;
import java.util.UUID;

public record BannedPlayer(
        UUID uuid,
        String name,
        String reason,
        Instant bannedAt,
        Instant expiresAt
) {
}
