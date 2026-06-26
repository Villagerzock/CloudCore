package net.villagerzock.cloudcore.core;

import net.villagerzock.cloudcore.core.api.ApiServer;
import net.villagerzock.cloudcore.core.api.CoreHandshakeProviderImpl;
import net.villagerzock.cloudcore.core.api.CoreLogForwarder;
import net.villagerzock.cloudcore.core.api.CoreMetricForwarder;
import net.villagerzock.cloudcore.core.command.Suggests;
import net.villagerzock.cloudcore.core.command.providers.RunningServerProvider;
import net.villagerzock.cloudcore.core.command.providers.ServerTemplateProvider;
import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.server.ProxyServerManager;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.corehandshake.CoreHandshakeInitializer;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static picocli.CommandLine.*;

public class Main {
    public static final String logo = """
            \u001B[36m______________            _________\u001B[33m________                 \s
            \u001B[36m__  ____/__  /_________  _______  /\u001B[33m_  ____/_________________\s
            \u001B[36m_  /    __  /_  __ \\  / / /  __  /\u001B[33m_  /    _  __ \\_  ___/  _ \\
            \u001B[36m/ /___  _  / / /_/ / /_/ // /_/ /\u001B[33m / /___  / /_/ /  /   /  __/
            \u001B[36m\\____/  /_/  \\____/\\__,_/ \\__,_/\u001B[33m  \\____/  \\____//_/    \\___/\u001B[0m\s
            """;

    @Command(
            name = "cloudcore",
            subcommands = {
                    StartCommand.class,
                    CreateCommand.class,
                    DeleteCommand.class,
                    LogsCommand.class,
                    ListCommand.class,
                    ExitCommand.class,
                    ReloadCommand.class,
                    ShutdownCommand.class,
                    MaintenanceCommand.class
            }
    )
    public static class CloudCoreCommand {
    }

    @Command(name = "logs")
    public static class LogsCommand implements Runnable{

        @Parameters(index = "0", arity = "0..1", description = "Server to show logs for.")
        @Suggests(RunningServerProvider.class)

        public String server;

        @Override
        public void run() {
            try {
                if (server == null || server.isBlank()) {
                    server = ServerManager.selectRunningServer();

                    if (server == null) {
                        return;
                    }
                }

                ServerManager.RunningServer runningServer = ServerManager.getRunningServers().get(server);

                if (runningServer == null) {
                    System.out.println("Server is not running: " + server);
                    return;
                }

                ServerManager.printLogsSnapshot(runningServer);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get server logs", e);
            }
        }
    }

    @Command(name = "start")
    public static class StartCommand implements Runnable {

        @Option(
                names = {"-s", "--singleton"},
                description = "Start The Server as Singleton, meaning it will start the actual server and not a Copy."
        )
        public boolean singleton;

        @Parameters(index = "0", description = "Server to start.")
        @Suggests(ServerTemplateProvider.class)
        public String server;

        @Override
        public void run() {
            System.out.println("Starting Server Async!");

            try {
                ServerManager.ServerLaunchResult result = ServerManager.launchServer(server, singleton).get();
                System.out.println(result.message());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

        }
    }

    @Command(name = "list")
    public static class ListCommand implements Runnable {
        @Override
        public void run() {
            Map<String, ServerManager.RunningServer> servers = ServerManager.getRunningServers();

            if (servers.isEmpty()) {
                System.out.println("No servers running.");
                return;
            }

            for (ServerManager.RunningServer server : servers.values()) {
                System.out.println(
                        server.name()
                                + " | template=" + server.templateName()
                                + " | port=" + server.port()
                                + " | container=" + server.containerName()
                                + " | dir=" + server.serverDir()
                );
            }
        }
    }

    @Command(name = "exit", aliases = "quit")
    public static class ExitCommand implements java.util.concurrent.Callable<Integer> {
        @Override
        public Integer call() {
            return 42;
        }
    }


    @Command(name = "create")
    public static class CreateCommand implements Runnable {

        @Option(names = {"-n","--name"})
        public String name = null;

        @Override
        public void run() {
            ServerManager.launchCreationWizard(name);
        }


    }

    @Command(name = "shutdown")
    public static class ShutdownCommand implements Runnable {

        @Parameters(index = "0", description = "Server to Delete.")
        @Suggests(RunningServerProvider.class)
        public String server;

        @Override
        public void run() {
            ServerManager.shutdownServer(ServerManager.getRunningServers().get(server));
        }
    }

    @Command(name = "delete")
    public static class DeleteCommand implements Runnable {

        @Parameters(index = "0", description = "Server to Delete.")
        @Suggests(ServerTemplateProvider.class)
        public String server;

        @Override
        public void run() {
            for (ServerManager.RunningServer server : ServerManager.getRunningServers().values()){
                if (server.templateName().equals(this.server)){
                    ServerManager.shutdownServer(server);
                }
            }
            if (Files.exists(ServerManager.BASE_DIR.resolve("templates").resolve(server))){
                deleteRecursive(ServerManager.BASE_DIR.resolve("templates").resolve(server));
            }
        }
    }

