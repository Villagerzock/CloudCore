package net.villagerzock.cloudcore.core.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.server.ProxyServerManager;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.cloudcore.core.server.ServerType;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import net.villagerzock.corehandshake.CoreHandshakeProvider;
import net.villagerzock.corehandshake.MetricRange;
import net.villagerzock.corehandshake.dto.*;

import java.io.File;
import java.io.FileFilter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Pattern;

public class CoreHandshakeProviderImpl implements CoreHandshakeProvider {
    private static final Gson GSON = new Gson();
    private static final long MAX_INLINE_FILE_BYTES = 256L * 1024L;
    private static final Pattern MC_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Override
    public List<ServerInfo> getRunningServers() {
        return ServerManager.getRunningServers().values().stream().map(CoreHandshakeMapper::runningToInfo).toList();
    }

    @Override
    public Optional<ServerInfo> getServer(String serverName) {
        return Optional.ofNullable(CoreHandshakeMapper.runningToInfo(ServerManager.getRunningServers().get(serverName)));
    }

    @Override
    public String launchServer(String template, boolean singleton) {
        try {
            ServerManager.ServerLaunchResult result = ServerManager.launchServer(template, singleton).get();
            if (result.server() == null) {
                throw new IllegalStateException(result.message());
            }
            result.started().exceptionally(exception -> {
                System.out.println("Server failed to become ready: " + exception.getMessage());
                return null;
            });
            return result.server().name();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while launching server", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Failed to launch server", exception.getCause());
        }
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
    public ServerTemplate createTemplate(CreateTemplateRequest request) {
        String name = requirePathSegment(request.name(), "name");
        String software = requireValue(request.serverSoftware(), "serverSoftware").toLowerCase(Locale.ROOT);
        String version = requireValue(request.version(), "version");
        String memory = requireValue(request.memory(), "memory");
        String worldType = requireValue(request.worldType(), "worldType").toLowerCase(Locale.ROOT);
        String seed = request.seed() == null ? "" : request.seed().trim();
        String superflatType = request.superflatType() == null ? null : request.superflatType().trim();

        ServerType serverType = parseServerType(software);
        Map<String, String> versions = net.villagerzock.cloudcore.core.Main.SERVER_TO_VERSION_TO_URL_MAP.get(software);
        if (versions == null || !versions.containsKey(version)) {
            throw new IllegalArgumentException("Unknown version " + version + " for " + software);
        }
        Path templateDir = ServerManager.BASE_DIR.resolve("templates").resolve(name).normalize();
        if (Files.exists(templateDir)) {
            throw new IllegalArgumentException("Template already exists: " + name);
        }

        ServerManager.createServer(
                serverType,
                versions.get(version),
                version,
                name,
                memory,
                worldType,
                superflatType,
                seed);
        return new ServerTemplate(name, software, version);
    }

