package net.villagerzock.velocity.service;


import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.aspectj.apache.bcel.classfile.Module;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class ServerMangementService {
    private final ProxyServer proxyServer;

    private final Map<String, String> SERVERS = new HashMap<>();

    public ServerMangementService(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public RegisteredServer findAnyServerOfType(String type){
        int smallest = Integer.MAX_VALUE;
        RegisteredServer smallestServer = null;
        for (String server : SERVERS.keySet()){
            if (SERVERS.get(server).equals(type)){
                Optional<RegisteredServer> serverOpt = proxyServer.getServer(server);
                if (serverOpt.isPresent() && smallest > serverOpt.get().getPlayersConnected().size()){
                    smallest = serverOpt.get().getPlayersConnected().size();
                    smallestServer = serverOpt.get();
                }
            }
        }
        if (smallestServer == null){
            return null;
        }

        return smallestServer;
    }
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    @EventListener
    public void onReady(ApplicationReadyEvent event){
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://mariadb:8008/started"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            System.out.println("Registered with proxy: " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to register with proxy", e);
        }
    }

    public void register(String type, String name) {
        SERVERS.put(name,type);
    }

    public void unregister(String name) {
        SERVERS.remove(name);
    }

    public Map<String,String> getServers(){
        return SERVERS;
    }

    public String getTypeOf(String name) {
        return SERVERS.get(name);
    }
}
