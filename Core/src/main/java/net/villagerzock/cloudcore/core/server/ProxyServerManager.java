package net.villagerzock.cloudcore.core.server;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

    static ProxyServerManager getInstance(){
        return INSTANCE;
    }
}