    @Override
    public List<MatchmakingConfiguration> getMatchmakingConfigurations() {
        return Config.getInstance().getMatchmaking().entrySet().stream()
                .map(entry -> toHandshakeMatchmakingConfiguration(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(MatchmakingConfiguration::name))
                .toList();
    }

    @Override
    public MatchmakingConfiguration saveMatchmakingConfiguration(MatchmakingConfiguration configuration) {
        String name = requirePathSegment(configuration.name(), "name");
        String template = requirePathSegment(configuration.template(), "template");
        if (!Files.isDirectory(ServerManager.BASE_DIR.resolve("templates").resolve(template).normalize())) {
            throw new IllegalArgumentException("Template not found: " + template);
        }
        if (configuration.maxAmountOfServers() < 1
                || configuration.maxPlayersPerServer() < 1
                || configuration.playersPerTeam() < 1
                || configuration.maxMmvDiff() < 0) {
            throw new IllegalArgumentException("Matchmaking limits are invalid");
        }

        Config.MatchmakingConfiguration saved = new Config.MatchmakingConfiguration();
        saved.template = template;
        saved.maxAmountOfServers = configuration.maxAmountOfServers();
        saved.maxPlayersPerServer = configuration.maxPlayersPerServer();
        saved.playersPerTeam = configuration.playersPerTeam();
        saved.canRejoin = configuration.canRejoin();
        saved.splitSameQueue = configuration.splitSameQueue();
        saved.singleQueueServerOnSplit = configuration.singleQueueServerOnSplit();
        saved.maxMmvDiff = configuration.maxMmvDiff();

        Config.getInstance().getMatchmaking().put(name, saved);
        Config.getInstance().save();
        pushProxyConfiguration();
        return toHandshakeMatchmakingConfiguration(name, saved);
    }

    @Override
    public void deleteMatchmakingConfiguration(String name) {
        String normalized = requirePathSegment(name, "name");
        Config.MatchmakingConfiguration removed = Config.getInstance().getMatchmaking().remove(normalized);
        if (removed == null) {
            throw new NoSuchElementException("Matchmaking configuration not found: " + normalized);
        }
        Config.getInstance().save();
        pushProxyConfiguration();
    }

    @Override
    public MaintenanceStatus getMaintenanceStatus() {
        return GSON.fromJson(
                ProxyServerManager.getInstance().maintenanceStatus(),
                MaintenanceStatus.class);
    }

    @Override
    public MaintenanceStatus setMaintenanceActive(boolean active) {
        if (active) {
            ProxyServerManager.getInstance().maintenanceOn();
        } else {
            ProxyServerManager.getInstance().maintenanceOff();
        }
        return getMaintenanceStatus();
    }

    @Override
    public MaintenanceStatus addMaintenancePlayer(AddMaintenancePlayerRequest request) {
        UUID uuid = resolveMaintenancePlayer(request.player());
        ProxyServerManager.getInstance().addPlayer(uuid);
        return getMaintenanceStatus();
    }

    @Override
    public MaintenanceStatus removeMaintenancePlayer(String uuid) {
        ProxyServerManager.getInstance().removePlayer(UUID.fromString(requireValue(uuid, "uuid")));
        return getMaintenanceStatus();
    }

    @Override
    public FileSystemResponse getTemplateFileSystemPath(String template, String path) {
        Path requestedPath = resolveTemplatePath(template, path);
        try {
            if (Files.isDirectory(requestedPath)) {
                try (var children = Files.list(requestedPath)) {
                    return new FolderResponse(children
                            .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                            .map(child -> new FolderResponse.FileInFolder(
                                    child.getFileName().toString(),
                                    Files.isRegularFile(child)))
                            .toList());
                }
            }

            long sizeBytes = Files.size(requestedPath);
            String type = contentType(requestedPath);
            boolean binary = isBinaryFile(requestedPath, type);
            String normalizedPath = normalizeRelativePath(path);
            return new FileResponse(
                    type,
                    binary,
                    !binary && sizeBytes > MAX_INLINE_FILE_BYTES,
                    sizeBytes,
                    "/core-handshake/v1/templates/" + template + "/content/" + normalizedPath,
                    "/core-handshake/v1/templates/" + template + "/download/" + normalizedPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read template path " + path, exception);
        }
    }

    @Override
    public FileDownload downloadTemplateFile(String template, String path) {
        Path requestedPath = resolveTemplatePath(template, path);
        try {
            if (Files.isDirectory(requestedPath)) {
                return new FileDownload(
                        requestedPath.getFileName().toString() + ".zip",
                        "application/zip",
                        zipDirectory(requestedPath));
            }

            return new FileDownload(
                    requestedPath.getFileName().toString(),
                    contentType(requestedPath),
                    Files.readAllBytes(requestedPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to download template path " + path, exception);
        }
    }

    @Override
    public FileDownload getTemplateFileContent(String template, String path) {
        Path requestedPath = resolveTemplatePath(template, path);
        if (!Files.isRegularFile(requestedPath)) {
            throw new NoSuchElementException("File not found: " + template + "/" + path);
        }
        try {
            return new FileDownload(
                    requestedPath.getFileName().toString(),
                    contentType(requestedPath),
                    Files.readAllBytes(requestedPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read template path " + path, exception);
        }
    }

    @Override
    public FileSystemResponse saveTemplateFile(String template, String path, SaveTemplateFileRequest request) {
        Path target = resolveTemplateFileTarget(template, path);
        try {
            Files.createDirectories(target.getParent());
            byte[] content = request.binary()
                    ? Base64.getDecoder().decode(request.content())
                    : request.content().getBytes(StandardCharsets.UTF_8);
            Files.write(target, content);
            return getTemplateFileSystemPath(template, path);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("File content is not valid base64", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save template path " + path, exception);
        }
    }

    @Override
    public FileSystemResponse uploadTemplateFile(String template, String folderPath, UploadTemplateFileRequest request) {
        Path folder = resolveTemplatePath(template, folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Upload path must be a folder");
        }

        Path target = folder.resolve(normalizeRelativePath(request.relativePath())).normalize();
        if (!target.startsWith(folder)) {
            throw new IllegalArgumentException("Upload path must stay inside folder");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, Base64.getDecoder().decode(request.content()));
            return getTemplateFileSystemPath(template, folderPath);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Uploaded file content is not valid base64", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload template file " + request.relativePath(), exception);
        }
    }

    @Override
    public void deleteTemplatePath(String template, String path) {
        Path target = resolveTemplatePath(template, path);
        Path templateRoot = resolveTemplateRoot(template);
        if (target.equals(templateRoot)) {
            throw new IllegalArgumentException("Template root cannot be deleted here");
        }

        try {
            deleteRecursive(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete template path " + path, exception);
        }
    }

    @Override
    public FileSystemResponse createTemplateFolder(String template, String folderPath, String folderName) {
        Path folder = resolveTemplatePath(template, folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Target path must be a folder");
        }
        Path target = resolveChildTarget(folder, folderName);
        try {
            Files.createDirectory(target);
            return getTemplateFileSystemPath(template, folderPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create folder " + folderName, exception);
        }
    }

    @Override
    public FileSystemResponse copyTemplatePath(String template, String sourcePath, String destinationFolderPath) {
        Path source = resolveNonRootTemplatePath(template, sourcePath);
        Path destinationFolder = resolveTemplatePath(template, destinationFolderPath);
        if (!Files.isDirectory(destinationFolder)) {
            throw new IllegalArgumentException("Destination must be a folder");
        }

        Path target = destinationFolder.resolve(source.getFileName()).normalize();
        ensureInsideTemplate(template, target);
        if (Files.isDirectory(source) && target.startsWith(source)) {
            throw new IllegalArgumentException("Cannot copy a folder into itself");
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Destination already exists: " + destinationFolderPath);
        }

        try {
            copyRecursive(source, target);
            return getTemplateFileSystemPath(template, parentRelativePath(source, template));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy template path " + sourcePath, exception);
        }
    }

    @Override
    public FileSystemResponse moveTemplatePath(String template, String sourcePath, String destinationFolderPath) {
        Path source = resolveNonRootTemplatePath(template, sourcePath);
        Path destinationFolder = resolveTemplatePath(template, destinationFolderPath);
        if (!Files.isDirectory(destinationFolder)) {
            throw new IllegalArgumentException("Destination must be a folder");
        }

        Path target = destinationFolder.resolve(source.getFileName()).normalize();
        ensureInsideTemplate(template, target);
        if (Files.isDirectory(source) && target.startsWith(source)) {
            throw new IllegalArgumentException("Cannot move a folder into itself");
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Destination already exists: " + destinationFolderPath);
        }

        try {
            Files.move(source, target);
            return getTemplateFileSystemPath(template, parentRelativePath(source, template));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to move template path " + sourcePath, exception);
        }
    }

    @Override
    public FileSystemResponse renameTemplatePath(String template, String sourcePath, String newName) {
        Path source = resolveNonRootTemplatePath(template, sourcePath);
        Path target = resolveChildTarget(source.getParent(), newName);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Destination already exists: " + newName);
        }

        try {
            Files.move(source, target);
            return getTemplateFileSystemPath(template, parentRelativePath(target, template));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to rename template path " + sourcePath, exception);
        }
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
    public List<ChartPoint> getProxyPlayerCount(MetricRange range) {
        return GSON.fromJson(
                ProxyServerManager.getInstance().get(
                        "/api/metrics/proxy/player-count?range=" + range.queryValue()),
                new TypeToken<List<ChartPoint>>() {}.getType());
    }

    @Override
    public List<NetworkPoint> getProxyNetwork(MetricRange range) {
        return GSON.fromJson(
                ProxyServerManager.getInstance().get(
                        "/api/metrics/proxy/network?range=" + range.queryValue()),
                new TypeToken<List<NetworkPoint>>() {}.getType());
    }

    @Override
    public List<ChartPoint> getServerPlayerCount(String serverName) {
        return GSON.fromJson(
                ProxyServerManager.getInstance().get(
                        "/api/metrics/servers/"
                                + URLEncoder.encode(serverName, StandardCharsets.UTF_8)
                                + "/player-count"),
                new TypeToken<List<ChartPoint>>() {}.getType());
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

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private MatchmakingConfiguration toHandshakeMatchmakingConfiguration(
            String name,
            Config.MatchmakingConfiguration configuration
    ) {
        return new MatchmakingConfiguration(
                name,
                configuration.getTemplate(),
                configuration.getMaxAmountOfServers(),
                configuration.getMaxPlayersPerServer(),
                configuration.getPlayersPerTeam(),
                configuration.isCanRejoin(),
                configuration.isSplitSameQueue(),
                configuration.isSingleQueueServerOnSplit(),
                configuration.getMaxMmvDiff());
    }

    private Map<String, ConfigDto.MatchmakingServerConfigDto> proxyMatchmakingConfigurations() {
        Map<String, ConfigDto.MatchmakingServerConfigDto> result = new LinkedHashMap<>();
        Config.getInstance().getMatchmaking().forEach((name, configuration) -> result.put(
                name,
                new ConfigDto.MatchmakingServerConfigDto(
                        configuration.getTemplate(),
                        configuration.getMaxAmountOfServers(),
                        configuration.getMaxPlayersPerServer(),
                        configuration.getPlayersPerTeam(),
                        configuration.isCanRejoin(),
                        configuration.isSplitSameQueue(),
                        configuration.isSingleQueueServerOnSplit(),
                        configuration.getMaxMmvDiff())));
        return result;
    }

    private void pushProxyConfiguration() {
        Config current = Config.getInstance();
        try {
            ProxyServerManager.getInstance().configure(new ConfigDto(
                    current.getLobby().getServer(),
                    proxyMatchmakingConfigurations(),
                    current.getProxy().getMaintenanceMotd()));
        } catch (RuntimeException exception) {
            System.out.println("Failed to push matchmaking configuration to Velocity: " + exception.getMessage());
        }
    }

    private String requirePathSegment(String value, String name) {
        String normalized = requireValue(value, name);
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException(name + " must be a single path segment");
        }
        return normalized;
    }

    private ServerType parseServerType(String software) {
        try {
            return ServerType.valueOf(software.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown server software: " + software, exception);
        }
    }

    private UUID resolveMaintenancePlayer(String player) {
        String normalized = requireValue(player, "player");
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, resolve as Minecraft name below.
        }
        if (!MC_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Player must be a UUID or Minecraft username");
        }
        return resolveMojangUuid(normalized);
    }

    private UUID resolveMojangUuid(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                throw new NoSuchElementException("Minecraft player not found: " + name);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Mojang API failed: " + response.statusCode());
            }
            MojangProfile profile = GSON.fromJson(response.body(), MojangProfile.class);
            if (profile == null || profile.id == null || profile.id.isBlank()) {
                throw new NoSuchElementException("Minecraft player not found: " + name);
            }
            return undashedUuid(profile.id);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to resolve Minecraft player", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while resolving Minecraft player", exception);
        }
    }

    private UUID undashedUuid(String value) {
        String id = value.replace("-", "");
        if (id.length() != 32) {
            throw new IllegalArgumentException("Invalid Mojang UUID: " + value);
        }
        return UUID.fromString(id.substring(0, 8)
                + "-"
                + id.substring(8, 12)
                + "-"
                + id.substring(12, 16)
                + "-"
                + id.substring(16, 20)
                + "-"
                + id.substring(20));
    }

    private static class MojangProfile {
        String id;
    }

    private String normalizeRelativePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return ".";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private Path resolveTemplatePath(String template, String path) {
        Path templateDir = resolveTemplateRoot(template);

        Path requestedPath = templateDir.resolve(normalizeRelativePath(path)).normalize();
        if (!requestedPath.startsWith(templateDir)) {
            throw new IllegalArgumentException("Path must stay inside template");
        }
        if (!Files.exists(requestedPath)) {
            throw new NoSuchElementException("Path not found: " + template + "/" + path);
        }
        return requestedPath;
    }

    private Path resolveNonRootTemplatePath(String template, String path) {
        Path target = resolveTemplatePath(template, path);
        if (target.equals(resolveTemplateRoot(template))) {
            throw new IllegalArgumentException("Template root cannot be changed here");
        }
        return target;
    }

    private Path resolveTemplateFileTarget(String template, String path) {
        Path templateDir = resolveTemplateRoot(template);
        Path target = templateDir.resolve(normalizeRelativePath(path)).normalize();
        if (!target.startsWith(templateDir)) {
            throw new IllegalArgumentException("Path must stay inside template");
        }
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException("Path points to a folder");
        }
        return target;
    }

    private Path resolveTemplateRoot(String template) {
        Path templatesDir = ServerManager.BASE_DIR.resolve("templates").normalize();
        Path templateDir = templatesDir.resolve(normalizeRelativePath(template)).normalize();
        if (!templateDir.startsWith(templatesDir) || !templateDir.getParent().equals(templatesDir)) {
            throw new IllegalArgumentException("Invalid template");
        }
        if (!Files.isDirectory(templateDir)) {
            throw new NoSuchElementException("Template not found: " + template);
        }
        return templateDir;
    }

    private void ensureInsideTemplate(String template, Path target) {
        if (!target.normalize().startsWith(resolveTemplateRoot(template))) {
            throw new IllegalArgumentException("Path must stay inside template");
        }
    }

    private Path resolveChildTarget(Path folder, String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Name must be a single path segment");
        }
        Path target = folder.resolve(name).normalize();
        if (!target.getParent().equals(folder.normalize())) {
            throw new IllegalArgumentException("Name must stay inside folder");
        }
        return target;
    }

    private String parentRelativePath(Path path, String template) {
        Path root = resolveTemplateRoot(template);
        Path parent = path.getParent();
        if (parent == null || parent.equals(root)) {
            return "";
        }
        return root.relativize(parent).toString().replace(File.separatorChar, '/');
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectory(target);
            try (var children = Files.list(source)) {
                for (Path child : children.toList()) {
                    copyRecursive(child, target.resolve(child.getFileName()));
                }
            }
            return;
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void deleteRecursive(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (var children = Files.list(target)) {
                for (Path child : children.toList()) {
                    deleteRecursive(child);
                }
            }
        }
        Files.delete(target);
    }

    private byte[] zipDirectory(Path directory) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output);
             var paths = Files.walk(directory)) {
            for (Path path : paths.filter(path -> !path.equals(directory)).toList()) {
                String entryName = directory.relativize(path).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(path)) {
                    zip.putNextEntry(new ZipEntry(entryName + "/"));
                    zip.closeEntry();
                    continue;
                }

                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private Optional<String> decodeText(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException exception) {
            return Optional.empty();
        }
    }

    private boolean isBinaryFile(Path path, String type) throws IOException {
        if (isBinaryType(type)) {
            return true;
        }
        if (Files.size(path) > MAX_INLINE_FILE_BYTES) {
            return false;
        }
        return decodeText(Files.readAllBytes(path)).isEmpty();
    }

    private String contentType(Path path) throws IOException {
        String type = Files.probeContentType(path);
        return type == null ? "application/octet-stream" : type;
    }

    private boolean isBinaryType(String type) {
        return !(type.startsWith("text/")
                || type.equals("application/json")
                || type.equals("application/xml")
                || type.equals("application/javascript")
                || type.equals("application/x-yaml"));
    }
}
