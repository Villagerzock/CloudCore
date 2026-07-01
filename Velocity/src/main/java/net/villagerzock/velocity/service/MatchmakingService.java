package net.villagerzock.velocity.service;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.villagerzock.velocity.config.CloudCoreConfiguration;
import net.villagerzock.velocity.entities.MatchmakingServer;
import net.villagerzock.velocity.entities.StartingMatchmakingServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MatchmakingService {
    private final List<MatchmakingServer> SERVERS = new CopyOnWriteArrayList<>();
    private final List<StartingMatchmakingServer> STARTING_QUEUE = new CopyOnWriteArrayList<>();
    private final CloudCoreConfiguration cloudCoreConfiguration;
    private final ProxyServer proxyServer;
    private final CallbackService callbackService;
    private final String coreApiUrl;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public MatchmakingService(
            CloudCoreConfiguration cloudCoreConfiguration,
            ProxyServer proxyServer,
            CallbackService callbackService,
            @Value("${cloudcore.core-api-url:http://mariadb:8008}") String coreApiUrl
    ) {
        this.cloudCoreConfiguration = cloudCoreConfiguration;
        this.proxyServer = proxyServer;
        this.callbackService = callbackService;
        this.coreApiUrl = coreApiUrl;
    }

    public CompletableFuture<RegisteredServer> queue(List<UUID> party, String type, int mmv){
        if (party == null || party.isEmpty()) {
            throw new IllegalArgumentException("Party must contain at least one player");
        }
        if (cloudCoreConfiguration.getMatchmakingServerConfigs() == null) {
            throw new IllegalStateException("Matchmaking is not configured");
        }

        CloudCoreConfiguration.ServerConfig typeConfig = cloudCoreConfiguration.getMatchmakingServerConfigs().get(type);

        if (typeConfig == null) {
            throw new IllegalArgumentException("Unknown server type: " + type);
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

        UUID callbackId = callbackService.createCallback(completableFuture, data -> {
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
        startMatchmakingServer(typeConfig.template(), callbackId, matchmakingServer);

        return completableFuture;
    }

    public void handlePlayerDisconnect(UUID playerUuid, String previousServerName) {
        removeFromStartingQueues(playerUuid);

        if (previousServerName == null || previousServerName.isBlank()) {
            return;
        }

        for (MatchmakingServer matchmakingServer : SERVERS) {
            RegisteredServer server = matchmakingServer.getServer();
            if (!server.getServerInfo().getName().equals(previousServerName)) {
                continue;
            }
            long remainingPlayers = server.getPlayersConnected().stream()
                    .map(Player::getUniqueId)
                    .filter(uuid -> !uuid.equals(playerUuid))
                    .count();
            if (remainingPlayers == 0) {
                shutdownMatchmakingServer(matchmakingServer);
            }
            return;
        }
    }

    private void removeFromStartingQueues(UUID playerUuid) {
        for (StartingMatchmakingServer server : STARTING_QUEUE) {
            server.getQueuedPlayers().remove(playerUuid);
            if (!server.getQueuedPlayers().isEmpty()) {
                continue;
            }

            STARTING_QUEUE.remove(server);
            server.getCompletableFuture().thenAccept(registeredServer ->
                    shutdownRegisteredServer(registeredServer.getServerInfo().getName()));
        }
    }

    private void shutdownMatchmakingServer(MatchmakingServer matchmakingServer) {
        SERVERS.remove(matchmakingServer);
        shutdownRegisteredServer(matchmakingServer.getServer().getServerInfo().getName());
    }

    private void startMatchmakingServer(String template, UUID callbackId, StartingMatchmakingServer matchmakingServer) {
        String url = coreApiUrl
                + "/start?server=" + URLEncoder.encode(template, StandardCharsets.UTF_8)
                + "&callback=" + URLEncoder.encode(callbackId.toString(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, exception) -> {
                    if (exception != null) {
                        STARTING_QUEUE.remove(matchmakingServer);
                        matchmakingServer.getCompletableFuture().completeExceptionally(exception);
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        STARTING_QUEUE.remove(matchmakingServer);
                        matchmakingServer.getCompletableFuture().completeExceptionally(
                                new IllegalStateException("Core failed to start matchmaking server: HTTP " + response.statusCode()));
                    }
                });
    }

    private void shutdownRegisteredServer(String serverName) {
        String url = coreApiUrl
                + "/shutdown?server=" + URLEncoder.encode(serverName, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
