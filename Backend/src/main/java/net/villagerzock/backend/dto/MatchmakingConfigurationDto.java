package net.villagerzock.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingConfigurationDto(
        @NotBlank String name,
        @NotBlank String template,
        @Min(1) int maxAmountOfServers,
        @Min(1) int maxPlayersPerServer,
        @Min(1) int playersPerTeam,
        boolean canRejoin,
        boolean splitSameQueue,
        boolean singleQueueServerOnSplit,
        @Min(0) int maxMmvDiff
) {
    public MatchmakingConfigurationDto withName(String nextName) {
        return new MatchmakingConfigurationDto(
                nextName,
                template,
                maxAmountOfServers,
                maxPlayersPerServer,
                playersPerTeam,
                canRejoin,
                splitSameQueue,
                singleQueueServerOnSplit,
                maxMmvDiff);
    }
}
