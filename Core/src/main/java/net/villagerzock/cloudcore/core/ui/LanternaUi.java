package net.villagerzock.cloudcore.core.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class LanternaUi {
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final AtomicBoolean COMMAND_EXIT_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean NESTED_EXIT_REQUESTED = new AtomicBoolean();
    private static final AtomicReference<UiMode> UI_MODE = new AtomicReference<>(UiMode.NONE);

    private LanternaUi() {
    }

    public record Option(String value, String label) {
    }

    public record CommandCompletion(List<String> suggestions, String hint) {
        public CommandCompletion {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
            hint = hint == null ? "" : hint;
        }
    }

    private record WindowBounds(int column, int row, int width, int height) {
    }

    public static String input(String title, String message, String defaultValue, boolean masked) throws IOException {
        return withScreen(screen -> {
            StringBuilder value = new StringBuilder(defaultValue == null ? "" : defaultValue);

            while (true) {
                TextGraphics graphics = prepareDialog(screen, title, 72, 12);
                WindowBounds window = windowBounds(screen, 72, 12);
                drawText(graphics, window.column + 3, window.row + 3, cut(message, window.width - 6));
                drawInputBox(graphics, window.column + 3, window.row + 5, window.width - 6, masked ? "*".repeat(value.length()) : value.toString());
                screen.refresh();

                KeyStroke key = screen.readInput();

                if (key.getKeyType() == KeyType.Enter) {
                    return value.toString();
                }
                if (key.getKeyType() == KeyType.Escape) {
                    return defaultValue == null ? "" : defaultValue;
                }
                if (key.getKeyType() == KeyType.Backspace && !value.isEmpty()) {
                    value.deleteCharAt(value.length() - 1);
                } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
                    value.append(key.getCharacter());
                }
            }
        });
    }

    public static String select(String title, String message, List<Option> options) throws IOException {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }

        return withScreen(screen -> {
            int selected = 0;

            while (true) {
                TextGraphics graphics = prepareDialog(screen, title, 72, 18);
                WindowBounds window = windowBounds(screen, 72, 18);
                drawText(graphics, window.column + 3, window.row + 3, cut(message, window.width - 6));

                int visibleRows = Math.max(1, window.height - 7);
                int offset = Math.max(0, Math.min(selected - visibleRows + 1, options.size() - visibleRows));

                for (int row = 0; row < visibleRows && offset + row < options.size(); row++) {
                    int index = offset + row;
                    Option option = options.get(index);
                    String prefix = index == selected ? "> " : "  ";

                    drawMenuLine(graphics, window.column + 3, window.row + 5 + row, window.width - 6, prefix + option.label(), index == selected);
                }

                screen.refresh();

                KeyStroke key = screen.readInput();

                if (key.getKeyType() == KeyType.Enter) {
                    return options.get(selected).value();
                }
                if (key.getKeyType() == KeyType.Escape) {
                    return options.get(0).value();
                }
                if (key.getKeyType() == KeyType.ArrowUp) {
                    selected = Math.max(0, selected - 1);
                } else if (key.getKeyType() == KeyType.ArrowDown) {
                    selected = Math.min(options.size() - 1, selected + 1);
                }
            }
        });
    }

    public static boolean confirm(String title, String message) throws IOException {
        return withScreen(screen -> {
            boolean confirmed = true;

            while (true) {
                TextGraphics graphics = prepareDialog(screen, title, 72, 11);
                WindowBounds window = windowBounds(screen, 72, 11);
                drawText(graphics, window.column + 3, window.row + 3, cut(message, window.width - 6));
                drawMenuLine(graphics, window.column + 3, window.row + 5, 12, "Yes", confirmed);
                drawMenuLine(graphics, window.column + 17, window.row + 5, 12, "No", !confirmed);
                screen.refresh();

                KeyStroke key = screen.readInput();

                if (key.getKeyType() == KeyType.Enter) {
                    return confirmed;
                }
                if (key.getKeyType() == KeyType.ArrowLeft || key.getKeyType() == KeyType.ArrowRight) {
                    confirmed = !confirmed;
                }
                if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
                    char c = Character.toLowerCase(key.getCharacter());

                    if (c == 'y') {
                        return true;
                    }
                    if (c == 'n') {
                        return false;
                    }
                }
            }
        });
    }

    public static void showStartup(String title, Runnable startup) throws IOException {
        showStartup(title, null, startup);
    }

    public static void showStartup(String title, String logo, Runnable startup) throws IOException {
        Objects.requireNonNull(startup, "startup");

        UiMode previousMode = UI_MODE.getAndSet(UiMode.STARTUP);
        Screen screen = openScreen();
        List<String> lines = new ArrayList<>();
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        CompletableFuture<Void> future = new CompletableFuture<>();

        PrintStream capture = new PrintStream(new LineCaptureOutputStream(lines), true, StandardCharsets.UTF_8);

        try {
            System.setOut(capture);
            System.setErr(capture);

            Thread thread = new Thread(() -> {
                try {
                    startup.run();
                    future.complete(null);
                } catch (Throwable t) {
                    t.printStackTrace();
                    future.completeExceptionally(t);
                }
            }, "startup");
            thread.start();

            while (!future.isDone()) {
                renderLogScreen(screen, title, logo, lines, "Starting...");
                KeyStroke key = screen.pollInput();

                if (key != null && key.getKeyType() == KeyType.EOF) {
                    break;
                }

                sleep(100);
            }

            renderLogScreen(screen, title, logo, lines, "Startup complete. Opening command mode...");
            future.join();
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            try {
                closeScreen(screen);
            } finally {
                UI_MODE.set(previousMode);
            }
        }
    }

    public static void showLiveLogs(
            String title,
            List<String> lines,
            CommandHandler commandHandler,
            AliveCheck aliveCheck
    ) throws IOException {
        showLiveLogs(title, lines, commandHandler, aliveCheck, () -> "");
    }

    public static void showLiveLogs(
            String title,
            List<String> lines,
            CommandHandler commandHandler,
            AliveCheck aliveCheck,
            StatusProvider statusProvider
    ) throws IOException {
        UiMode previousMode = UI_MODE.getAndSet(UiMode.NESTED);

        try {
            withScreen(screen -> {
                StringBuilder command = new StringBuilder();
                String status = "";
                String renderedCommand = null;
                String renderedStatus = null;
                int renderedLineCount = -1;
                String renderedLastLine = null;
                long nextStatusUpdate = 0;

                while (aliveCheck.isAlive()) {
                    if (NESTED_EXIT_REQUESTED.getAndSet(false)) {
                        break;
                    }

                    long now = System.currentTimeMillis();
                    boolean dirty = false;

                    if (now >= nextStatusUpdate) {
                        String updatedStatus = statusProvider.status();

                        if (!Objects.equals(status, updatedStatus)) {
                            status = updatedStatus;
                            dirty = true;
                        }

                        nextStatusUpdate = now + 2000;
                    }

                    int lineCount = lines.size();
                    String lastLine = lastLine(lines);

                    if (lineCount != renderedLineCount
                            || !Objects.equals(lastLine, renderedLastLine)
                            || !Objects.equals(command.toString(), renderedCommand)
                            || !Objects.equals(status, renderedStatus)) {
                        dirty = true;
                    }

                    if (dirty) {
                        renderInteractiveLogScreen(screen, title, lines, command.toString(), status);
                        renderedCommand = command.toString();
                        renderedStatus = status;
                        renderedLineCount = lineCount;
                        renderedLastLine = lastLine;
                    }

                    KeyStroke key = screen.pollInput();

                    if (key == null) {
                        sleep(100);
                        continue;
                    }

                    if (key.getKeyType() == KeyType.EOF || (key.getKeyType() == KeyType.Character && key.isCtrlDown() && key.getCharacter() == 'c')) {
                        NESTED_EXIT_REQUESTED.set(false);
                        break;
                    }
                    if (key.getKeyType() == KeyType.Enter) {
                        String commandText = command.toString().trim();
                        command.setLength(0);

                        if (!commandText.isBlank()) {
                            commandHandler.handle(commandText);
                            status = "sent: " + commandText;
                        }
                    } else if (key.getKeyType() == KeyType.Backspace && !command.isEmpty()) {
                        command.deleteCharAt(command.length() - 1);
                    } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
                        command.append(key.getCharacter());
                    }
                }

                return null;
            });
        } finally {
            UI_MODE.set(previousMode);
        }
    }

    public static void commandLoop(String title, String logo, CommandExecutor executor) throws IOException {
        commandLoop(title, logo, List.of(), executor);
    }

    public static void commandLoop(String title, String logo, List<String> suggestions, CommandExecutor executor) throws IOException {
        commandLoop(title, logo, command -> new CommandCompletion(findSuggestions(command, suggestions), ""), executor);
    }

    public static void commandLoop(String title, String logo, CommandCompletionProvider completionProvider, CommandExecutor executor) throws IOException {
        List<String> output = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        String status = "";
        int selectedSuggestion = 0;
        boolean running = true;
        UiMode previousMode = UI_MODE.getAndSet(UiMode.COMMAND);
        Screen screen = openScreen();

        try {
            while (running) {
                if (COMMAND_EXIT_REQUESTED.getAndSet(false)) {
                    break;
                }

                CommandCompletion completion = completionProvider.complete(command.toString());
                List<String> matches = completion.suggestions();
                selectedSuggestion = normalizeSuggestionIndex(selectedSuggestion, matches);
                renderCommandScreen(screen, title, logo, output, command.toString(), completion.hint().isBlank() ? status : completion.hint(), matches, selectedSuggestion);
                KeyStroke key = screen.pollInput();

                if (key == null) {
                    sleep(100);
                    continue;
                }

                if (isCtrlC(key) || key.getKeyType() == KeyType.EOF) {
                    COMMAND_EXIT_REQUESTED.set(false);
                    break;
                }
                if (key.getKeyType() == KeyType.Enter) {
                    String commandText = command.toString().trim();
                    command.setLength(0);
                    selectedSuggestion = 0;

                    if (commandText.isBlank()) {
                        continue;
                    }

                    closeScreen(screen);
                    screen = null;

                    boolean keepRunning = runCommand(commandText, output, executor);

                    if (!keepRunning) {
                        running = false;
                        break;
                    }

                    status = "";
                    screen = openScreen();
                } else if (key.getKeyType() == KeyType.Backspace && !command.isEmpty()) {
                    command.deleteCharAt(command.length() - 1);
                    selectedSuggestion = 0;
                } else if (key.getKeyType() == KeyType.ArrowLeft) {
                    selectedSuggestion = Math.max(0, selectedSuggestion - 1);
                } else if (key.getKeyType() == KeyType.ArrowRight) {
                    selectedSuggestion = Math.min(Math.max(0, matches.size() - 1), selectedSuggestion + 1);
                } else if (key.getKeyType() == KeyType.Tab && !matches.isEmpty()) {
                    completeSuggestion(command, matches.get(selectedSuggestion));
                } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null && !key.isCtrlDown()) {
                    command.append(key.getCharacter());
                    selectedSuggestion = 0;
                }
            }
        } finally {
            try {
                if (screen != null) {
                    closeScreen(screen);
                }

                restoreTerminal();
            } finally {
                UI_MODE.set(previousMode);
            }
        }
    }

    public static void requestCommandModeExit() {
        COMMAND_EXIT_REQUESTED.set(true);
    }

    public static void requestNestedModeExit() {
        NESTED_EXIT_REQUESTED.set(true);
    }

    public static void handleExternalInterrupt() {
        switch (UI_MODE.get()) {
            case STARTUP -> {
                // Startup ignores Ctrl+C for now.
            }
            case COMMAND -> requestCommandModeExit();
            case NESTED -> requestNestedModeExit();
            case NONE -> restoreTerminal();
        }
    }

    public static void showMessage(String title, String message) throws IOException {
        withScreen(screen -> {
            TextGraphics graphics = prepare(screen, title);
            drawText(graphics, 2, 3, message);
            screen.refresh();

            while (screen.readInput().getKeyType() != KeyType.Enter) {
                // wait
            }

            return null;
        });
    }

    public interface CommandHandler {
        void handle(String command) throws IOException;
    }

    public interface AliveCheck {
        boolean isAlive();
    }

    public interface StatusProvider {
        String status();
    }

    public interface CommandExecutor {
        boolean execute(String command) throws Exception;
    }

    public interface CommandCompletionProvider {
        CommandCompletion complete(String command);
    }

    private interface ScreenTask<T> {
        T run(Screen screen) throws IOException;
    }

    private enum UiMode {
        NONE,
        STARTUP,
        COMMAND,
        NESTED
    }

    private static <T> T withScreen(ScreenTask<T> task) throws IOException {
        Screen screen = openScreen();

        try {
            return task.run(screen);
        } finally {
            closeScreen(screen);
        }
    }

    private static Screen openScreen() throws IOException {
        Screen screen = new TerminalScreen(new DefaultTerminalFactory().createTerminal());
        screen.startScreen();
        screen.setCursorPosition(null);
        return screen;
    }

    private static void closeScreen(Screen screen) throws IOException {
        if (screen instanceof TerminalScreen terminalScreen) {
            terminalScreen.getTerminal().resetColorAndSGR();
            terminalScreen.getTerminal().setCursorVisible(true);
            terminalScreen.getTerminal().flush();
        }

        screen.stopScreen();
    }

    public static void restoreTerminal() {
        try {
            System.err.write(("\u001B[0m"
                    + "\u001B[!p"
                    + "\u001B[?1l"
                    + "\u001B>"
                    + "\u001B[?25h"
                    + "\u001B[?1000l"
                    + "\u001B[?1002l"
                    + "\u001B[?1003l"
                    + "\u001B[?1006l"
                    + "\u001B[?2004l"
                    + "\u001B[?1049l"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            System.err.flush();
        } catch (IOException ignored) {
        }

        try {
            Process process = new ProcessBuilder("sh", "-c", "stty sane echo icanon isig iexten opost < /dev/tty").start();
            process.waitFor();
        } catch (IOException ignored) {
            // No controlling terminal, nothing to restore.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static TextGraphics prepare(Screen screen, String title) {
        screen.clear();
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(TextColor.ANSI.CYAN);
        drawText(graphics, 2, 1, title);
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        return graphics;
    }

    private static TextGraphics prepareDialog(Screen screen, String title, int preferredWidth, int preferredHeight) {
        screen.clear();
        TextGraphics graphics = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();

        graphics.setBackgroundColor(TextColor.ANSI.BLUE);
        graphics.setForegroundColor(TextColor.ANSI.WHITE);
        graphics.fillRectangle(new TerminalPosition(0, 0), size, ' ');

        WindowBounds window = windowBounds(screen, preferredWidth, preferredHeight);
        graphics.setBackgroundColor(TextColor.ANSI.WHITE);
        graphics.setForegroundColor(TextColor.ANSI.BLACK);
        graphics.fillRectangle(
                new TerminalPosition(window.column, window.row),
                new TerminalSize(window.width, window.height),
                ' '
        );
        drawBorder(graphics, window);
        drawText(graphics, window.column + 2, window.row + 1, cut(title, window.width - 4));

        return graphics;
    }

    private static void renderLogScreen(Screen screen, String title, String logo, List<String> lines, String status) throws IOException {
        TextGraphics graphics = prepare(screen, title);
        int rows = screen.getTerminalSize().getRows();
        int columns = screen.getTerminalSize().getColumns();
        List<String> logoLines = splitLogo(logo);
        int bodyStart = drawLogo(graphics, logoLines, 3, columns);
        int bodyRows = Math.max(1, rows - bodyStart - 2);
        List<String> copy = copyLines(lines);
        int start = Math.max(0, copy.size() - bodyRows);

        for (int row = 0; row < bodyRows && start + row < copy.size(); row++) {
            drawText(graphics, 2, bodyStart + row, cut(copy.get(start + row), columns - 4));
        }

        drawText(graphics, 2, rows - 2, cut(status, columns - 4));
        screen.refresh();
    }

    private static void renderCommandScreen(
            Screen screen,
            String title,
            String logo,
            List<String> output,
            String command,
            String status,
            List<String> suggestions,
            int selectedSuggestion
    ) throws IOException {
        TextGraphics graphics = prepare(screen, title);
        int rows = screen.getTerminalSize().getRows();
        int columns = screen.getTerminalSize().getColumns();
        List<String> logoLines = splitLogo(logo);
        int bodyStart = drawLogo(graphics, logoLines, 3, columns);
        int bodyRows = Math.max(1, rows - bodyStart - 5);
        List<String> copy = copyLines(output);
        int start = Math.max(0, copy.size() - bodyRows);

        for (int row = 0; row < bodyRows && start + row < copy.size(); row++) {
            drawText(graphics, 2, bodyStart + row, cut(copy.get(start + row), columns - 4));
        }

        drawText(graphics, 2, rows - 4, "-".repeat(Math.max(1, columns - 4)));
        drawText(graphics, 2, rows - 3, cut("> " + command, columns - 4));
        drawSuggestions(graphics, 2, rows - 2, columns - 4, suggestions, selectedSuggestion, status);
        screen.setCursorPosition(new TerminalPosition(Math.min(columns - 1, 4 + command.length()), rows - 3));
        screen.refresh();
    }

    private static void renderInteractiveLogScreen(
            Screen screen,
            String title,
            List<String> lines,
            String command,
            String status
    ) throws IOException {
        TextGraphics graphics = prepare(screen, title);
        int rows = screen.getTerminalSize().getRows();
        int columns = screen.getTerminalSize().getColumns();
        int bodyRows = Math.max(1, rows - 7);
        List<String> copy = copyLines(lines);
        int start = Math.max(0, copy.size() - bodyRows);

        for (int row = 0; row < bodyRows && start + row < copy.size(); row++) {
            drawText(graphics, 2, 3 + row, cut(copy.get(start + row), columns - 4));
        }

        drawText(graphics, 2, rows - 4, "-".repeat(Math.max(1, columns - 4)));
        drawText(graphics, 2, rows - 3, cut("> " + command, columns - 4));
        drawText(graphics, 2, rows - 2, cut(status, columns - 4));
        screen.setCursorPosition(new TerminalPosition(Math.min(columns - 1, 4 + command.length()), rows - 3));
        screen.refresh();
    }

    private static List<String> copyLines(List<String> lines) {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    private static String lastLine(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }

        try {
            return lines.get(lines.size() - 1);
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    private static void drawText(TextGraphics graphics, int column, int row, String text) {
        graphics.putString(new TerminalPosition(column, row), text == null ? "" : text);
    }

    private static void drawBorder(TextGraphics graphics, WindowBounds window) {
        int right = window.column + window.width - 1;
        int bottom = window.row + window.height - 1;

        for (int column = window.column; column <= right; column++) {
            graphics.setCharacter(column, window.row, '-');
            graphics.setCharacter(column, bottom, '-');
        }

        for (int row = window.row; row <= bottom; row++) {
            graphics.setCharacter(window.column, row, '|');
            graphics.setCharacter(right, row, '|');
        }

        graphics.setCharacter(window.column, window.row, '+');
        graphics.setCharacter(right, window.row, '+');
        graphics.setCharacter(window.column, bottom, '+');
        graphics.setCharacter(right, bottom, '+');
    }

    private static WindowBounds windowBounds(Screen screen, int preferredWidth, int preferredHeight) {
        TerminalSize size = screen.getTerminalSize();
        int width = Math.min(preferredWidth, Math.max(30, size.getColumns() - 4));
        int height = Math.min(preferredHeight, Math.max(8, size.getRows() - 4));
        int column = Math.max(0, (size.getColumns() - width) / 2);
        int row = Math.max(0, (size.getRows() - height) / 2);

        return new WindowBounds(column, row, width, height);
    }

    private static void drawInputBox(TextGraphics graphics, int column, int row, int width, String value) {
        graphics.setBackgroundColor(TextColor.ANSI.BLACK);
        graphics.setForegroundColor(TextColor.ANSI.WHITE);
        graphics.putString(new TerminalPosition(column, row), " ".repeat(Math.max(1, width)));
        graphics.putString(new TerminalPosition(column + 1, row), cut(value, Math.max(1, width - 2)));
        graphics.setBackgroundColor(TextColor.ANSI.WHITE);
        graphics.setForegroundColor(TextColor.ANSI.BLACK);
    }

    private static void drawMenuLine(TextGraphics graphics, int column, int row, int width, String text, boolean selected) {
        graphics.setBackgroundColor(selected ? TextColor.ANSI.BLUE : TextColor.ANSI.WHITE);
        graphics.setForegroundColor(selected ? TextColor.ANSI.WHITE : TextColor.ANSI.BLACK);
        graphics.putString(new TerminalPosition(column, row), pad(cut(text, width), width));
        graphics.setBackgroundColor(TextColor.ANSI.WHITE);
        graphics.setForegroundColor(TextColor.ANSI.BLACK);
    }

    private static int drawLogo(TextGraphics graphics, List<String> logoLines, int startRow, int columns) {
        if (logoLines.isEmpty()) {
            return startRow;
        }

        graphics.setForegroundColor(TextColor.ANSI.CYAN);

        for (int i = 0; i < logoLines.size(); i++) {
            drawText(graphics, 2, startRow + i, cut(logoLines.get(i), columns - 4));
        }

        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        return startRow + logoLines.size() + 1;
    }

    private static List<String> splitLogo(String logo) {
        if (logo == null || logo.isBlank()) {
            return List.of();
        }

        String cleaned = ANSI_ESCAPE.matcher(logo).replaceAll("");
        return cleaned.lines().toList();
    }

    private static boolean isCtrlC(KeyStroke key) {
        return key.getKeyType() == KeyType.Character
                && key.isCtrlDown()
                && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'c';
    }

    private static void drawSuggestions(
            TextGraphics graphics,
            int column,
            int row,
            int width,
            List<String> suggestions,
            int selectedSuggestion,
            String fallback
    ) {
        if (suggestions.isEmpty()) {
            drawText(graphics, column, row, cut(fallback, width));
            return;
        }

        int cursor = column;

        for (int i = 0; i < suggestions.size() && cursor < column + width; i++) {
            String text = " " + suggestions.get(i) + " ";

            graphics.setBackgroundColor(i == selectedSuggestion ? TextColor.ANSI.CYAN : TextColor.ANSI.DEFAULT);
            graphics.setForegroundColor(i == selectedSuggestion ? TextColor.ANSI.BLACK : TextColor.ANSI.DEFAULT);
            drawText(graphics, cursor, row, cut(text, column + width - cursor));
            cursor += text.length() + 1;
        }

        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private static List<String> findSuggestions(String command, List<String> suggestions) {
        String token = currentToken(command);

        if (token.isBlank()) {
            return suggestions.stream().limit(6).toList();
        }

        return suggestions.stream()
                .filter(suggestion -> suggestion.startsWith(token))
                .limit(6)
                .toList();
    }

    private static int normalizeSuggestionIndex(int selectedSuggestion, List<String> suggestions) {
        if (suggestions.isEmpty()) {
            return 0;
        }

        return Math.min(selectedSuggestion, suggestions.size() - 1);
    }

    private static void completeSuggestion(StringBuilder command, String suggestion) {
        String text = command.toString();
        int tokenStart = Math.max(text.lastIndexOf(' '), Math.max(text.lastIndexOf('\t'), -1)) + 1;
        command.replace(tokenStart, command.length(), suggestion);

        if (tokenStart == 0) {
            command.append(' ');
        }
    }

    private static String currentToken(String command) {
        int tokenStart = Math.max(command.lastIndexOf(' '), Math.max(command.lastIndexOf('\t'), -1)) + 1;
        return command.substring(tokenStart);
    }

    private static boolean runCommand(String command, List<String> output, CommandExecutor executor) throws IOException {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        PrintStream capture = new PrintStream(new LineCaptureOutputStream(output), true, StandardCharsets.UTF_8);

        try {
            System.setOut(capture);
            System.setErr(capture);
            return executor.execute(command);
        } catch (Exception e) {
            e.printStackTrace(capture);
            return true;
        } finally {
            capture.flush();
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }

    private static String cut(String text, int width) {
        if (text == null) {
            return "";
        }

        if (width <= 0) {
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

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }

        return text + " ".repeat(width - text.length());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class LineCaptureOutputStream extends OutputStream {
        private final List<String> lines;
        private final StringBuilder current = new StringBuilder();

        private LineCaptureOutputStream(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                flushLine();
                return;
            }

            if (b != '\r') {
                current.append((char) b);
            }
        }

        @Override
        public synchronized void flush() {
            if (!current.isEmpty()) {
                flushLine();
            }
        }

        private void flushLine() {
            synchronized (lines) {
                lines.add(current.toString());

                while (lines.size() > 1000) {
                    lines.remove(0);
                }
            }

            current.setLength(0);
        }
    }
}
