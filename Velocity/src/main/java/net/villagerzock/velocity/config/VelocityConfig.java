package net.villagerzock.velocity.config;

import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.CloudCoreVelocityPlugin;
import net.villagerzock.velocity.dto.LobbySettingsDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VelocityConfig {

    @Bean
    public LobbyConfiguration lobbyConfiguration(){
        return CloudCoreVelocityPlugin.lobbyConfiguration;
    }

    @Bean
    public MatchmakingConfiguration matchmakingConfiguration(){
        return CloudCoreVelocityPlugin.matchmakingConfiguration;
    }
    @Bean
    public ProxyServer proxyServer(){
        return CloudCoreVelocityPlugin.INSTANCE.proxy;
    }
}
