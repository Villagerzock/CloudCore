package net.villagerzock.corehandshake.dto;

import java.util.List;
import java.util.UUID;

public record MaintenanceStatus(
        boolean active,
        List<PlayerEntry> players
) {
    public record PlayerEntry(UUID uuid, String name) {
    }
}
