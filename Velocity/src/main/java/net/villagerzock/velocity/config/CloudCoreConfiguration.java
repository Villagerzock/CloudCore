package net.villagerzock.velocity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class CloudCoreConfiguration {

    public record ServerConfig(
            String template,
            int maxAmountOfServers,
            int maxPlayersPerServer,
            int playersPerTeam,
            boolean canRejoin,
            boolean splitSameQueue,
            boolean singleQueueServerOnSplit,
            int maxMmvDiff
    ) {
        public int maxPlayers() {
            return maxPlayersPerServer;
        }

        public int maxEloDiff() {
            return maxMmvDiff;
        }
    }

    @Getter
    @Setter
    private String lobbyServer;

    @Getter
    @Setter
    private Map<String, ServerConfig> matchmakingServerConfigs;

    @Getter
    @Setter
    private String maintenanceMotd;

    @Getter
    @Setter
    private String banMessage = """
            <red>You are banned from this network.</red>
            <gray>Player:</gray> <white>%name%</white>
            <gray>Reason:</gray> <white>%reason%</white>
            <gray>Release:</gray> <white>%release_date%</white>
            <gray>Time left:</gray> <white>%time_left%</white>
            """;
}
