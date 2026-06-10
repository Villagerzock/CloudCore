package net.villagerzock.cloudcore.core.server;

import net.villagerzock.cloudcore.core.Main;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.InputResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.NonBlockingReader;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.villagerzock.cloudcore.core.Main.terminal;

public class ServerManager {

    private static final Path BASE_DIR = Path.of(System.getProperty("user.home"), ".cloudcore");
    private static final Path TEMPLATES_DIR = BASE_DIR.resolve("templates");
    private static final Path INSTANCES_DIR = BASE_DIR.resolve("instances");

    private static final String DOCKER_NETWORK = "cloudcore";

    private static final List<String> DOCKER_BASE_ARGS = List.of(
            "docker",
            "run",
            "-d",
            "--network",
            DOCKER_NETWORK
    );



    // ======== PROXY ========

    private static RunningProxy RUNNING_PROXY;

    public static RunningProxy getRunningProxy() {
        return RUNNING_PROXY;
    }

    public static void init() {
        ensureDockerNetwork();
        scanForRunningProxy();
        scanForRunningServers();
    }

    private static void ensureDockerNetwork() {
        try {
            Process inspectProcess = new ProcessBuilder(
                    "docker",
                    "network",
                    "inspect",
                    DOCKER_NETWORK
            ).redirectErrorStream(true).start();

            String inspectOutput = new String(
                    inspectProcess.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (inspectProcess.waitFor() == 0) {
                return;
            }

            System.out.println("Creating docker network: " + DOCKER_NETWORK);

            Process createProcess = new ProcessBuilder(
                    "docker",
                    "network",
                    "create",
                    DOCKER_NETWORK
            ).redirectErrorStream(true).start();

            String createOutput = new String(
                    createProcess.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (createProcess.waitFor() != 0) {
                throw new RuntimeException(
                        "Failed to create docker network: " + createOutput + "\n" + inspectOutput
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure docker network", e);
        }
    }

    public record RunningProxy(
            String containerId,
            String containerName,
            String host,
            String minecraftPort,
            String apiPort
    ) {
    }

    private static void launchProxyWizard() {
        try {
            ConsolePrompt prompt = new ConsolePrompt(terminal);
            PromptBuilder builder = prompt.getPromptBuilder();

            builder.createInputPrompt()
                    .name("proxyPort")
                    .message("Proxy port")
                    .defaultValue("25565")
                    .addPrompt();

            builder.createInputPrompt()
                    .name("memory")
                    .message("Proxy memory, example: 512M / 1G")
                    .defaultValue("512M")
                    .addPrompt();

            builder.createInputPrompt()
                    .name("velocityVersion")
                    .message("Velocity version")
                    .defaultValue("latest")
                    .addPrompt();

            Map<String, ? extends PromptResultItemIF> result = prompt.prompt(builder.build());

            String proxyPort = ((InputResult) result.get("proxyPort")).getResult();
            String memory = ((InputResult) result.get("memory")).getResult();
            String velocityVersion = ((InputResult) result.get("velocityVersion")).getResult();

            setupVelocityProxy(proxyPort, memory, velocityVersion);
            Thread.sleep(1000);
            scanForRunningProxy();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch proxy wizard", e);
        }
    }
    private static void setupVelocityProxy(
            String proxyPort,
            String memory,
            String velocityVersion
    ) throws Exception {
        Path proxyDir = BASE_DIR.resolve("proxy");
        Path serverDir = proxyDir.resolve("server");
        Path pluginsDir = proxyDir.resolve("plugins");
        removeExistingProxyContainer();
        Files.createDirectories(serverDir);
        Files.createDirectories(pluginsDir);

        copyEmbeddedVelocityPlugin(pluginsDir);

        List<String> command = new ArrayList<>(DOCKER_BASE_ARGS);

        command.addAll(List.of(
                "--name",
                "cloudcore-proxy",
                "-p",
                proxyPort + ":25577",
                "-p",
                ":8080",
                "-v",
                serverDir.toAbsolutePath() + ":/server",
                "-v",
                pluginsDir.toAbsolutePath() + ":/plugins",
                "-e",
                "TYPE=VELOCITY",
                "-e",
                "MEMORY=" + memory,
                "-e",
                "VELOCITY_VERSION=" + velocityVersion,
                "-e",
                "ENABLE_RCON=false",
                "itzg/mc-proxy:latest"
        ));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ).trim();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Failed to start Velocity proxy: " + output);
        }

        System.out.println("Started Velocity proxy container: " + output);
    }

    private static void copyEmbeddedVelocityPlugin(Path pluginsDir) throws IOException {
        try (InputStream inputStream = Main.class.getResourceAsStream("/embedded/CloudCore-Velocity.jar")) {
            if (inputStream == null) {
                throw new FileNotFoundException("Missing embedded resource: /embedded/CloudCore-Velocity.jar");
            }

            Files.copy(
                    inputStream,
                    pluginsDir.resolve("CloudCore-Velocity.jar"),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
    private static void removeExistingProxyContainer() {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "rm",
                    "-f",
                    "cloudcore-proxy"
            ).redirectErrorStream(true).start();

            process.getInputStream().readAllBytes();
            process.waitFor();
        } catch (Exception ignored) {
        }
    }
    private static void scanForRunningProxy() {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "ps",
                    "--filter",
                    "name=^/cloudcore-proxy$",
                    "--format",
                    "{{.ID}}|{{.Names}}"
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            int exitCode = process.waitFor();

            if (exitCode != 0 || output.isBlank()) {
                RUNNING_PROXY = null;
                launchProxyWizard();
                return;
            }

            String[] parts = output.split("\\|", 2);

            if (parts.length != 2) {
                RUNNING_PROXY = null;
                launchProxyWizard();
                return;
            }

            String containerId = parts[0].trim();
            String containerName = parts[1].trim();

            String port;

            try {
                port = getDockerPort(containerId, 25577);
            } catch (Exception e) {
                port = "unknown";
            }

            String minecraftPort = getDockerPort(containerId, 25577);
            String apiPort = getDockerPort(containerId, 8080);

            RUNNING_PROXY = new RunningProxy(
                    containerId,
                    containerName,
                    "localhost",
                    minecraftPort,
                    apiPort
            );

            ProxyServerManager.getInstance().setHostAndPort(
                    RUNNING_PROXY.host(),
                    Integer.parseInt(RUNNING_PROXY.apiPort())
            );

            System.out.println("Found running proxy: " + containerName + " on port " + port);
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan running proxy", e);
        }
    }


    // ======== SERVERS ========

    private static final Map<String, Integer> INSTANCE_COUNTERS = new HashMap<>();
    private static final Map<String, RunningServer> RUNNING_SERVERS = new HashMap<>();

    public static void createServer(ServerType serverType, String url, String name) {
        try {
            Path serverDir = TEMPLATES_DIR.resolve(name);

            Files.createDirectories(serverDir);

            Path serverJar = serverDir.resolve("server.jar");

            System.out.println("Downloading server jar...");
            try (InputStream inputStream = URI.create(url).toURL().openStream()) {
                Files.copy(inputStream, serverJar, StandardCopyOption.REPLACE_EXISTING);
            }

            if (serverType.hasPlugins()) {
                Files.createDirectories(serverDir.resolve("plugins"));
            }

            if (serverType.isModded()) {
                Files.createDirectories(serverDir.resolve("mods"));
            }

            Files.writeString(
                    serverDir.resolve("eula.txt"),
                    "eula=true" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            System.out.println("Created server template at: " + serverDir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server template", e);
        }
    }

    public static CompletableFuture<ServerLaunchResult> launchServer(String name, boolean singleton) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path serverDir;
                String instanceName;

                if (singleton) {
                    instanceName = name;
                    serverDir = TEMPLATES_DIR.resolve(name);
                } else {
                    instanceName = nextInstanceName(name);
                    serverDir = createInstance(name, instanceName);
                }

                if (!Files.exists(serverDir.resolve("server.jar"))) {
                    return new ServerLaunchResult(ServerState.FAILED, "server.jar not found in " + serverDir, null);
                }

                String containerName = sanitizeDockerName(instanceName);

                List<String> command = new ArrayList<>(DOCKER_BASE_ARGS);

                command.addAll(List.of(
                        "--name",
                        containerName,
                        "-p",
                        ":25565",
                        "-v",
                        serverDir.toAbsolutePath() + ":/server",
                        "-w",
                        "/server",
                        "eclipse-temurin:21",
                        "java",
                        "-jar",
                        "server.jar",
                        "nogui"
                ));

                Process runProcess = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();

                String containerId = new String(
                        runProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                ).trim();

                int exitCode = runProcess.waitFor();

                if (exitCode != 0) {
                    return new ServerLaunchResult(ServerState.FAILED, "Docker failed: " + containerId, null);
                }

                String port = getDockerPort(containerId);

                RunningServer runningServer = new RunningServer(
                        instanceName,
                        name,
                        singleton,
                        containerId,
                        containerName,
                        serverDir,
                        port
                );

                RUNNING_SERVERS.put(instanceName, runningServer);

                for (int i = 0; i < 30; i++) {
                    ServerState state = getDockerState(containerId);

                    if (state == ServerState.RUNNING) {
                        ProxyServerManager.getInstance().register(
                                runningServer.name(),
                                runningServer.templateName(),
                                runningServer.containerName(),
                                25565
                        );

                        return new ServerLaunchResult(
                                ServerState.RUNNING,
                                "Started Successfully on port " + port,
                                runningServer
                        );
                    }

                    if (state == ServerState.CRASHED || state == ServerState.STOPPED) {
                        return new ServerLaunchResult(
                                ServerState.CRASHED,
                                "Crashed :(",
                                runningServer
                        );
                    }

                    Thread.sleep(1000);
                }

                return new ServerLaunchResult(
                        ServerState.TIMEOUT,
                        "Startup Timeout",
                        runningServer
                );
            } catch (Exception e) {
                return new ServerLaunchResult(
                        ServerState.FAILED,
                        "Failed: " + e.getMessage(),
                        null
                );
            }
        });
    }

