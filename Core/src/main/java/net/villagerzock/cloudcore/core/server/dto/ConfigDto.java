package net.villagerzock.cloudcore.core.server.dto;

import java.util.Map;

public record ConfigDto(String lobbyServer, Map<String, MatchmakingServerConfigDto> matchmakingServerConfigs, String maintenanceMotd) {
    public record MatchmakingServerConfigDto(
            String template,
            int maxAmountOfServers,
            int maxPlayersPerServer,
            int playersPerTeam,
            boolean canRejoin,
            boolean splitSameQueue,
            boolean singleQueueServerOnSplit,
            int maxMmvDiff
    ) {
    }
}
