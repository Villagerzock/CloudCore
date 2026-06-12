package net.villagerzock.velocity.entities;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchmakingServer {

    private ServerState state;
    private RegisteredServer server;
    private int averageMatchmakingValue;
    private String type;

    public MatchmakingServer(RegisteredServer server, int averageMatchmakingValue, String type){
        this.state = ServerState.WAITING;
        this.server = server;
        this.averageMatchmakingValue = averageMatchmakingValue;
        this.type = type;
    }

    public enum ServerState {
        WAITING,
        RUNNING,
        FINISHED
    }

    public boolean canAcceptPlayers() {
        return state == ServerState.WAITING;
    }

    public boolean canAcceptRejoins(boolean canRejoin) {
        return state == ServerState.RUNNING && canRejoin;
    }

    public boolean isFinished() {
        return state == ServerState.FINISHED;
    }
}