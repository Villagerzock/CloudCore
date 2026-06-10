package net.villagerzock.velocity.controller;


import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.villagerzock.velocity.dto.ServerCreationDto;
import net.villagerzock.velocity.dto.ServerShutdownDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.html.Option;
import java.net.InetSocketAddress;
import java.util.Optional;

@RestController
@RequestMapping("/api/server")
public class ServerController {
    private final ProxyServer proxyServer;

    public ServerController(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @PostMapping("/")
    public ResponseEntity<String> registerServer(@RequestBody ServerCreationDto server){
        ServerInfo serverInfo = new ServerInfo(server.name(),new InetSocketAddress(server.host(),server.port()));

        RegisteredServer registeredServer = proxyServer.registerServer(serverInfo);
        registeredServer.getServerInfo();

        return ResponseEntity.ok("Created");
    }


    @DeleteMapping("/")
    public ResponseEntity<String> shutdownServer(@RequestBody ServerShutdownDto server) {
        Optional<RegisteredServer> shutdownServerOpt = proxyServer.getServer(server.name());
        Optional<RegisteredServer> fallbackServerOpt = proxyServer.getServer(server.fallback());

        if (shutdownServerOpt.isEmpty() || fallbackServerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RegisteredServer shutdownServer = shutdownServerOpt.get();
        RegisteredServer fallbackServer = fallbackServerOpt.get();

        for (Player player : shutdownServer.getPlayersConnected()) {
            player.createConnectionRequest(fallbackServer).connect();
        }

        proxyServer.unregisterServer(shutdownServer.getServerInfo());

        return ResponseEntity.ok("Server Removed");
    }
}
