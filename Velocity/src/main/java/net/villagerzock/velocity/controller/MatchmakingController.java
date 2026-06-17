package net.villagerzock.velocity.controller;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.villagerzock.velocity.dto.MatchmakingRequestDto;
import net.villagerzock.velocity.service.MatchmakingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {

    private final MatchmakingService matchmakingService;
    private final ProxyServer proxyServer;

    public MatchmakingController(MatchmakingService matchmakingService, ProxyServer proxyServer) {
        this.matchmakingService = matchmakingService;
        this.proxyServer = proxyServer;
    }

    @PostMapping("/")
    public ResponseEntity<?> startMatchmaking(@RequestBody MatchmakingRequestDto dto){
        matchmakingService.queue(dto.partyOfPlayers(),dto.serverType(),dto.matchmakingValue()).thenAcceptAsync((server)->{
            for (UUID uuid : dto.partyOfPlayers()){
                Optional<Player> playerOpt = proxyServer.getPlayer(uuid);
                if (playerOpt.isEmpty()) continue;

                Player player = playerOpt.get();
                player.createConnectionRequest(server).fireAndForget();
            }
        });

        return ResponseEntity.notFound().build();
    }

}
