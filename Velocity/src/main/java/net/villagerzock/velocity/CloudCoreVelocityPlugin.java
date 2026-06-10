package net.villagerzock.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;


@Plugin(
        id = "cloudcore",
        name = "CloudCore",
        version = "1.0.0",
        authors = {"Villagerzock"}
)
public class CloudCoreVelocityPlugin {

    public static CloudCoreVelocityPlugin INSTANCE;
    public final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public CloudCoreVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        INSTANCE = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("CloudCore started on Velocity");
        Thread spring = new Thread(()->{
            SpringApplication.run(VelocitySpringBootApplication.class);
        }, "Spring-Boot");
        spring.start();

    }

}
