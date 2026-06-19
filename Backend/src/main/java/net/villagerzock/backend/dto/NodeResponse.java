package net.villagerzock.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record NodeResponse(
        long id,
        UUID serverId,
        String name,
        String ipAddress,
        Instant linkedAt
) {
}
