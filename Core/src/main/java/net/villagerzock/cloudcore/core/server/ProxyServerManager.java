package net.villagerzock.cloudcore.core.server;

import com.google.gson.Gson;
import net.villagerzock.cloudcore.core.server.dto.CallbackDto;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import net.villagerzock.cloudcore.core.server.dto.ServerCreationDto;
import net.villagerzock.cloudcore.core.server.dto.ServerShutdownDto;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
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

    public String get(String path) {
        try {
            return sendGet(path);
        } catch (RuntimeException firstFailure) {
            refreshApiPort();
            return sendGet(path);
        }
    }

    private String sendGet(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + path))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "Request failed: " + response.statusCode() + " - " + response.body());
            }
            return response.body();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to contact Velocity API", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while contacting Velocity API", exception);
        }
    }

    private synchronized void refreshApiPort() {
        try {
            Process process = new ProcessBuilder(
                    "docker", "port", "cloudcore-proxy", "8080/tcp")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0 || output.isBlank()) {
                throw new RuntimeException("Could not resolve Velocity API port: " + output);
            }
            String firstBinding = output.lines().findFirst().orElseThrow();
            port = Integer.parseInt(firstBinding.substring(firstBinding.lastIndexOf(':') + 1));
        } catch (IOException exception) {
            throw new RuntimeException("Could not refresh Velocity API port", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while refreshing Velocity API port", exception);
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
    public void maintenanceOn() {
        send("POST", "/api/maintenance/on", "");
    }

    public void maintenanceOff() {
        send("POST", "/api/maintenance/off", "");
    }

    public void addPlayer(String player) {
        send("POST", "/api/maintenance/?playerName=" + URLEncoder.encode(player, StandardCharsets.UTF_8), "");
    }

    public void removePlayer(String player) {
        send("DELETE", "/api/maintenance?playerName=" + URLEncoder.encode(player, StandardCharsets.UTF_8), "");
    }

    public void addPlayer(UUID player) {
        send("POST", "/api/maintenance/?playerUUID=" + player, "");
    }

    public void removePlayer(UUID player) {
        send("DELETE", "/api/maintenance?playerUUID=" + player, "");
    }
}
