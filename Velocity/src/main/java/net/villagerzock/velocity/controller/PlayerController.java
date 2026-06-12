package net.villagerzock.velocity.controller;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.villagerzock.velocity.dto.PlayerRequestDto;
import net.villagerzock.velocity.dto.PlayerResponseDto;
import net.villagerzock.velocity.service.ServerMangementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/player")
public class PlayerController {
    private final ProxyServer proxyServer;
    private final ServerMangementService serverMangementService;

    public PlayerController(ProxyServer proxyServer, ServerMangementService serverMangementService) {
        this.proxyServer = proxyServer;
        this.serverMangementService = serverMangementService;
    }

    @PostMapping("/send/{server}")
    public ResponseEntity<String> sendPlayerTo(@PathVariable String server, @RequestBody PlayerRequestDto requestDto){
        Optional<Player> p = proxyServer.getPlayer(requestDto.uuid());
        if (p.isEmpty()) return ResponseEntity.notFound().build();

        Optional<RegisteredServer> s =  proxyServer.getServer(server);
        if (s.isEmpty()) return ResponseEntity.notFound().build();

        p.get().createConnectionRequest(s.get()).fireAndForget();

        return ResponseEntity.ok("Connected");
    }

    @GetMapping("/")
    public ResponseEntity<PlayerResponseDto> getPlayer(@RequestBody PlayerRequestDto playerRequestDto){
        Optional<Player> p = proxyServer.getPlayer(playerRequestDto.uuid());
        if (p.isEmpty()) return ResponseEntity.notFound().build();

        Optional<ServerConnection> s = p.get().getCurrentServer();
        if (s.isEmpty()) return ResponseEntity.notFound().build();

        String serverName = s.get().getServerInfo().getName();

        return ResponseEntity.ok(new PlayerResponseDto(serverName,serverMangementService.getTypeOf(serverName)));
    }

}
