package net.villagerzock.velocity.config;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

public class MatchmakingConfiguration {

    public record ServerConfig(int maxAmountOfServers, int maxPlayers, int maxEloDiff, boolean canRejoin){

    }

    @Getter
    @Setter
    public Map<String,ServerConfig> serverConfigs;
}
