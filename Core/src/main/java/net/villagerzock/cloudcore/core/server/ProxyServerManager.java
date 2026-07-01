package net.villagerzock.cloudcore.core.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.villagerzock.cloudcore.core.server.dto.CallbackDto;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import net.villagerzock.cloudcore.core.server.dto.ServerCreationDto;
import net.villagerzock.cloudcore.core.server.dto.ServerShutdownDto;
import net.villagerzock.corehandshake.dto.ResolvedBanRequest;
import net.villagerzock.corehandshake.dto.UpdateBannedPlayerRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

public class ProxyServerManager {
    private static final ProxyServerManager INSTANCE = new ProxyServerManager();
    private String host;
    private int port;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (src, typeOfSrc, context) -> src == null ? null : context.serialize(src.toString()))
            .create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

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
        sendForBody(method, path, body);
    }

    private String sendForBody(String method, String path, String body) {
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
            return response.body();
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
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT
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

    public String maintenanceStatus() {
        return get("/api/maintenance/status");
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

    public void addPlayer(UUID player, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            addPlayer(player);
            return;
        }
        send("POST", "/api/maintenance/?playerUUID=" + player
                + "&playerName=" + URLEncoder.encode(playerName, StandardCharsets.UTF_8), "");
    }

    public void removePlayer(UUID player) {
        send("DELETE", "/api/maintenance?playerUUID=" + player, "");
    }

    public String getBans() {
        return get("/api/bans");
    }

    public String createBan(ResolvedBanRequest request) {
        return sendForBody("POST", "/api/bans", GSON.toJson(request));
    }

    public String updateBan(UUID uuid, UpdateBannedPlayerRequest request) {
        return sendForBody("PATCH", "/api/bans/" + uuid, GSON.toJson(request));
    }

    public void deleteBan(UUID uuid) {
        send("DELETE", "/api/bans/" + uuid, "");
    }
}
