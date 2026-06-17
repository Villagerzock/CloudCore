package net.villagerzock.cloudcore.core.server.dto;

import java.util.Map;

public record ConfigDto(String lobbyServer, Map<String, MatchmakingServerConfigDto> matchmakingServerConfigs, String maintenanceMotd) {
    public record MatchmakingServerConfigDto(int maxAmountOfServers, int maxPlayers, int maxEloDiff, boolean canRejoin) {
    }
}
