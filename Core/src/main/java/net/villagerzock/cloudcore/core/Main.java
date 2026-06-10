package net.villagerzock.cloudcore.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
                    QuitCommand.class
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

            ServerManager.launchServer(server, singleton).thenAccept(result -> {
                System.out.println(result.message());
            });
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

    @CommandLine.Command(name = "exit")
    public static class ExitCommand implements java.util.concurrent.Callable<Integer> {
        @Override
        public Integer call() {
            return 42;
        }
    }

    @CommandLine.Command(name = "quit")
    public static class QuitCommand implements java.util.concurrent.Callable<Integer> {
        @Override
        public Integer call() {
            return 42;
        }
    }

    @CommandLine.Command(name = "create")
    public static class CreateCommand implements Runnable {

        @Override
        public void run() {
            launchCreationWizard();
        }

        private void launchCreationWizard() {
            try {

                ConsolePrompt prompt = new ConsolePrompt(terminal);

                PromptBuilder builder = prompt.getPromptBuilder();

                builder.createInputPrompt()
                        .name("name")
                        .message("How do you want to call the Server?")
                        .addPrompt();

                builder.createListPrompt()
                        .name("serverType")
                        .message("What server type?")
                        .newItem("paper").text("Paper").add()
                        .newItem("folia").text("Folia").add()
                        .addPrompt();

                Map<String, ? extends PromptResultItemIF> result = prompt.prompt(builder.build());

                ListResult serverType = (ListResult) result.get("serverType");
                InputResult name = (InputResult) result.get("name");

                prompt = new ConsolePrompt(terminal);
                builder = prompt.getPromptBuilder();

                ListPromptBuilder versionBuilder = builder.createListPrompt()
                        .name("version")
                        .message("What version do you Want?");

                for (String version : SERVER_TO_VERSION_TO_URL_MAP.get(serverType.getResult()).keySet()){
                    versionBuilder.newItem(version).text(version).add();
                }
                versionBuilder.addPrompt();

                builder.createConfirmPromp()
                        .name("eula")
                        .message("Do you accept the Mojang EULA? (https://www.minecraft.net/en-us/eula?utm_source=chatgpt.com)")
                        .addPrompt();

                result = prompt.prompt(builder.build());

                ListResult version = (ListResult) result.get("version");
                ConfirmResult eula = (ConfirmResult) result.get("eula");

                if (eula.getConfirmed() == ConfirmChoice.ConfirmationValue.NO){
                    System.out.println("Eula not accepted. Abort!");
                    return;
                }

                ServerManager.createServer(ServerType.valueOf(serverType.getResult().toUpperCase(Locale.ROOT)),SERVER_TO_VERSION_TO_URL_MAP.get(serverType.getResult()).get(version.getResult()), name.getResult());


            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @CommandLine.Command(name = "delete")
    public static class DeleteCommand implements Runnable {

        @CommandLine.Parameters(index = "0", description = "Server to Delete.")
        public String server;

        @Override
        public void run() {
            CompletableFuture<Void> serverStarted = new CompletableFuture<>();

            System.out.println("Starting Server!");
        }
    }

    public static Terminal terminal;
    public static Map<String, Map<String, String>> SERVER_TO_VERSION_TO_URL_MAP;

    public static void main(String[] args) throws IOException {
        System.out.println("Started CloudCore Service");

        SERVER_TO_VERSION_TO_URL_MAP = VersionHelper.loadServerVersionMap();



        CloudCoreCommand rootCommand = new CloudCoreCommand();
        CommandLine commandLine = new CommandLine(rootCommand);

        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

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
    }
}