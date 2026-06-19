package net.villagerzock.backend.dto;

import java.time.Instant;

public record NodeMetadataDto(
        String coreVersion,
        Instant startedAt,
        long uptimeSeconds,
        double cpuUsage,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int onlinePlayers,
        int runningServers
) {
}
