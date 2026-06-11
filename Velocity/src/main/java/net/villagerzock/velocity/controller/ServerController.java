package net.villagerzock.velocity.controller;


import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.villagerzock.velocity.config.LobbyConfiguration;
import net.villagerzock.velocity.dto.LobbySettingsDto;
import net.villagerzock.velocity.dto.ServerCreationDto;
import net.villagerzock.velocity.dto.ServerShutdownDto;
import net.villagerzock.velocity.service.ServerMangementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.html.Option;
import java.net.InetSocketAddress;
import java.util.Optional;

@RestController
@RequestMapping("/api/server")
public class ServerController {
    private final ProxyServer proxyServer;
    private final ServerMangementService serverMangementService;
    private final LobbyConfiguration lobbyConfiguration;

    public ServerController(ProxyServer proxyServer, ServerMangementService serverMangementService, LobbyConfiguration lobbyConfiguration) {
        this.proxyServer = proxyServer;
        this.serverMangementService = serverMangementService;
        this.lobbyConfiguration = lobbyConfiguration;
    }

    @PostMapping("/")
    public ResponseEntity<String> registerServer(@RequestBody ServerCreationDto server){
        ServerInfo serverInfo = new ServerInfo(server.name(),new InetSocketAddress(server.host(),server.port()));

        RegisteredServer registeredServer = proxyServer.registerServer(serverInfo);
        serverMangementService.register(server.base(),server.name());
        registeredServer.getServerInfo();

        return ResponseEntity.ok("Created");
    }


    @DeleteMapping("/")
    public ResponseEntity<String> shutdownServer(@RequestBody ServerShutdownDto server) {
        Optional<RegisteredServer> shutdownServerOpt = proxyServer.getServer(server.name());
        serverMangementService.unregister(server.name());
        Optional<RegisteredServer> fallbackServerOpt = server.fallback() == null ? Optional.of(serverMangementService.findAnyServerOfType(lobbyConfiguration.getServer())) : proxyServer.getServer(server.fallback());

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

    @PostMapping("/lobby")
    public ResponseEntity<String> setLobbySettings(@RequestBody LobbySettingsDto lobbySettingsDto){
        lobbyConfiguration.setServer(lobbySettingsDto.server());
        return ResponseEntity.ok("Updated");
    }
}