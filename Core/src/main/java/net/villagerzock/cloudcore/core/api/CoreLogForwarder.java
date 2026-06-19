package net.villagerzock.cloudcore.core.api;

import com.google.gson.Gson;
import net.villagerzock.cloudcore.core.server.ServerManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreLogForwarder {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Map<String, Instant> CURSORS = new HashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static volatile Thread worker;

    private CoreLogForwarder() {
    }

    public static void start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofPlatform()
                .name("core-log-forwarder")
                .daemon(true)
                .start(CoreLogForwarder::runLoop);
    }

    public static void stop() {
        RUNNING.set(false);
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            Map<String, LogTarget> targets = currentTargets();
            CURSORS.keySet().retainAll(targets.keySet());
            for (Map.Entry<String, LogTarget> entry : targets.entrySet()) {
                if (!RUNNING.get()) {
                    break;
                }
                poll(entry.getKey(), entry.getValue());
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        RUNNING.set(false);
    }

    private static Map<String, LogTarget> currentTargets() {
        Map<String, LogTarget> targets = new HashMap<>();
        targets.put("cloudcore-proxy", new LogTarget("cloudcore-proxy", "proxy"));
        for (ServerManager.RunningServer server : ServerManager.getRunningServers().values()) {
            targets.put(server.containerName(), new LogTarget(
                    server.containerName(),
                    "server-" + server.name()));
        }
        return targets;
    }

    private static void poll(String key, LogTarget target) {
        Instant cursor = CURSORS.computeIfAbsent(key, ignored -> Instant.now());
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "docker",
                    "logs",
                    "--since",
                    cursor.toString(),
                    "--timestamps",
                    target.container())
                    .redirectErrorStream(true)
                    .start();

            List<LogLine> logLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parse(line, cursor).ifPresent(logLines::add);
                }
            }

            if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0 || logLines.isEmpty()) {
                return;
            }

            Instant newest = logLines.stream()
                    .map(LogLine::timestamp)
                    .max(Instant::compareTo)
                    .orElse(cursor);
            List<String> lines = logLines.stream().map(LogLine::text).toList();
            push(target.console(), lines);
            CURSORS.put(key, newest);
        } catch (IOException exception) {
            // Docker or Backend may be temporarily unavailable; the next poll retries from the same cursor.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static java.util.Optional<LogLine> parse(String line, Instant cursor) {
        int separator = line.indexOf(' ');
        if (separator <= 0) {
            return java.util.Optional.empty();
        }
        try {
            Instant timestamp = Instant.parse(line.substring(0, separator));
            if (!timestamp.isAfter(cursor)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new LogLine(timestamp, line.substring(separator + 1)));
        } catch (DateTimeParseException exception) {
            return java.util.Optional.empty();
        }
    }

    private static void push(String console, List<String> lines) throws IOException, InterruptedException {
        String backendUrl = System.getenv().getOrDefault(
                "CLOUDCORE_BACKEND_URL",
                "http://localhost:8080").replaceAll("/+$", "");
        String body = GSON.toJson(Map.of("console", console, "lines", lines));
        HttpRequest request = HttpRequest.newBuilder(URI.create(backendUrl + "/api/core/logs"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Backend rejected log push with status " + response.statusCode());
        }
    }

    private record LogTarget(String container, String console) {
    }

    private record LogLine(Instant timestamp, String text) {
    }
}
