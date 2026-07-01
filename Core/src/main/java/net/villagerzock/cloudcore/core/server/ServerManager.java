package net.villagerzock.cloudcore.core.server;

import lombok.Getter;
import lombok.Setter;
import net.villagerzock.cloudcore.core.Main;
import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.config.NoClassTagRepresenter;
import net.villagerzock.cloudcore.core.server.dto.ConfigDto;
import net.villagerzock.cloudcore.core.console.ConsolePrompts;
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
import java.util.concurrent.*;

import static net.villagerzock.cloudcore.core.Main.SERVER_TO_VERSION_TO_URL_MAP;

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
            "-i",
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

        int userArgumentIndex = DOCKER_BASE_ARGS.indexOf("%s:%s");
        DOCKER_BASE_ARGS.set(userArgumentIndex, DOCKER_BASE_ARGS.get(userArgumentIndex).formatted(uid, gid));
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
        if (lobbyConfig.server == null || !Files.exists(BASE_DIR.resolve("templates").resolve(lobbyConfig.server))){
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
                matchmakingConfigurations(),
                Config.getInstance().getProxy().getMaintenanceMotd(),
                Config.getInstance().getProxy().getBanMessage()
        ));
    }

    private static Map<String, ConfigDto.MatchmakingServerConfigDto> matchmakingConfigurations() {
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

            String proxyPort = ConsolePrompts.input("Proxy Setup", "Proxy port", "25565", false);
            String memory = ConsolePrompts.input("Proxy Setup", "Proxy memory, example: 512M / 1G", "512M", false);
            String velocityVersion = ConsolePrompts.input("Proxy Setup", "Velocity version", "latest", false);

            Files.createDirectories(proxyDir);
            writeProxyLaunchConfig(proxyConfigFile, proxyPort, memory, velocityVersion);

            setupVelocityProxy(proxyPort, memory, velocityVersion);
            Thread.sleep(1000);
            scanForRunningProxy(false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch proxy wizard", e);
        }
    }

    public static void startProxy() {
        launchProxyWizard();
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
                "SPRING_OUTPUT_ANSI_ENABLED=always",
                "-e",
                "LOGGING_PATTERN_CONSOLE=[%clr(%d{HH:mm:ss}){faint} %clr(%-5level)] [%clr(%logger){cyan}]: %msg%n",

                "-e",
                "CLOUDCORE_DB_HOST=mariadb",
                "-e",
                "CLOUDCORE_DB_PORT=" + Config.getInstance().getMariadb().getPort(),
                "-e",
                "CLOUDCORE_DB_NAME=" + Config.getInstance().getMariadb().getDatabase(),
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

    public static String createServer(ServerType serverType, String url, String version, String name, String memory, String worldType, String superflatType, String seed) {
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
            cloudCoreConfig.version = version;

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

        @Getter
        @Setter
        public String version;
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
                RunningServer runningServer = startDockerServer(name, singleton, instanceName, serverDir, containerName, config);
                watchServerStartup(name, singleton, instanceName, serverDir, containerName, config, runningServer, 1);

                return new ServerLaunchResult(
                        ServerState.STARTING,
                        "Container started on port " + runningServer.port(),
                        runningServer,
                        runningServer.future()
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

    private static void watchServerStartup(
            String templateName,
            boolean singleton,
            String instanceName,
            Path serverDir,
            String containerName,
            ServerConfig config,
            RunningServer runningServer,
            int attempt
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                waitForServerReady(runningServer);
                ProxyServerManager.getInstance().register(
                        runningServer.name(),
                        runningServer.templateName(),
                        runningServer.containerName(),
                        25565
                );
                runningServer.future().complete(runningServer);
            } catch (IllegalStateException exception) {
                String logs = dockerLogs(runningServer.containerId(), 200);

                if (attempt == 1 && isMojangHashFailure(exception, logs)) {
                    retryAfterMojangCacheFailure(
                            templateName,
                            singleton,
                            instanceName,
                            serverDir,
                            containerName,
                            config,
                            runningServer);
                    return;
                }

                runningServer.future().completeExceptionally(exception);
            } catch (Exception exception) {
                runningServer.future().completeExceptionally(exception);
            }
        });
    }

    private static void retryAfterMojangCacheFailure(
            String templateName,
            boolean singleton,
            String instanceName,
            Path serverDir,
            String containerName,
            ServerConfig config,
            RunningServer failedServer
    ) {
        try {
            System.out.println("Detected corrupt Mojang download cache. Cleaning cache and retrying...");
            removeContainer(failedServer.containerName());
            RUNNING_SERVERS.remove(failedServer.name());
            deleteMojangDownloadCache(serverDir);

            Path retryServerDir = serverDir;
            if (!singleton) {
                deleteMojangDownloadCache(TEMPLATES_DIR.resolve(templateName));
                Main.deleteRecursive(serverDir);
                retryServerDir = createInstance(templateName, instanceName);
            }

            RunningServer retryServer = startDockerServer(
                    templateName,
                    singleton,
                    instanceName,
                    retryServerDir,
                    containerName,
                    config);
            retryServer.future().whenComplete((server, throwable) -> {
                if (throwable == null) {
                    failedServer.future().complete(server);
                } else {
                    failedServer.future().completeExceptionally(throwable);
                }
            });
            watchServerStartup(templateName, singleton, instanceName, retryServerDir, containerName, config, retryServer, 2);
        } catch (Exception retryException) {
            failedServer.future().completeExceptionally(retryException);
        }
    }

    private static RunningServer startDockerServer(
            String templateName,
            boolean singleton,
            String instanceName,
            Path serverDir,
            String containerName,
            ServerConfig config
    ) throws IOException, InterruptedException {
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
            throw new IllegalStateException("Docker failed");
        }

        RunningServer runningServer = new RunningServer(
                instanceName,
                templateName,
                singleton,
                containerId,
                containerName,
                serverDir,
                getDockerPort(containerId),
                new CompletableFuture<>()
        );

        RUNNING_SERVERS.put(instanceName, runningServer);
        return runningServer;
    }

    private static void waitForServerReady(RunningServer runningServer) throws InterruptedException {
        long timeoutAt = System.currentTimeMillis() + 120_000;

        while (System.currentTimeMillis() < timeoutAt) {
            ServerState state = getDockerState(runningServer.containerId());

            if (state == ServerState.CRASHED || state == ServerState.STOPPED || state == ServerState.FAILED) {
                String logs = dockerLogs(runningServer.containerId(), 200);
                throw new IllegalStateException("Server crashed while starting: " + firstUsefulLogLine(logs));
            }

            if (runningServer.isReady()) {
                return;
            }

            Thread.sleep(1000);
        }

        throw new IllegalStateException("Server did not become ready in time");
    }

    private static CompletableFuture<RunningServer> registerServerWhenStarted(String name, String base, String host, RunningServer runningServer) {
        return runningServer.future.completeAsync(() -> {

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


                if (Thread.currentThread().isInterrupted()){
                    throw new CancellationException();
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

    public static ServerConfig readServerConfig(Path serverDir) {
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
                                port,
                                new CompletableFuture<>()
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

    private static String dockerLogs(String containerId, int tail) {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "logs",
                    "--tail",
                    String.valueOf(tail),
                    containerId
            ).redirectErrorStream(true).start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output;
        } catch (Exception e) {
            return "";
        }
    }

    private static void removeContainer(String containerName) {
        try {
            Process process = new ProcessBuilder(
                    "docker",
                    "rm",
                    "-f",
                    containerName
            ).redirectErrorStream(true).start();

            process.getInputStream().readAllBytes();
            process.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static boolean isMojangHashFailure(Throwable error, String logs) {
        String text = (String.valueOf(error.getMessage()) + "\n" + logs).toLowerCase(Locale.ROOT);

        return text.contains("hash check failed")
                && (text.contains("mojang_") || text.contains("downloaded file"));
    }

    private static void deleteMojangDownloadCache(Path serverDir) {
        if (!Files.exists(serverDir)) {
            return;
        }

        try (var files = Files.walk(serverDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("mojang_"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            System.out.println("Deleted corrupt cache file: " + path.getFileName());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw new RuntimeException("Failed to clean Mojang download cache", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan Mojang download cache", e);
        }
    }

    private static String firstUsefulLogLine(String logs) {
        if (logs == null || logs.isBlank()) {
            return "no logs available";
        }

        return logs.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.toLowerCase(Locale.ROOT).contains("exception")
                        || line.toLowerCase(Locale.ROOT).contains("error")
                        || line.toLowerCase(Locale.ROOT).contains("failed"))
                .reduce((first, second) -> second)
                .orElseGet(() -> logs.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .reduce((first, second) -> second)
                        .orElse("no useful logs available"));
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
            String port,
            CompletableFuture<RunningServer> future
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
                        "--tail",
                        "100",
                        containerId
                ).start();

                String logs = new String(process.getInputStream().readAllBytes());

                return logs.contains("Done (");
            } catch (Exception e) {
                return false;
            }
        }
        public boolean canConnect(){
            return future.state() == Future.State.SUCCESS;
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

    public static String selectRunningServer() {
        Map<String, RunningServer> servers = getRunningServers();

        if (servers.isEmpty()) {
            System.out.println("No servers running.");
            return null;
        }

        try {
            List<ConsolePrompts.Option> options = servers.values().stream()
                    .sorted(Comparator.comparing(RunningServer::name))
                    .map(server -> new ConsolePrompts.Option(
                            server.name(),
                            server.name() + " | template=" + server.templateName() + " | port=" + server.port()
                    ))
                    .toList();

            return ConsolePrompts.select("Server Logs", "Select server", options);
        } catch (Exception e) {
            throw new RuntimeException("Failed to select server", e);
        }
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
            String name = initialName == null
                    ? ConsolePrompts.input("Create Server", "How do you want to call the Server?", "", false)
                    : initialName;
            String serverType = ConsolePrompts.select(
                    "Create Server",
                    "What server type?",
                    List.of(
                            new ConsolePrompts.Option("paper", "Paper"),
                            new ConsolePrompts.Option("folia", "Folia"),
                            new ConsolePrompts.Option("vanilla", "Vanilla (Unsafe)")
                    )
            );
            String memory = ConsolePrompts.input(
                    "Create Server",
                    "How much memory should the server use? Example: 1G / 2048M",
                    "1G",
                    false
            );
            String worldType = ConsolePrompts.select(
                    "Create Server",
                    "What world type?",
                    List.of(
                            new ConsolePrompts.Option("default", "Default"),
                            new ConsolePrompts.Option("superflat", "Superflat"),
                            new ConsolePrompts.Option("large_biomes", "Large Biomes"),
                            new ConsolePrompts.Option("amplified", "Amplified")
                    )
            );
            String seed = ConsolePrompts.input("Create Server", "World seed, leave empty for random", "", false);

            String superflatType = null;

            if (worldType.equals("superflat")) {
                superflatType = ConsolePrompts.select(
                        "Create Server",
                        "What superflat type?",
                        List.of(
                                new ConsolePrompts.Option("default", "Default Superflat"),
                                new ConsolePrompts.Option("the_void", "The Void"),
                                new ConsolePrompts.Option("redstone_ready", "Redstone Ready"),
                                new ConsolePrompts.Option("water_world", "Water World")
                        )
                );
            }

            if (seed == null || seed.isBlank()) {
                seed = String.valueOf(new Random().nextLong());
            }

            Map<String, String> versions = SERVER_TO_VERSION_TO_URL_MAP.get(serverType);
            String defaultVersion = versions.keySet().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No versions loaded for " + serverType));
            String version;
            while (true) {
                version = ConsolePrompts.input(
                        "Create Server",
                        "What version do you want?",
                        defaultVersion,
                        false
                ).trim();

                if (versions.containsKey(version)) {
                    break;
                }

                System.out.println("Unknown version: " + version);
                System.out.println("Examples: " + versions.keySet().stream().limit(8).toList());
            }
            boolean eula = ConsolePrompts.confirm(
                    "Create Server",
                    "Do you accept the Mojang EULA? (https://www.minecraft.net/en-us/eula)"
            );

            if (!eula) {
                System.out.println("Eula not accepted. Abort!");
                return null;
            }

            return createServer(
                    ServerType.valueOf(serverType.toUpperCase(Locale.ROOT)),
                    versions.get(version),
                    version,
                    name,
                    memory,
                    worldType,
                    superflatType,
                    seed
            );

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void shutdownServer(RunningServer server) {
        try {
            if (server.canConnect()){
                ProxyServerManager.getInstance().unregister(server.name(),null);
            }else {
                server.future.cancel(true);
            }

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
        ArrayList<RunningServer> servers = new ArrayList<>(RUNNING_SERVERS.values());
        for (RunningServer server : servers) {
            try {
                shutdownServer(server);
            }catch (Throwable t){
                t.printStackTrace();
            }
        }

        try {
            shutdownProxy();
        }catch (Throwable ignored){

        }
        removeNetwork();
    }
}
