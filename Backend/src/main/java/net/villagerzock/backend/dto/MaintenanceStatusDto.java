package net.villagerzock.backend.dto;

import java.util.List;
import java.util.UUID;

public record MaintenanceStatusDto(
        boolean active,
        List<PlayerEntry> players
) {
    public record PlayerEntry(UUID uuid, String name) {
    }
}
