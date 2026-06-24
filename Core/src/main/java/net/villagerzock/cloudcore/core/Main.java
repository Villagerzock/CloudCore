package net.villagerzock.cloudcore.core;

import net.villagerzock.cloudcore.core.api.ApiServer;
import net.villagerzock.cloudcore.core.api.CoreHandshakeProviderImpl;
import net.villagerzock.cloudcore.core.api.CoreLogForwarder;
import net.villagerzock.cloudcore.core.api.CoreMetricForwarder;
import net.villagerzock.cloudcore.core.command.SuggestionProvider;
import net.villagerzock.cloudcore.core.command.Suggests;
import net.villagerzock.cloudcore.core.command.providers.RunningServerProvider;
import net.villagerzock.cloudcore.core.command.providers.ServerTemplateProvider;
import net.villagerzock.cloudcore.core.config.Config;
import net.villagerzock.cloudcore.core.server.ProxyServerManager;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.cloudcore.core.ui.LanternaUi;
import net.villagerzock.corehandshake.CoreHandshakeInitializer;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static picocli.CommandLine.*;

public class Main {
    private static final Map<Class<? extends SuggestionProvider>, SuggestionProvider> SUGGESTION_PROVIDER_CACHE = new HashMap<>();

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

        @Option(names = {"-b","--book"})
        public boolean asBook;

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

                if (asBook) {
                    ServerManager.showLiveLogsBook(runningServer);
                } else {
                    ServerManager.printLogsSnapshot(runningServer);
                }
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
        installTerminalSafety();