    @Command(name = "reload")
    public static class ReloadCommand implements Runnable {

        @Override
        public void run() {
            Config.load();
        }
    }
    private static final Pattern MC_NAME =
            Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    @Command(name = "maintenance")
    public static class MaintenanceCommand implements Callable<Integer> {

        @ArgGroup(exclusive = true, multiplicity = "1")
        private Action action;

        static class Action {

            @Option(names = "--on")
            boolean on;

            @Option(names = "--off")
            boolean off;

            @ArgGroup(exclusive = true)
            PlayerAction playerAction;
        }

        static class PlayerAction {

            @Option(names = "--add")
            boolean add;

            @Option(names = "--remove")
            boolean remove;

            @ArgGroup(exclusive = true, multiplicity = "1")
            Target target;
        }

        static class Target {

            @Option(names = "--uuid")
            UUID uuid;

            @Option(names = "--player")
            String player;
        }

        @Override
        public Integer call() {
            if (action.playerAction != null
                    && action.playerAction.target.player != null
                    && !MC_NAME.matcher(action.playerAction.target.player).matches()) {
                System.err.println("Invalid Minecraft username.");
                return 1;
            }
            if (action.on){
                ProxyServerManager.getInstance().maintenanceOn();
            }
            if (action.off){
                ProxyServerManager.getInstance().maintenanceOff();
            }
            if (action.playerAction != null) {
                var playerAction = action.playerAction;
                var target = playerAction.target;

                if (target.player != null) {
                    if (playerAction.add) {
                        ProxyServerManager.getInstance().addPlayer(target.player);
                    } else {
                        ProxyServerManager.getInstance().removePlayer(target.player);
                    }
                } else if (target.uuid != null) {
                    if (playerAction.add) {
                        ProxyServerManager.getInstance().addPlayer(target.uuid);
                    } else {
                        ProxyServerManager.getInstance().removePlayer(target.uuid);
                    }
                }
            }
            return 0;
        }
    }

    public static Map<String, Map<String, String>> SERVER_TO_VERSION_TO_URL_MAP;

    public static void main(String[] args) throws IOException {
        try {
            disableSpringLogs();
            System.out.println(logo);
            Config.load();

            System.out.println("CloudCore Startup");
            System.out.println("Started CloudCore Service");
            SERVER_TO_VERSION_TO_URL_MAP = VersionHelper.loadServerVersionMap();

            Thread communication = new Thread(()->{
                try {
                    ApiServer.start();
                } catch (IOException e) {
                    System.out.println("Failed to Start Server: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }, "communication");

            communication.start();

            if (Config.getInstance().isUseWebPanel()){
                Thread webPanelConnection = new Thread(
                        () -> CoreHandshakeInitializer.start(new CoreHandshakeProviderImpl()),
                        "web-panel-connection");
                webPanelConnection.start();
            }
            ServerManager.init();
            if (Config.getInstance().isUseWebPanel()) {
                CoreLogForwarder.start();
                CoreMetricForwarder.start();
            }

            CloudCoreCommand rootCommand = new CloudCoreCommand();
            CommandLine commandLine = new CommandLine(rootCommand);

            commandLoop(commandLine);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            System.out.println("Shutting Down All Servers and Proxy");
            CoreLogForwarder.stop();
            CoreMetricForwarder.stop();
            ApiServer.stop();
            CoreHandshakeInitializer.stop();
            ServerManager.shutdown();
        }

    }

    private static void disableSpringLogs() {
        System.setProperty("spring.main.banner-mode", "off");
        System.setProperty("logging.level.root", "OFF");
        System.setProperty("logging.level.org.springframework", "OFF");
        System.setProperty("logging.level.org.apache.catalina", "OFF");
        System.setProperty("logging.level.org.apache.coyote", "OFF");
        System.setProperty("logging.level.org.apache.tomcat", "OFF");
    }

    private static void commandLoop(CommandLine commandLine) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("CloudCore Command Mode");

        while (true) {
            System.out.print("> ");
            String line = reader.readLine();

            if (line == null) {
                break;
            }

            line = line.trim();

            if (line.isBlank()) {
                continue;
            }

            try {
                int exitCode = commandLine.execute(splitCommandLine(line));

                if (exitCode == 42) {
                    break;
                }
            } catch (Exception e) {
                System.out.println("Invalid command line: " + e.getMessage());
            }
        }
    }

    private static String[] splitCommandLine(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (escaping) {
            current.append('\\');
        }

        if (inSingleQuote || inDoubleQuote) {
            throw new IllegalArgumentException("Unclosed quote");
        }

        if (!current.isEmpty()) {
            words.add(current.toString());
        }

        return words.toArray(String[]::new);
    }

    public static void deleteRecursive(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }

            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + path, e);
        }
    }
}
