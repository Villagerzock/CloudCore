package net.villagerzock.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.villagerzock.velocity.config.LobbyConfiguration;
import net.villagerzock.velocity.config.MatchmakingConfiguration;
import net.villagerzock.velocity.service.ServerMangementService;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.TextStyle;


@Plugin(
        id = "cloudcore",
        name = "CloudCore",
        version = "1.0.0",
        authors = {"Villagerzock"}
)
public class CloudCoreVelocityPlugin {

    public static CloudCoreVelocityPlugin INSTANCE;
    public static LobbyConfiguration lobbyConfiguration = new LobbyConfiguration();
    public static MatchmakingConfiguration matchmakingConfiguration = new MatchmakingConfiguration();
    public final ProxyServer proxy;
    private final Logger logger;
    private ConfigurableApplicationContext application;

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
            String dbHost = System.getenv("CLOUDCORE_DB_HOST");
            String dbPort = System.getenv("CLOUDCORE_DB_PORT");
            String dbName = System.getenv("CLOUDCORE_DB_NAME");
            String dbUser = System.getenv("CLOUDCORE_DB_USER");
            String dbPassword = System.getenv("CLOUDCORE_DB_PASSWORD");

            application = new SpringApplicationBuilder(VelocitySpringBootApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .properties(
                            "server.port=8080",
                            "server.address=0.0.0.0",

                            "spring.datasource.url=jdbc:mariadb://" + dbHost + ":" + dbPort + "/" + dbName,
                            "spring.datasource.username=" + dbUser,
                            "spring.datasource.password=" + dbPassword,
                            "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
                            "spring.jpa.hibernate.ddl-auto=update"
                    )
                    .run();

        }, "Spring-Boot");
        ClassLoader pluginClassLoader = CloudCoreVelocityPlugin.class.getClassLoader();

        spring.setContextClassLoader(pluginClassLoader);
        spring.start();

    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event){
        ServerMangementService service = application.getBean(ServerMangementService.class);
        RegisteredServer target = service.findAnyServerOfType(lobbyConfiguration.getServer());
        if (target == null){
            event.getPlayer().disconnect(Component.text("There are no Available Lobby Servers right now! Please try again Later").style(Style.style(NamedTextColor.RED)));
            return;
        }

        event.setInitialServer(target);
    }

}
