package net.villagerzock.cloudcore.core.server;

import com.google.gson.Gson;
import net.villagerzock.cloudcore.core.server.dto.CallbackDto;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import net.villagerzock.cloudcore.core.server.dto.ServerCreationDto;
import net.villagerzock.cloudcore.core.server.dto.ServerShutdownDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

public class ProxyServerManager {
    private static final ProxyServerManager INSTANCE = new ProxyServerManager();
    private String host;
    private int port;

    private static final Gson GSON = new Gson();

    public void register(String name, String base, String host, int port) {
        send(
                "POST",
                "/api/server/",
                GSON.toJson(new ServerCreationDto(
                        name,
                        base,
                        host,
                        port
                ))
        );
    }

    public void unregister(String name, String fallback) {
        send(
                "DELETE",
                "/api/server/",
                GSON.toJson(new ServerShutdownDto(
                        name,
                        fallback
                ))
        );
    }

    public void configure(ConfigDto configDto){
        send("POST", "/api/configure", GSON.toJson(configDto));
    }

    private void send(String method, String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "Request failed: " + response.statusCode() + " - " + response.body()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to contact Velocity API", e);
        }
    }

    public void setHostAndPort(String host, int port){
        this.host = host;
        this.port = port;
    }

    public static ProxyServerManager getInstance(){
        return INSTANCE;
    }

    public void callback(UUID uuid, Map<String, Object> data) {
        send("POST", "/api/callback", GSON.toJson(new CallbackDto(uuid,data)));
    }
}
