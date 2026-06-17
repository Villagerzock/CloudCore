package net.villagerzock.velocity.config;

import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.CloudCoreVelocityPlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VelocityConfig {

    @Bean
    public ProxyServer proxyServer(){
        return CloudCoreVelocityPlugin.INSTANCE.proxy;
    }
}
