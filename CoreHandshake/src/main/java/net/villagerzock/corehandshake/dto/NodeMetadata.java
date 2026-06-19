package net.villagerzock.corehandshake.dto;

import java.time.Instant;

public record NodeMetadata(
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
