package net.villagerzock.velocity.controller;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.villagerzock.velocity.dto.PlayerRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/player")
public class PlayerController {
    private final ProxyServer proxyServer;

    public PlayerController(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
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

}
