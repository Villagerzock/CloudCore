package net.villagerzock.velocity.dto;

import net.villagerzock.velocity.config.CloudCoreConfiguration;

import java.util.Map;

public record ConfigDto(
        String lobbyServer,
        Map<String, CloudCoreConfiguration.ServerConfig> matchmakingServerConfigs,
        String maintenanceMotd
) {
}