    private static String nextInstanceName(String templateName) {
        int id = INSTANCE_COUNTERS.getOrDefault(templateName, 0) + 1;

        while (true) {
            String instanceName = templateName + "-" + id;
            Path instanceDir = INSTANCES_DIR.resolve(instanceName);

            if (!Files.exists(instanceDir) && !RUNNING_SERVERS.containsKey(instanceName)) {
                INSTANCE_COUNTERS.put(templateName, id);
                return instanceName;
            }

            id++;
        }
    }

    private static ServerState getDockerState(String containerId) {
        try {
            Process inspectProcess = new ProcessBuilder(
                    "docker",
                    "inspect",
                    "-f",
                    "{{.State.Status}}",
                    containerId
            ).redirectErrorStream(true).start();

            String status = new String(
                    inspectProcess.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            int exitCode = inspectProcess.waitFor();

            if (exitCode != 0) {
                return ServerState.FAILED;
            }

            return switch (status) {
                case "running" -> ServerState.RUNNING;
                case "exited", "dead" -> ServerState.CRASHED;
                case "created", "restarting", "paused" -> ServerState.STARTING;
                default -> ServerState.FAILED;
            };
        } catch (Exception e) {
            return ServerState.FAILED;
        }
    }

    public enum ServerState {
        STARTING,
        RUNNING,
        STOPPED,
        CRASHED,
        TIMEOUT,
        FAILED
    }

    public record ServerLaunchResult(
            ServerState state,
            String message,
            RunningServer server
    ) {
    }

    public static void scanForRunningServers() {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "ps",
                    "--format",
                    "{{.ID}}|{{.Names}}"
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            int exitCode = process.waitFor();

            if (exitCode != 0 || output.isBlank()) {
                return;
            }

            for (String line : output.split("\\R")) {
                String[] parts = line.split("\\|", 2);

                if (parts.length != 2) {
                    continue;
                }

                String containerId = parts[0].trim();
                String containerName = parts[1].trim();

                Path serverDir = getContainerServerDir(containerId);

                if (serverDir == null) {
                    continue;
                }

                serverDir = serverDir.toAbsolutePath().normalize();

                boolean singleton;
                String serverName = containerName;
                String templateName;

                if (serverDir.startsWith(INSTANCES_DIR.toAbsolutePath().normalize())) {
                    singleton = false;
                    templateName = detectTemplateNameFromInstanceName(serverName);
                } else if (serverDir.startsWith(TEMPLATES_DIR.toAbsolutePath().normalize())) {
                    singleton = true;
                    templateName = serverName;
                } else {
                    continue;
                }

                String port;

                try {
                    port = getDockerPort(containerId);
                } catch (Exception e) {
                    port = "unknown";
                }

                RUNNING_SERVERS.put(
                        serverName,
                        new RunningServer(
                                serverName,
                                templateName,
                                singleton,
                                containerId,
                                containerName,
                                serverDir,
                                port
                        )
                );

                if (!singleton) {
                    updateInstanceCounter(templateName, serverName);
                }
            }

            System.out.println("Found " + RUNNING_SERVERS.size() + " running server(s).");
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan running docker servers", e);
        }
    }

