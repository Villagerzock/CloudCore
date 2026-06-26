package net.villagerzock.velocity;

import com.google.inject.Inject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.villagerzock.velocity.config.CloudCoreConfiguration;
import net.villagerzock.velocity.service.MaintenanceService;
import net.villagerzock.velocity.service.MetricCollectionService;
import net.villagerzock.velocity.service.ServerMangementService;
import org.slf4j.Logger;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "cloudcore",
        name = "CloudCore",
        version = "1.0.0",
        authors = {"Villagerzock"}
)
public class CloudCoreVelocityPlugin {

    public static CloudCoreVelocityPlugin INSTANCE;

    private final Map<Class<?>, Object> beanCache = new ConcurrentHashMap<>();

    public final ProxyServer proxy;
    private final Logger logger;

    private volatile ConfigurableApplicationContext application;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Inject
    public CloudCoreVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        INSTANCE = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("CloudCore started on Velocity");

        Thread spring = new Thread(() -> {
            try {
                String dbHost = getEnvOrDefault("CLOUDCORE_DB_HOST", "mariadb");
                String dbPort = getEnvOrDefault("CLOUDCORE_DB_PORT", "3306");
                String dbName = getEnvOrDefault("CLOUDCORE_DB_NAME", "cloudcore_backend");
                String dbUser = getEnvOrDefault("CLOUDCORE_DB_USER", "cloudcore_backend");
                String dbPassword = getEnvOrDefault("CLOUDCORE_DB_PASSWORD", "");

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

                logger.info("CloudCore Spring application started");
            } catch (Exception exception) {
                logger.error("Failed to start CloudCore Spring application", exception);
            }
        }, "Spring-Boot");

        spring.setContextClassLoader(CloudCoreVelocityPlugin.class.getClassLoader());
        spring.start();

        BrigadierCommand cloudcoreCommand = new BrigadierCommand(
                LiteralArgumentBuilder.<CommandSource>literal("cloudcore")
                        .then(
                                LiteralArgumentBuilder.<CommandSource>literal("maintenance")
                                        .then(
                                                LiteralArgumentBuilder.<CommandSource>literal("on").executes(context -> {
                                                    getCachedBeanByType(MaintenanceService.class).turnOn();
                                                    return 0;
                                                })
                                        )
                                        .then(
                                                LiteralArgumentBuilder.<CommandSource>literal("off").executes(context -> {
                                                    getCachedBeanByType(MaintenanceService.class).turnOff();
                                                    return 0;
                                                })
                                        )
                                        .then(
                                                LiteralArgumentBuilder.<CommandSource>literal("add")
                                                        .then(
                                                                playerNameArgument().executes(context -> {
                                                                    Player player = findOnlinePlayerOrNotify(
                                                                            context.getSource(),
                                                                            StringArgumentType.getString(context, "player"));
                                                                    if (player == null) {
                                                                        return 0;
                                                                    }

                                                                    getCachedBeanByType(MaintenanceService.class).addPlayer(player);

                                                                    return 0;
                                                                })
                                                        )
                                        )
                                        .then(
                                                LiteralArgumentBuilder.<CommandSource>literal("remove")
                                                        .then(
                                                                playerNameArgument().executes(context -> {
                                                                    Player player = findOnlinePlayerOrNotify(
                                                                            context.getSource(),
                                                                            StringArgumentType.getString(context, "player"));
                                                                    if (player == null) {
                                                                        return 0;
                                                                    }

                                                                    getCachedBeanByType(MaintenanceService.class).removePlayer(player);

                                                                    return 0;
                                                                })
                                                        )
                                        )
                        )
        );

        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("cloudcore").build(), cloudcoreCommand);
    }

    private RequiredArgumentBuilder<CommandSource, String> playerNameArgument() {
        return RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (Player player : proxy.getAllPlayers()) {
                        builder.suggest(player.getUsername());
                    }
                    return builder.buildFuture();
                });
    }

    private Player findOnlinePlayerOrNotify(CommandSource source, String username) {
        return proxy.getPlayer(username).orElseGet(() -> {
            source.sendMessage(Component.text("Player is not online: " + username, NamedTextColor.RED));
            return null;
        });
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (isSpringStarting()) {
            event.getPlayer().disconnect(Component.text("CloudCore is still starting...", NamedTextColor.RED));
            return;
        }

        ServerMangementService serverMangementService = application.getBean(ServerMangementService.class);
        MaintenanceService maintenanceService = application.getBean(MaintenanceService.class);
        CloudCoreConfiguration configuration = application.getBean(CloudCoreConfiguration.class);

        if (maintenanceService.isActive() && !maintenanceService.hasPlayer(event.getPlayer())) {
            event.getPlayer().disconnect(Component.text("This Server is currently in Maintenance", NamedTextColor.RED));
            return;
        }

        RegisteredServer target = serverMangementService.findAnyServerOfType(configuration.getLobbyServer());

        if (target == null) {
            event.getPlayer().disconnect(Component.text(
                    "There are no available lobby servers right now! Please try again later.",
                    NamedTextColor.RED
            ));
            return;
        }
        event.setInitialServer(target);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        recordPlayerPeak();
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        recordPlayerPeak();
    }

    private void recordPlayerPeak() {
        if (!isSpringStarting()) {
            getCachedBeanByType(MetricCollectionService.class).recordPlayerCounts();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getCachedBeanByType(Class<T> type) {
        if (isSpringStarting()) {
            return null;
        }

        Object cachedBean = beanCache.get(type);

        if (cachedBean != null) {
            return (T) cachedBean;
        }

        T bean = application.getBean(type);
        beanCache.put(type, bean);

        return bean;
    }

    @Subscribe
    public void onServerPing(ProxyPingEvent event) {
        if (isSpringStarting()) {
            return;
        }

        MaintenanceService maintenanceService = application.getBean(MaintenanceService.class);

        if (!maintenanceService.isActive()) {
            return;
        }

        CloudCoreConfiguration configuration = application.getBean(CloudCoreConfiguration.class);

        Component motd = Component.text("!!!MAINTENANCE!!! ", NamedTextColor.RED)
                .append(miniMessage.deserialize(configuration.getMaintenanceMotd()));

        event.setPing(event.getPing()
                .asBuilder()
                .description(motd)
                .build());
    }

    private boolean isSpringStarting() {
        return application == null || !application.isRunning();
    }

    private String getEnvOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
