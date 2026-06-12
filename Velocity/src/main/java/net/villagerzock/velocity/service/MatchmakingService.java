package net.villagerzock.velocity.service;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.villagerzock.velocity.config.MatchmakingConfiguration;
import net.villagerzock.velocity.entities.MatchmakingServer;
import net.villagerzock.velocity.entities.StartingMatchmakingServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MatchmakingService {
    private final List<MatchmakingServer> SERVERS = new CopyOnWriteArrayList<>();
    private final List<StartingMatchmakingServer> STARTING_QUEUE = new CopyOnWriteArrayList<>();
    private final MatchmakingConfiguration matchmakingConfiguration;
    private final ProxyServer proxyServer;
    private final CallbackService callbackService;

    public MatchmakingService(MatchmakingConfiguration matchmakingConfiguration, ProxyServer proxyServer, CallbackService callbackService) {
        this.matchmakingConfiguration = matchmakingConfiguration;
        this.proxyServer = proxyServer;
        this.callbackService = callbackService;
    }

    public CompletableFuture<RegisteredServer> queue(List<UUID> party, String type, int mmv){
        MatchmakingConfiguration.ServerConfig typeConfig = matchmakingConfiguration.getServerConfigs().get(type);

        if (typeConfig == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown server type: " + type)
            );
        }

        for (MatchmakingServer server : SERVERS){
            if (!server.getType().equals(type)) continue;
            if (!server.canAcceptPlayers()) continue;

            if (Math.abs(server.getAverageMatchmakingValue() - mmv) <= typeConfig.maxEloDiff()){
                RegisteredServer s = server.getServer();
                if (s.getPlayersConnected().size() + party.size() > typeConfig.maxPlayers()) continue;

                return CompletableFuture.completedFuture(s);
            }
        }
        for (StartingMatchmakingServer server : STARTING_QUEUE){
            if (!server.getType().equals(type)) continue;

            if (Math.abs(server.getMatchmakingValue() - mmv) <= typeConfig.maxEloDiff()){
                if (server.getQueuedPlayers().size() + party.size() > typeConfig.maxPlayers()) continue;

                server.getQueuedPlayers().addAll(party);

                return server.getCompletableFuture();
            }
        }
        CompletableFuture<RegisteredServer> completableFuture = new CompletableFuture<>();

        StartingMatchmakingServer matchmakingServer = new StartingMatchmakingServer(completableFuture,type,mmv,new ArrayList<>(party));

        callbackService.createCallback(completableFuture, data -> {
            String s = (String) data.get("server");

            RegisteredServer registeredServer = proxyServer.getServer(s)
                    .orElseThrow(() -> new IllegalStateException("Velocity does not know server: " + s));

            STARTING_QUEUE.remove(matchmakingServer);

            SERVERS.add(new MatchmakingServer(
                    registeredServer,
                    matchmakingServer.getMatchmakingValue(),
                    matchmakingServer.getType()
            ));

            return registeredServer;
        });

        STARTING_QUEUE.add(matchmakingServer);

        return completableFuture;
    }
}
