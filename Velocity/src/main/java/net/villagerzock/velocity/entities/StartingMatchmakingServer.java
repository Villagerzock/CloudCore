package net.villagerzock.velocity.entities;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StartingMatchmakingServer {
    @Getter
    private final CompletableFuture<RegisteredServer> completableFuture;
    @Getter
    private final String type;
    @Getter
    @Setter
    private int matchmakingValue;
    @Getter
    private final List<UUID> queuedPlayers;


    public StartingMatchmakingServer(CompletableFuture<RegisteredServer> completableFuture, String type, int matchmakingValue, List<UUID> queuedPlayers) {
        this.completableFuture = completableFuture;
        this.type = type;
        this.matchmakingValue = matchmakingValue;
        this.queuedPlayers = queuedPlayers;
    }
}
