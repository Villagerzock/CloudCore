package net.villagerzock.cloudcore.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.villagerzock.cloudcore.core.api.ApiServer;
import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.cloudcore.core.server.ServerType;
import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.*;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Main {

    @CommandLine.Command(
            name = "cloudcore",
            subcommands = {
                    StartCommand.class,
                    CreateCommand.class,
                    DeleteCommand.class,
                    LogsCommand.class,
                    ListCommand.class,
                    ExitCommand.class,
                    ReloadCommand.class,
                    ShutdownCommand.class
            }
    )
    public static class CloudCoreCommand {
    }

    @CommandLine.Command(name = "logs")
    public static class LogsCommand implements Runnable{

        @CommandLine.Parameters(index = "0", description = "Server to start.")
        public String server;

        @CommandLine.Option(names = {"-b","--book"})
        public boolean asBook;

        @Override
        public void run() {
            try {
                ServerManager.RunningServer runningServer = ServerManager.getRunningServers().get(server);

                if (runningServer == null) {
                    System.out.println("Server is not running: " + server);
                    return;
                }

                if (asBook) {
                    ServerManager.showLiveLogsBook(runningServer,terminal);
                } else {
                    ServerManager.printLogsSnapshot(runningServer);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get server logs", e);
            }
        }
    }

    @CommandLine.Command(name = "start")
    public static class StartCommand implements Runnable {

        @CommandLine.Option(
                names = {"-s", "--singleton"},
                description = "Start The Server as Singleton, meaning it will start the actual server and not a Copy."
        )
        public boolean singleton;

        @CommandLine.Parameters(index = "0", description = "Server to start.")
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

    @CommandLine.Command(name = "list")
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

    @CommandLine.Command(name = "exit", aliases = "quit")
    public static class ExitCommand implements java.util.concurrent.Callable<Integer> {
        @Override
        public Integer call() {
            return 42;
        }
    }


    @CommandLine.Command(name = "create")
    public static class CreateCommand implements Runnable {

        @CommandLine.Option(names = {"-n","--name"})
        public String name = null;

        @Override
        public void run() {
            ServerManager.launchCreationWizard(name);
        }


    }

    @CommandLine.Command(name = "shutdown")
    public static class ShutdownCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "Server to Delete.")
        public String server;

        @Override
        public void run() {
            ServerManager.shutdownServer(ServerManager.getRunningServers().get(server));
        }
    }

    @CommandLine.Command(name = "delete")
    public static class DeleteCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "Server to Delete.")
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

    @CommandLine.Command(name = "reload")
    public static class ReloadCommand implements Runnable {

        @Override
        public void run() {
            Config.load();
        }
    }

    public static Terminal terminal;
    public static Map<String, Map<String, String>> SERVER_TO_VERSION_TO_URL_MAP;

    public static void main(String[] args) throws IOException {
        try {
            System.out.println("Started CloudCore Service");

            SERVER_TO_VERSION_TO_URL_MAP = VersionHelper.loadServerVersionMap();

            System.out.println("Loading Config");

            Thread communication = new Thread(()->{
                try {
                    ApiServer.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, "communication");

            communication.start();

            CloudCoreCommand rootCommand = new CloudCoreCommand();
            CommandLine commandLine = new CommandLine(rootCommand);

            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            Config.load();
            ServerManager.init();

            Parser parser = new DefaultParser();

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(parser)
                    .completer(new PicocliJLineCompleter(commandLine.getCommandSpec()))
                    .build();

            while (true) {
                String line;

                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException e) {
                    break;
                } catch (EndOfFileException e) {
                    break;
                }

                if (line == null || line.isBlank()) {
                    continue;
                }

                List<String> words = parser.parse(line, 0).words();

                int exitCode = commandLine.execute(words.toArray(String[]::new));

                if (exitCode == 42) {
                    break;
                }
            }
        }finally {
            System.out.println("Shutting Down All Servers and Proxy");
            ServerManager.shutdown();

            System.exit(0);
        }

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