        try {
            Config.load();

            LanternaUi.showStartup("CloudCore Startup", logo, () -> {
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
            });

            CloudCoreCommand rootCommand = new CloudCoreCommand();
            CommandLine commandLine = new CommandLine(rootCommand);

            LanternaUi.commandLoop(
                    "CloudCore Command Mode",
                    logo,
                    command -> completeCommandLine(commandLine, command),
                    line -> {
                        try {
                            int exitCode = commandLine.execute(splitCommandLine(line));
                            return exitCode != 42;
                        } catch (Exception e) {
                            System.out.println("Invalid command line: " + e.getMessage());
                            return true;
                        }
                    }
            );
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            System.out.println("Shutting Down All Servers and Proxy");
            CoreLogForwarder.stop();
            CoreMetricForwarder.stop();
            ServerManager.shutdown();

            ApiServer.stop();
            CoreHandshakeInitializer.stop();
            LanternaUi.restoreTerminal();
        }

    }

    private static void installTerminalSafety() {
        Runtime.getRuntime().addShutdownHook(new Thread(LanternaUi::restoreTerminal, "terminal-restore"));
        installSigintHandler();
    }

    private static void installSigintHandler() {
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            Object intSignal = signalClass.getConstructor(String.class).newInstance("INT");
            Object handler = java.lang.reflect.Proxy.newProxyInstance(
                    Main.class.getClassLoader(),
                    new Class<?>[]{signalHandlerClass},
                    (proxy, method, methodArgs) -> {
                        if ("handle".equals(method.getName())) {
                            LanternaUi.handleExternalInterrupt();
                        }
                        return null;
                    }
            );

            signalClass.getMethod("handle", signalClass, signalHandlerClass).invoke(null, intSignal, handler);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // If SIGINT cannot be intercepted on this runtime, Lanterna key handling and the shutdown hook still apply.
        }
    }

    private record CompletionInput(List<String> tokens, boolean trailingSeparator) {
        String currentToken() {
            if (trailingSeparator || tokens.isEmpty()) {
                return "";
            }

            return tokens.get(tokens.size() - 1);
        }
    }

    private record ArgumentState(Set<OptionSpec> usedOptions, int positionals, OptionSpec awaitingValue) {
    }

    private static LanternaUi.CommandCompletion completeCommandLine(CommandLine commandLine, String input) {
        CompletionInput parsed = parseCompletionInput(input);
        CommandSpec root = commandLine.getCommandSpec();

        if (parsed.tokens().isEmpty()) {
            return suggestions(root.subcommands().keySet(), "");
        }

        String commandName = parsed.tokens().get(0);
        CommandLine subcommand = root.subcommands().get(commandName);

        if (subcommand == null) {
            return suggestions(root.subcommands().keySet(), parsed.currentToken());
        }

        if (parsed.tokens().size() == 1 && !parsed.trailingSeparator()) {
            return suggestions(root.subcommands().keySet(), parsed.currentToken());
        }

        CommandSpec spec = subcommand.getCommandSpec();
        List<String> argumentTokens = parsed.tokens().subList(1, parsed.tokens().size());
        String currentToken = parsed.currentToken();
        List<String> completedTokens = parsed.trailingSeparator() || argumentTokens.isEmpty()
                ? argumentTokens
                : argumentTokens.subList(0, argumentTokens.size() - 1);
        ArgumentState state = argumentState(spec, completedTokens);

        if (state.awaitingValue() != null) {
            return valueCompletion(state.awaitingValue(), currentToken);
        }

        OptionSpec currentOption = spec.findOption(currentToken);

        if (!parsed.trailingSeparator() && currentOption != null && expectsValue(currentOption)) {
            return valueCompletion(currentOption, "");
        }

        if (!parsed.trailingSeparator() && currentToken.startsWith("-")) {
            return suggestions(remainingOptionNames(spec, state.usedOptions()), currentToken);
        }

        PositionalParamSpec positional = nextPositional(spec, state.positionals());

        if (positional != null) {
            return valueCompletion(positional, currentToken);
        }

        return suggestions(remainingOptionNames(spec, state.usedOptions()), currentToken);
    }

    private static LanternaUi.CommandCompletion suggestions(Collection<String> candidates, String prefix) {
        String filter = prefix == null ? "" : prefix;

        return new LanternaUi.CommandCompletion(
                candidates.stream()
                        .filter(candidate -> candidate.startsWith(filter))
                        .distinct()
                        .toList(),
                ""
        );
    }

    private static LanternaUi.CommandCompletion valueCompletion(picocli.CommandLine.Model.ArgSpec arg, String prefix) {
        Suggests suggests = suggestsAnnotation(arg);

        if (suggests != null) {
            return suggestions(provider(suggests.value()).suggestions(), prefix);
        }

        return valueHint(arg);
    }

    private static LanternaUi.CommandCompletion valueHint(picocli.CommandLine.Model.ArgSpec arg) {
        String label = arg.paramLabel();

        if (label == null || label.isBlank() || label.startsWith("<")) {
            label = arg.isOption() ? ((OptionSpec) arg).longestName() : "parameter";
        }

        return new LanternaUi.CommandCompletion(List.of(), "expected " + label + ": " + simpleTypeName(arg.type()));
    }

    private static Suggests suggestsAnnotation(picocli.CommandLine.Model.ArgSpec arg) {
        Object userObject = arg.userObject();

        if (userObject instanceof Field field) {
            return field.getAnnotation(Suggests.class);
        }

        try {
            Field annotatedElementField = picocli.CommandLine.Model.ArgSpec.class.getDeclaredField("annotatedElement");
            annotatedElementField.setAccessible(true);
            Object annotatedElement = annotatedElementField.get(arg);

            if (annotatedElement instanceof picocli.CommandLine.Model.IAnnotatedElement element) {
                return element.getAnnotation(Suggests.class);
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the default type hint when picocli does not expose the annotation.
        }

        return null;
    }

    private static SuggestionProvider provider(Class<? extends SuggestionProvider> providerClass) {
        return SUGGESTION_PROVIDER_CACHE.computeIfAbsent(providerClass, Main::createSuggestionProvider);
    }

    private static SuggestionProvider createSuggestionProvider(Class<? extends SuggestionProvider> providerClass) {
        try {
            return providerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create suggestion provider: " + providerClass.getSimpleName(), e);
        }
    }

    private static List<String> remainingOptionNames(CommandSpec spec, Set<OptionSpec> usedOptions) {
        List<OptionSpec> options = new ArrayList<>(spec.options());

        for (ArgGroupSpec group : spec.argGroups()) {
            options.addAll(group.allOptionsNested());
        }

        return options.stream()
                .filter(option -> !option.hidden())
                .filter(option -> !usedOptions.contains(option))
                .flatMap(option -> Arrays.stream(option.names()))
                .distinct()
                .toList();
    }

    private static PositionalParamSpec nextPositional(CommandSpec spec, int positionals) {
        List<PositionalParamSpec> positionalParams = new ArrayList<>(spec.positionalParameters());

        for (ArgGroupSpec group : spec.argGroups()) {
            positionalParams.addAll(group.allPositionalParametersNested());
        }

        if (positionals >= positionalParams.size()) {
            return null;
        }

        return positionalParams.get(positionals);
    }

    private static ArgumentState argumentState(CommandSpec spec, List<String> tokens) {
        Set<OptionSpec> usedOptions = new HashSet<>();
        int positionals = 0;
        OptionSpec awaitingValue = null;

        for (String token : tokens) {
            if (awaitingValue != null) {
                awaitingValue = null;
                continue;
            }

            OptionSpec option = spec.findOption(token);

            if (option != null) {
                usedOptions.add(option);

                if (expectsValue(option)) {
                    awaitingValue = option;
                }

                continue;
            }

            positionals++;
        }

        return new ArgumentState(usedOptions, positionals, awaitingValue);
    }

    private static boolean expectsValue(OptionSpec option) {
        return option.arity().max() > 0 && option.type() != boolean.class && option.type() != Boolean.class;
    }

    private static String simpleTypeName(Class<?> type) {
        if (type == null) {
            return "String";
        }

        if (type.isArray()) {
            return simpleTypeName(type.getComponentType()) + "[]";
        }

        return type.getSimpleName();
    }

    private static CompletionInput parseCompletionInput(String line) {
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

        if (!current.isEmpty()) {
            words.add(current.toString());
        }

        boolean trailingSeparator = !line.isEmpty() && Character.isWhitespace(line.charAt(line.length() - 1));

        return new CompletionInput(words, trailingSeparator);
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
