package net.villagerzock.cloudcore.core.server;

import lombok.Getter;
import lombok.Setter;
import net.villagerzock.cloudcore.core.Main;
import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.config.NoClassTagRepresenter;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.*;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.NonBlockingReader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static net.villagerzock.cloudcore.core.Main.SERVER_TO_VERSION_TO_URL_MAP;
import static net.villagerzock.cloudcore.core.Main.terminal;

public class ServerManager {

    private static final String VELOCITY_CONFIG = """
config-version = "2.8"
bind = "0.0.0.0:%d"
motd = \"""%s\"""
show-max-players = %d
online-mode = %s
force-key-authentication = %s
prevent-client-proxy-connections = %s
player-info-forwarding-mode = "%s"
forwarding-secret-file = "forwarding.secret"
announce-forge = %s
kick-existing-players = %s
ping-passthrough = "%s"
enable-player-address-logging = %s

[advanced]
	compression-threshold = 256
	compression-level = -1
	login-ratelimit = 3000
	connection-timeout = 5000
	read-timeout = 30000
	haproxy-protocol = false
	tcp-fast-open = false
	bungee-plugin-message-channel = true
	show-ping-requests = false
	failover-on-unexpected-server-disconnect = true
	announce-proxy-commands = true
	log-command-executions = false
	log-player-connections = true
	accepts-transfers = false

[query]
	enabled = false
	port = %d
	map = "Velocity"
	show-plugins = false

[packet-limiter]
	interval = 7
	packets-per-second = -1
	bytes-per-second = -1
	decompressed-bytes-per-second = 5242880

[servers]
    
try = [
]

[forced-hosts]
""";

    private static final String SERVER_PROPERTIES = """
            #Minecraft server properties
            accepts-transfers=false
            allow-flight=false
            broadcast-console-to-ops=true
            broadcast-rcon-to-ops=true
            bug-report-link=
            debug=false
            difficulty=easy
            enable-code-of-conduct=false
            enable-jmx-monitoring=false
            enable-query=false
            enable-rcon=false
            enable-status=true
            enforce-secure-profile=true
            enforce-whitelist=false
            entity-broadcast-range-percentage=100
            force-gamemode=false
            function-permission-level=2
            gamemode=survival
            generate-structures=true
            generator-settings={}
            hardcore=false
            hide-online-players=false
            initial-disabled-packs=
            initial-enabled-packs=vanilla
            level-name=world
            level-seed=
            level-type=minecraft\\:normal
            log-ips=true
            management-server-allowed-origins=
            management-server-enabled=false
            management-server-host=localhost
            management-server-port=0
            management-server-secret=jO2PiqtmY11ZfbTOfCSwRd3CGXdnCChBv8hXq5w2
            management-server-tls-enabled=true
            management-server-tls-keystore=
            management-server-tls-keystore-password=
            max-chained-neighbor-updates=1000000
            max-players=20
            max-tick-time=60000
            max-world-size=29999984
            motd=A Minecraft Server
            network-compression-threshold=256
            online-mode=false
            op-permission-level=4
            pause-when-empty-seconds=-1
            player-idle-timeout=0
            prevent-proxy-connections=false
            query.port=25565
            rate-limit=0
            rcon.password=
            rcon.port=25575
            region-file-compression=deflate
            require-resource-pack=false
            resource-pack=
            resource-pack-id=
            resource-pack-prompt=
            resource-pack-sha1=
            server-ip=
            server-port=25565
            simulation-distance=10
            spawn-protection=0
            status-heartbeat-interval=0
            sync-chunk-writes=true
            text-filtering-config=
            text-filtering-version=0
            use-native-transport=true
            view-distance=10
            white-list=false
            """;


    public static final CompletableFuture<Void> WAIT_FOR_SPRING_START = new CompletableFuture<>();
    public static final Path BASE_DIR = Path.of(System.getProperty("user.home"), ".cloudcore");
    private static final Path TEMPLATES_DIR = BASE_DIR.resolve("templates");
    private static final Path INSTANCES_DIR = BASE_DIR.resolve("instances");

