package net.villagerzock.cloudcore.core.api;

import com.google.gson.Gson;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.corehandshake.MetricRange;
import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.NetworkPoint;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreMetricForwarder {
    private static final Duration INTERVAL = Duration.ofSeconds(2);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Map<String, String> LAST_PAYLOADS = new HashMap<>();

    private static volatile Thread worker;

    private CoreMetricForwarder() {
    }

    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofPlatform()
                .name("core-metric-forwarder")
                .daemon(true)
                .start(CoreMetricForwarder::runLoop);
    }

    public static void stop() {
        RUNNING.set(false);
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    private static void runLoop() {
        CoreHandshakeProviderImpl provider = new CoreHandshakeProviderImpl();
        while (RUNNING.get()) {
            try {
                pushIfChanged(new MetricPayload(
                        "proxy",
                        provider.getProxyPlayerCount(MetricRange.MINUTES),
                        provider.getProxyNetwork(MetricRange.MINUTES)));

                for (String serverName : ServerManager.getRunningServers().keySet()) {
                    pushIfChanged(new MetricPayload(
                            "server-" + serverName,
                            provider.getServerPlayerCount(serverName),
                            List.of()));
                }
            } catch (RuntimeException ignored) {
                // Velocity may be restarting; retry the complete snapshot on the next iteration.
            }

            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        RUNNING.set(false);
    }

    private static void pushIfChanged(MetricPayload payload) {
        String json = GSON.toJson(payload);
        if (json.equals(LAST_PAYLOADS.get(payload.console()))) {
            return;
        }
        if (push(json)) {
            LAST_PAYLOADS.put(payload.console(), json);
        }
    }

    private static boolean push(String json) {
        String backendUrl = System.getenv().getOrDefault(
                "CLOUDCORE_BACKEND_URL",
                "http://localhost:8080").replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(backendUrl + "/api/core/metrics"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record MetricPayload(
            String console,
            List<ChartPoint> playerCount,
            List<NetworkPoint> network
    ) {
    }
}