    private static Path getContainerServerDir(String containerId) {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "inspect",
                    "-f",
                    "{{range .Mounts}}{{if eq .Destination \"/server\"}}{{.Source}}{{end}}{{end}}",
                    containerId
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            int exitCode = process.waitFor();

            if (exitCode != 0 || output.isBlank()) {
                return null;
            }

            return Path.of(output);
        } catch (Exception e) {
            return null;
        }
    }

    private static String detectTemplateNameFromInstanceName(String instanceName) {
        int dashIndex = instanceName.lastIndexOf('-');

        if (dashIndex == -1) {
            return instanceName;
        }

        String suffix = instanceName.substring(dashIndex + 1);

        for (char c : suffix.toCharArray()) {
            if (!Character.isDigit(c)) {
                return instanceName;
            }
        }

        return instanceName.substring(0, dashIndex);
    }

    private static void updateInstanceCounter(String templateName, String instanceName) {
        int dashIndex = instanceName.lastIndexOf('-');

        if (dashIndex == -1) {
            return;
        }

        try {
            int id = Integer.parseInt(instanceName.substring(dashIndex + 1));
            INSTANCE_COUNTERS.merge(templateName, id, Math::max);
        } catch (NumberFormatException ignored) {
        }
    }

    private static Path createInstance(String templateName, String instanceName) {
        try {
            Path templateDir = TEMPLATES_DIR.resolve(templateName);
            Path instanceDir = INSTANCES_DIR.resolve(instanceName);

            if (!Files.exists(templateDir)) {
                throw new IllegalArgumentException("Template does not exist: " + templateName);
            }

            if (Files.exists(instanceDir)) {
                throw new IllegalStateException("Instance already exists: " + instanceName);
            }

            Files.createDirectories(instanceDir);

            try (var paths = Files.walk(templateDir)) {
                for (Path source : paths.toList()) {
                    Path target = instanceDir.resolve(templateDir.relativize(source).toString());

                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            return instanceDir;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create instance from template " + templateName, e);
        }
    }

    private static String getDockerPort(String containerId, int containerPort) {
        try {
            Process inspectProcess = new ProcessBuilder(
                    "docker",
                    "port",
                    containerId,
                    containerPort + "/tcp"
            ).redirectErrorStream(true).start();

            String output = new String(
                    inspectProcess.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            int exitCode = inspectProcess.waitFor();

            if (exitCode != 0 || output.isBlank()) {
                throw new RuntimeException("Could not read docker port: " + output);
            }

            int colonIndex = output.lastIndexOf(':');

            if (colonIndex == -1) {
                return output;
            }

            return output.substring(colonIndex + 1);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to inspect docker port", e);
        }
    }

    private static String getDockerPort(String containerId) {
        return getDockerPort(containerId, 25565);
    }

    private static String sanitizeDockerName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    public static Map<String, RunningServer> getRunningServers() {
        return Map.copyOf(RUNNING_SERVERS);
    }

    public record RunningServer(
            String name,
            String templateName,
            boolean singleton,
            String containerId,
            String containerName,
            Path serverDir,
            String port
    ) {
    }


    public static void printLogsSnapshot(RunningServer server) {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "logs",
                    "--tail",
                    "100",
                    server.containerName()
            ).redirectErrorStream(true).start();

            process.getInputStream().transferTo(System.out);

            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to print logs", e);
        }
    }
    public static void showLiveLogsBook(RunningServer server, Terminal terminal) {
        Process process = null;

        try {
            List<String> lines = Collections.synchronizedList(new ArrayList<>());

            Process snapshotProcess = new ProcessBuilder(
                    "docker",
                    "logs",
                    "--tail",
                    "200",
                    server.containerName()
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(snapshotProcess.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            snapshotProcess.waitFor();

            process = new ProcessBuilder(
                    "docker",
                    "logs",
                    "-f",
                    "--tail",
                    "0",
                    server.containerName()
            ).redirectErrorStream(true).start();

            Process finalProcess = process;

            Thread logThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8)
                )) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        lines.add(line);

                        while (lines.size() > 1000) {
                            lines.remove(0);
                        }
                    }
                } catch (Exception ignored) {
                }
            });

            logThread.setDaemon(true);
            logThread.start();

            Terminal.SignalHandler oldHandler = terminal.handle(Terminal.Signal.INT, signal -> {
                if (finalProcess.isAlive()) {
                    finalProcess.destroy();
                }
            });

            Attributes oldAttributes = terminal.enterRawMode();
            Display display = new Display(terminal, true);

            terminal.puts(org.jline.utils.InfoCmp.Capability.enter_ca_mode);
            terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
            terminal.flush();

            try {
                boolean running = true;

                while (running) {
                    display.update(buildLogScreen(terminal, server, lines), 0);

                    NonBlockingReader reader = terminal.reader();
                    int ch = reader.read(250);

                    if (ch == 'q' || ch == 'Q' || ch == 3) {
                        running = false;
                    }

                    if (!process.isAlive()) {
                        running = false;
                    }
                }
            } finally {
                display.update(List.of(), 0);

                terminal.puts(org.jline.utils.InfoCmp.Capability.exit_ca_mode);
                terminal.flush();

                terminal.setAttributes(oldAttributes);
                terminal.handle(Terminal.Signal.INT, oldHandler);

                if (process.isAlive()) {
                    process.destroy();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to show live logs", e);
        }
    }

    private static List<AttributedString> buildLogScreen(
            Terminal terminal,
            RunningServer server,
            List<String> lines
    ) {
        int width = Math.max(20, terminal.getWidth());
        int height = Math.max(8, terminal.getHeight());

        List<AttributedString> screen = new ArrayList<>();

        screen.add(new AttributedString("═".repeat(width)));
        screen.add(new AttributedString(cutLine(
                " Logs: " + server.name() + " | Port: " + server.port() + " | Container: " + server.containerName(),
                width
        )));
        screen.add(new AttributedString("═".repeat(width)));

        int logHeight = height - 5;

        List<String> copy;

        synchronized (lines) {
            copy = new ArrayList<>(lines);
        }

        int start = Math.max(0, copy.size() - logHeight);

        for (int i = start; i < copy.size(); i++) {
            screen.add(new AttributedString(cutLine(copy.get(i), width)));
        }

        while (screen.size() < height - 2) {
            screen.add(new AttributedString(""));
        }

        screen.add(new AttributedString("═".repeat(width)));
        screen.add(new AttributedString(cutLine(" Press q to quit | Ctrl+C to exit ", width)));

        return screen;
    }

    private static String cutLine(String text, int width) {
        if (text == null) {
            return "";
        }

        text = text.replace("\t", "    ");

        if (text.length() <= width) {
            return text;
        }

        if (width <= 3) {
            return text.substring(0, width);
        }

        return text.substring(0, width - 3) + "...";
    }
}