    private static final String DOCKER_NETWORK = "cloudcore";

    private static final List<String> DOCKER_BASE_ARGS = new ArrayList<>(List.of(
            "docker",
            "run",
            "-d",
            "--user",
            "%s:%s",
            "--add-host",
            "mariadb:host-gateway",
            "--network",
            DOCKER_NETWORK
    ));

    static {
        String uid = getCommandOutput("id", "-u");
        String gid = getCommandOutput("id", "-g");

        DOCKER_BASE_ARGS.set(4,DOCKER_BASE_ARGS.get(4).formatted(uid,gid));
    }


    // ======== PROXY ========

    private static RunningProxy RUNNING_PROXY;

    public static RunningProxy getRunningProxy() {
        return RUNNING_PROXY;
    }

    public static void init() {
        ensureDockerNetwork();
        scanForRunningProxy(true);
        scanForRunningServers();
        Config.LobbyConfig lobbyConfig = Config.getInstance().getLobby();
        if (!Files.exists(BASE_DIR.resolve("templates").resolve(lobbyConfig.server))){
            String server = launchCreationWizard(lobbyConfig.server);
            if (lobbyConfig.server == null){
                lobbyConfig.server = server;
                Config.getInstance().save();
            }
        }
        if (!WAIT_FOR_SPRING_START.isDone()){
            System.out.println("Waiting for Velocity");
            try {
                WAIT_FOR_SPRING_START.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Velocity Booted Up!");
        }


        System.out.println("Launching Lobby Servers");

        int initialAmount = lobbyConfig.getMode() == Config.LobbyConfig.Mode.STATIC ? lobbyConfig.getAmount() : lobbyConfig.getFrom();
        int alreadyThere = 0;
        for (RunningServer key : RUNNING_SERVERS.values()){
            if (key.templateName().equals(lobbyConfig.getServer())){
                alreadyThere++;
            }
        }
        System.out.printf("Need to Start %d more Servers.%n", initialAmount-alreadyThere);
        if (initialAmount-alreadyThere > 0){
            for (int i = 0; i<initialAmount-alreadyThere; i++){
                try {
                    ServerLaunchResult result = launchServer(lobbyConfig.getServer(),false).get();
                    System.out.println(result.message);
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println("Failed to launch lobby server:");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }


        ProxyServerManager.getInstance().configure(new ConfigDto(
                lobbyConfig.getServer(),
                null,
                Config.getInstance().getProxy().getMaintenanceMotd()
        ));
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
            Path proxyDir = BASE_DIR.resolve("proxy");
            Path proxyConfigFile = proxyDir.resolve(".cloudcore.conf");

            if (Files.exists(proxyDir)) {
                if (!Files.isDirectory(proxyDir)) {
                    throw new RuntimeException(proxyDir + " exists but is not a directory");
                }

                if (!Files.exists(proxyConfigFile)) {
                    throw new RuntimeException("Proxy folder exists but .cloudcore.conf is missing");
                }

                Map<String, Object> config = loadProxyLaunchConfig(proxyConfigFile);

                String proxyPort = String.valueOf(config.getOrDefault("proxyPort", "25565"));
                String memory = String.valueOf(config.getOrDefault("memory", "512M"));
                String velocityVersion = String.valueOf(config.getOrDefault("velocityVersion", "latest"));

                setupVelocityProxy(proxyPort, memory, velocityVersion);
                Thread.sleep(1000);
                scanForRunningProxy(false);
                return;
            }

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

            Files.createDirectories(proxyDir);
            writeProxyLaunchConfig(proxyConfigFile, proxyPort, memory, velocityVersion);

            setupVelocityProxy(proxyPort, memory, velocityVersion);
            Thread.sleep(1000);
            scanForRunningProxy(false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch proxy wizard", e);
        }
    }

    private static void writeProxyLaunchConfig(
            Path configFile,
            String proxyPort,
            String memory,
            String velocityVersion
    ) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("proxyPort", proxyPort);
        data.put("memory", memory);
        data.put("velocityVersion", velocityVersion);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);

        Files.writeString(
                configFile,
                yaml.dump(data),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadProxyLaunchConfig(Path configFile) throws IOException {
        Yaml yaml = new Yaml();

        try (Reader reader = Files.newBufferedReader(configFile)) {
            Object loaded = yaml.load(reader);

            if (!(loaded instanceof Map<?, ?> map)) {
                throw new RuntimeException(".cloudcore.conf is invalid");
            }

            return (Map<String, Object>) map;
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
        Config.ProxyConfig proxyConfig = Config.getInstance().getProxy();
        String config = VELOCITY_CONFIG.formatted(
                25577,
                proxyConfig.getMotd(),
                500,
                proxyConfig.isOnlineMode(),
                true,
                false,
                "modern",
                false,
                false,
                "DISABLED",
                true,
                25577
        );

        Files.writeString(
                serverDir.resolve("velocity.toml"),
                config,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

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

                "-e",
                "CLOUDCORE_DB_HOST=mariadb",
                "-e",
                "CLOUDCORE_DB_PORT=" + Config.getInstance().getMariadb().getPort(),
                "-e",
                "CLOUDCORE_DB_NAME=cloudcore",
                "-e",
                "CLOUDCORE_DB_USER=" + Config.getInstance().getMariadb().getUser(),
                "-e",
                "CLOUDCORE_DB_PASSWORD=" + Config.getInstance().getMariadb().getPassword(),

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
    private static void scanForRunningProxy(boolean skipWait) {
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
            }else if (skipWait){
                WAIT_FOR_SPRING_START.complete(null);
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

            System.out.println("Found running proxy: " + containerName + " on port " + port + " with API Port: " + RUNNING_PROXY.apiPort);
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan running proxy", e);
        }
    }


    // ======== SERVERS ========

    private static final Map<String, Integer> INSTANCE_COUNTERS = new HashMap<>();
    private static final Map<String, RunningServer> RUNNING_SERVERS = new HashMap<>();

    public static String createServer(ServerType serverType, String url, String name, String memory, String worldType, String superflatType, String seed) {
        try {
            Path serverDir = TEMPLATES_DIR.resolve(name);

            Files.createDirectories(serverDir);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

            Representer representer = new NoClassTagRepresenter(options);

            Yaml yaml = new Yaml(representer,options);

            ServerConfig cloudCoreConfig = new ServerConfig();
            cloudCoreConfig.type = serverType;
            cloudCoreConfig.memory = parseMemory(memory);

            try (Writer writer = Files.newBufferedWriter(
                    serverDir.resolve(".cloudcore.conf"),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                yaml.dump(cloudCoreConfig, writer);
            }

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

            if (Files.exists(serverDir.resolve("server.properties"))){
                Properties properties = new Properties();

                try (InputStream in = Files.newInputStream(serverDir.resolve("server.properties"))) {
                    properties.load(in);
                }

                properties.setProperty("online-mode","false");
                try (Writer writer = Files.newBufferedWriter(serverDir.resolve("server.properties"))) {
                    properties.store(writer, null);
                }
            }else {
                Files.writeString(
                        serverDir.resolve("server.properties"),
                        SERVER_PROPERTIES,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
            if (seed.isBlank()){
                long seedNr = new Random().nextLong();
                seed = Long.toString(seedNr);
            }
            WorldType.valueOf(worldType.toUpperCase(Locale.ROOT)).create(serverDir.resolve("world"),superflatType,seed);

            System.out.println("Created server template at: " + serverDir.toAbsolutePath());

            return name;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server template", e);
        }
    }

    public static long parseMemory(String memory) {
        if (memory == null || memory.isBlank()) {
            throw new IllegalArgumentException("Memory must not be empty");
        }

        memory = memory.trim().toUpperCase(Locale.ROOT);

        long multiplier = 1;

        if (memory.endsWith("K")) {
            multiplier = 1024L;
            memory = memory.substring(0, memory.length() - 1);
        } else if (memory.endsWith("M")) {
            multiplier = 1024L * 1024L;
            memory = memory.substring(0, memory.length() - 1);
        } else if (memory.endsWith("G")) {
            multiplier = 1024L * 1024L * 1024L;
            memory = memory.substring(0, memory.length() - 1);
        }

        return Long.parseLong(memory) * multiplier;
    }

    public static class ServerConfig{
        @Getter
        @Setter
        public ServerType type;

        @Getter
        @Setter
        public long memory;
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
                    return new ServerLaunchResult(ServerState.FAILED, "server.jar not found in " + serverDir, null,CompletableFuture.failedFuture(new IllegalStateException("server.jar not found in " + serverDir)));
                }

                Path secretPath = BASE_DIR.resolve("proxy/server/forwarding.secret");
                String secret = Files.readString(secretPath).trim();

                ServerConfig config = readServerConfig(serverDir);
                config.type.setupForVelocity(serverDir,secret);

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
                        "eclipse-temurin:25",
                        "java",
                        "-Xms" + config.memory,
                        "-Xmx" + config.memory,
                        "-jar",
                        "server.jar",
                        "nogui"
                ));

                Process runProcess = new ProcessBuilder(command)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();

                String containerId = new String(
                        runProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                ).trim();

                int exitCode = runProcess.waitFor();

                if (exitCode != 0) {
                    return new ServerLaunchResult(ServerState.FAILED, "Docker failed", null, CompletableFuture.failedFuture(new IllegalStateException("Docker failed")));
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

                        CompletableFuture<RunningServer> future = registerServerWhenStarted(
                                runningServer.name(),
                                runningServer.templateName(),
                                runningServer.containerName(),
                                runningServer
                        );

                        return new ServerLaunchResult(
                                ServerState.RUNNING,
                                "Started Successfully on port " + port,
                                runningServer,
                                future
                        );
                    }

                    if (state == ServerState.CRASHED || state == ServerState.STOPPED) {
                        return new ServerLaunchResult(
                                ServerState.CRASHED,
                                "Crashed :(",
                                runningServer,
                                CompletableFuture.failedFuture(new IllegalStateException("Crashed :("))
                        );
                    }

                    Thread.sleep(1000);
                }

                return new ServerLaunchResult(
                        ServerState.TIMEOUT,
                        "Startup Timeout",
                        runningServer,
                        CompletableFuture.failedFuture(new IllegalStateException("Startup Timeout"))
                );
            } catch (Exception e) {
                return new ServerLaunchResult(
                        ServerState.FAILED,
                        "Failed: " + e.getMessage(),
                        null,
                        CompletableFuture.failedFuture(e)
                );
            }
        });
    }

    private static CompletableFuture<RunningServer> registerServerWhenStarted(String name, String base, String host, RunningServer runningServer) {
        return CompletableFuture.supplyAsync(() -> {

            long timeoutAt = System.currentTimeMillis() + 120_000;

            while (System.currentTimeMillis() < timeoutAt) {
                if (runningServer.isCrashed()) {
                    throw new IllegalStateException("Server crashed while starting: " + name);
                }

                if (!runningServer.isRunning()) {
                    throw new IllegalStateException("Server stopped while starting: " + name);
                }

                if (runningServer.isReady()) {
                    ProxyServerManager.getInstance().register(name, base, host, 25565);
                    return runningServer;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for server start: " + name, e);
                }
            }

            throw new IllegalStateException("Server did not become ready in time: " + name);
        });
    }

    private static ServerConfig readServerConfig(Path serverDir) {
        try {
            Yaml yaml = new Yaml();

            try (InputStream in = Files.newInputStream(serverDir.resolve(".cloudcore.conf"))) {
                return yaml.loadAs(in, ServerConfig.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to read server config", e);
        }
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
            RunningServer server,
            CompletableFuture<RunningServer> started
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

    private static String getDockerPort(String containerName, int containerPort) {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "port",
                    containerName,
                    String.valueOf(containerPort)
            )
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            if (exitCode != 0) {

                System.out.println("Container: " + containerName);
                System.out.println("Port: " + containerPort);
                System.out.println("Output: " + output);
                System.out.println("Exit code: " + process.waitFor());
                throw new RuntimeException("Could not read docker port: " + output);
            }

            if (output.isBlank()) {
                throw new RuntimeException("Docker port output is empty for container " + containerName);
            }

            // Beispiel: 0.0.0.0:49154 oder :::49154
            String[] parts = output.split(":");
            return parts[parts.length - 1];
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect docker port", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while inspecting docker port", e);
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
        public boolean isCrashed() {
            try {
                Process process = new ProcessBuilder(
                        "docker",
                        "inspect",
                        "-f",
                        "{{.State.ExitCode}}",
                        containerId
                ).start();

                String result = new String(process.getInputStream().readAllBytes()).trim();

                if (result.isBlank()) {
                    return false;
                }

                return Integer.parseInt(result) != 0;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean isRunning() {
            try {
                Process process = new ProcessBuilder(
                        "docker",
                        "inspect",
                        "-f",
                        "{{.State.Running}}",
                        containerId
                ).start();

                String result = new String(process.getInputStream().readAllBytes()).trim();

                return Boolean.parseBoolean(result);
            } catch (Exception e) {
                return false;
            }
        }

        public boolean isReady() {
            try {
                Process process = new ProcessBuilder(
                        "docker",
                        "logs",
                        containerId
                ).start();

                String logs = new String(process.getInputStream().readAllBytes());

                return logs.contains("Done (");
            } catch (Exception e) {
                return false;
            }
        }
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

    private static String getCommandOutput(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (process.waitFor() != 0) {
                throw new RuntimeException("Command failed: " + String.join(" ", command) + " -> " + output);
            }

            return output;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run command: " + String.join(" ", command), e);
        }
    }

    public static String launchCreationWizard(String initialName) {
        try {

            ConsolePrompt prompt = new ConsolePrompt(terminal);

            PromptBuilder builder = prompt.getPromptBuilder();

            if (initialName == null) {
                builder.createInputPrompt()
                        .name("name")
                        .message("How do you want to call the Server?")
                        .addPrompt();
            } else {
                builder.createText()
                        .addLine("Name is: " + initialName)
                        .addPrompt();
            }

            builder.createListPrompt()
                    .name("serverType")
                    .message("What server type?")
                    .newItem("paper").text("Paper").add()
                    .newItem("folia").text("Folia").add()
                    .newItem("vanilla").text("Vanilla (Unsafe)").add()
                    .addPrompt();

            builder.createInputPrompt()
                    .name("memory")
                    .message("How much memory should the server use? Example: 1G / 2048M")
                    .defaultValue("1G")
                    .addPrompt();

            builder.createListPrompt()
                    .name("worldType")
                    .message("What world type?")
                    .newItem("default").text("Default").add()
                    .newItem("superflat").text("Superflat").add()
                    .newItem("large_biomes").text("Large Biomes").add()
                    .newItem("amplified").text("Amplified").add()
                    .addPrompt();

            builder.createInputPrompt()
                    .name("seed")
                    .message("World seed, leave empty for random")
                    .addPrompt();

            Map<String, ? extends PromptResultItemIF> result = prompt.prompt(builder.build());

            ListResult serverType = (ListResult) result.get("serverType");
            InputResult name = initialName == null ? (InputResult) result.get("name") : null;
            InputResult memory = (InputResult) result.get("memory");
            ListResult worldType = (ListResult) result.get("worldType");
            InputResult seedInput = (InputResult) result.get("seed");

            String superflatType = null;

            if (worldType.getResult().equals("superflat")) {
                prompt = new ConsolePrompt(terminal);
                builder = prompt.getPromptBuilder();

                builder.createListPrompt()
                        .name("superflatType")
                        .message("What superflat type?")
                        .newItem("default").text("Default Superflat").add()
                        .newItem("the_void").text("The Void").add()
                        .newItem("redstone_ready").text("Redstone Ready").add()
                        .newItem("water_world").text("Water World").add()
                        .addPrompt();

                result = prompt.prompt(builder.build());

                ListResult superflatTypeResult = (ListResult) result.get("superflatType");
                superflatType = superflatTypeResult.getResult();
            }

            String seed = seedInput.getResult();

            if (seed == null || seed.isBlank()) {
                seed = String.valueOf(new Random().nextLong());
            }

            prompt = new ConsolePrompt(terminal);
            builder = prompt.getPromptBuilder();

            ListPromptBuilder versionBuilder = builder.createListPrompt()
                    .name("version")
                    .message("What version do you Want?");

            for (String version : SERVER_TO_VERSION_TO_URL_MAP.get(serverType.getResult()).keySet()) {
                versionBuilder.newItem(version).text(version).add();
            }
            versionBuilder.addPrompt();

            builder.createConfirmPromp()
                    .name("eula")
                    .message("Do you accept the Mojang EULA? (https://www.minecraft.net/en-us/eula)")
                    .addPrompt();

            result = prompt.prompt(builder.build());

            ListResult version = (ListResult) result.get("version");
            ConfirmResult eula = (ConfirmResult) result.get("eula");

            if (eula.getConfirmed() == ConfirmChoice.ConfirmationValue.NO) {
                System.out.println("Eula not accepted. Abort!");
                return null;
            }

            return createServer(
                    ServerType.valueOf(serverType.getResult().toUpperCase(Locale.ROOT)),
                    SERVER_TO_VERSION_TO_URL_MAP.get(serverType.getResult()).get(version.getResult()),
                    initialName == null ? name.getResult() : initialName,
                    memory.getResult(),
                    worldType.getResult(),
                    superflatType,
                    seed
            );

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void shutdownServer(RunningServer server) {
        try {
            ProxyServerManager.getInstance().unregister(server.name(),null);

            Process process = new ProcessBuilder(
                    "docker",
                    "rm",
                    "-f",
                    server.containerName()
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (process.waitFor() != 0) {
                throw new RuntimeException("Failed to shutdown server " + server.name() + ": " + output);
            }


            if (!server.singleton){
                Main.deleteRecursive(server.serverDir);
            }
            RUNNING_SERVERS.remove(server.name());
            System.out.println("Shutdown server: " + server.name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to shutdown server " + server.name, e);
        }
    }

    public static void shutdownProxy() {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "rm",
                    "-f",
                    "cloudcore-proxy"
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (process.waitFor() != 0 && !output.toLowerCase(Locale.ROOT).contains("no such container")) {
                throw new RuntimeException("Failed to shutdown proxy: " + output);
            }

            RUNNING_PROXY = null;
            System.out.println("Shutdown proxy.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to shutdown proxy", e);
        }
    }

    public static void removeNetwork() {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "network",
                    "rm",
                    DOCKER_NETWORK
            ).redirectErrorStream(true).start();

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (process.waitFor() != 0 && !output.toLowerCase(Locale.ROOT).contains("no such network")) {
                throw new RuntimeException("Failed to remove docker network: " + output);
            }

            System.out.println("Removed docker network: " + DOCKER_NETWORK);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove docker network", e);
        }
    }

    public static void shutdown() {
        for (RunningServer server : new ArrayList<>(RUNNING_SERVERS.values())) {
            try {
                shutdownServer(server);
            }catch (Throwable ignored){

            }
        }

        try {
            shutdownProxy();
        }catch (Throwable ignored){

        }
        removeNetwork();
    }
}