package net.villagerzock.cloudcore.core.api;

import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.cloudcore.core.server.ServerType;
import net.villagerzock.corehandshake.CoreHandshakeProvider;
import net.villagerzock.corehandshake.dto.*;

import java.io.File;
import java.io.FileFilter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CoreHandshakeProviderImpl implements CoreHandshakeProvider {
    @Override
    public List<ServerInfo> getRunningServers() {
        return ServerManager.getRunningServers().values().stream().map(CoreHandshakeMapper::runningToInfo).toList();
    }

    @Override
    public Optional<ServerInfo> getServer(String serverName) {
        return Optional.ofNullable(CoreHandshakeMapper.runningToInfo(ServerManager.getRunningServers().get(serverName)));
    }

    @Override
    public List<ServerTemplate> getTemplates() {
        List<ServerTemplate> result = new ArrayList<>();
        Path templatesDir = ServerManager.BASE_DIR.resolve("templates");
        for (File file : Objects.requireNonNull(templatesDir.toFile().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        }))){
            String name = file.getName();
            ServerManager.ServerConfig config = ServerManager.readServerConfig(file.toPath());
            ServerType type = config.getType();
            String version = config.getVersion();

            result.add(new ServerTemplate(name,type.name().toLowerCase(Locale.ROOT),version));
        }

        return result;
    }

    @Override
    public List<String> getProxyLogs() {
        return getContainerLogs("cloudcore-proxy");
    }

    @Override
    public List<String> getServerLogs(String server) {
        return getContainerLogs(server);
    }

    @Override
    public void executeProxyCommand(String command) {
        sendContainerCommand("cloudcore-proxy", command);
    }

    @Override
    public void executeServerCommand(String server, String command) {
        sendContainerCommand(server, command);
    }

    @Override
    public NodeMetadata getMetadata() {
        return null;
    }

    @Override
    public List<ChartPoint> getProxyPlayerCount() {
        return List.of();
    }

    @Override
    public List<NetworkPoint> getProxyNetwork() {
        return List.of();
    }

    @Override
    public List<ChartPoint> getServerPlayerCount(String serverName) {
        return List.of();
    }

    @Override
    public List<NetworkPoint> getServerNetwork(String serverName) {
        return List.of();
    }

    private List<String> getContainerLogs(String container) {
        requireValue(container, "container");
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "logs", "--tail", "200", container)
                    .redirectErrorStream(true)
                    .start();
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                lines = reader.lines().toList();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "docker logs failed for container " + container + ": " + String.join("\n", lines));
            }
            return lines;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not execute docker logs for container " + container, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading logs for container " + container, exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private void sendContainerCommand(String container, String command) {
        requireValue(container, "container");
        requireValue(command, "command");
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "attach", "--sig-proxy=false", container)
                    .redirectErrorStream(true)
                    .start();
            Process attachedProcess = process;
            Thread.ofVirtual().start(() -> {
                try (var input = attachedProcess.getInputStream()) {
                    input.transferTo(java.io.OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                    // The stream closes when the short-lived docker attach client is destroyed.
                }
            });
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(command);
                writer.newLine();
                writer.flush();
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
            }
            if (!process.isAlive() && process.exitValue() != 0) {
                throw new IllegalStateException(
                        "docker attach failed for container " + container + " with exit code " + process.exitValue());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not send command to container " + container, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending command to container " + container, exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
