package net.villagerzock.velocity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class CloudCoreConfiguration {

    public record ServerConfig(int maxAmountOfServers, int maxPlayers, int maxEloDiff, boolean canRejoin) {
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
}
