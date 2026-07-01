package net.villagerzock.velocity.dto;

import java.time.Instant;
import java.util.UUID;

public record BannedPlayerDto(
        UUID uuid,
        String name,
        String reason,
        Instant bannedAt,
        Instant expiresAt
) {
}
