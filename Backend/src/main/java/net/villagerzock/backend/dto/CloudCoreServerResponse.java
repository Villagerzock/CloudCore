package net.villagerzock.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record CloudCoreServerResponse(
        UUID serverId,
        String name,
        String ipAddress,
        boolean linked,
        Instant createdAt,
        Instant linkedAt
) {
}
