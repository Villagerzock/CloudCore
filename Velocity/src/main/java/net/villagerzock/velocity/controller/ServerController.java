package net.villagerzock.velocity.controller;


import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.villagerzock.velocity.config.CloudCoreConfiguration;
import net.villagerzock.velocity.dto.ServerCreationDto;
import net.villagerzock.velocity.dto.ServerShutdownDto;
import net.villagerzock.velocity.service.ServerMangementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetSocketAddress;
import java.util.*;

@RestController
@RequestMapping("/api/server")
public class ServerController {
    private final ProxyServer proxyServer;
    private final ServerMangementService serverMangementService;
    private final CloudCoreConfiguration cloudCoreConfiguration;

    public ServerController(ProxyServer proxyServer, ServerMangementService serverMangementService, CloudCoreConfiguration cloudCoreConfiguration) {
        this.proxyServer = proxyServer;
        this.serverMangementService = serverMangementService;
        this.cloudCoreConfiguration = cloudCoreConfiguration;
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
        Optional<RegisteredServer> fallbackServerOpt = server.fallback() == null ? Optional.ofNullable(serverMangementService.findAnyServerOfType(cloudCoreConfiguration.getLobbyServer())) : proxyServer.getServer(server.fallback());

        if (shutdownServerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RegisteredServer shutdownServer = shutdownServerOpt.get();


        for (Player player : shutdownServer.getPlayersConnected()) {
            if (fallbackServerOpt.isPresent()){
                RegisteredServer fallbackServer = fallbackServerOpt.get();
                player.sendMessage(Component.text("The Server you were on Shutdown so you were moved to a Fallback Server!").style(Style.style(NamedTextColor.RED)));
                player.createConnectionRequest(fallbackServer).fireAndForget();
            }else {
                player.disconnect(Component.text("The Server you were on Shutdown and there are no Available Lobbies!").style(Style.style(NamedTextColor.RED)));
            }
        }

        proxyServer.unregisterServer(shutdownServer.getServerInfo());

        return ResponseEntity.ok("Server Removed");
    }


    @GetMapping("/")
    public ResponseEntity<?> getServers(
            @RequestParam(required = false) String type
    ) {
        Map<String, String> unformattedServers = serverMangementService.getServers();

        if (type != null) {
            List<String> servers = new ArrayList<>();

            for (String server : unformattedServers.keySet()) {
                if (unformattedServers.get(server).equals(type)) {
                    servers.add(server);
                }
            }

            return ResponseEntity.ok(servers);
        }

        Map<String, List<String>> servers = new HashMap<>();

        for (String server : unformattedServers.keySet()) {
            servers.computeIfAbsent(
                    unformattedServers.get(server),
                    k -> new ArrayList<>()
            ).add(server);
        }

        return ResponseEntity.ok(servers);
    }

}
