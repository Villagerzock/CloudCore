package net.villagerzock.corehandshake.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MatchmakingConfiguration(
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
}
