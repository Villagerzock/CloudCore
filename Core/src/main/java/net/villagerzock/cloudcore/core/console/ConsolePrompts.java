package net.villagerzock.cloudcore.core.console;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;

public final class ConsolePrompts {
    private ConsolePrompts() {
    }

    public record Option(String value, String label) {
    }

    public static String input(String title, String message, String defaultValue, boolean masked) throws IOException {
        String fallback = defaultValue == null ? "" : defaultValue;

        try (Terminal terminal = newTerminal()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            String prompt = title + " - " + message + (fallback.isBlank() ? ": " : " [" + fallback + "]: ");
            String value = masked ? reader.readLine(prompt, '*') : reader.readLine(prompt);

            if (value == null || value.isBlank()) {
                return fallback;
            }

            return value;
        } catch (EndOfFileException | UserInterruptException e) {
            return fallback;
        }
    }

    public static String select(String title, String message, List<Option> options) throws IOException {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }

        try (Terminal terminal = newTerminal()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            terminal.writer().println(title + " - " + message);

            for (int i = 0; i < options.size(); i++) {
                terminal.writer().printf("%d) %s%n", i + 1, options.get(i).label());
            }

            terminal.writer().flush();

            while (true) {
                String line = reader.readLine("Select [1]: ");

                if (line == null || line.isBlank()) {
                    return options.get(0).value();
                }

                try {
                    int selected = Integer.parseInt(line.trim());

                    if (selected >= 1 && selected <= options.size()) {
                        return options.get(selected - 1).value();
                    }
                } catch (NumberFormatException ignored) {
                    for (Option option : options) {
                        if (option.value().equalsIgnoreCase(line.trim())) {
                            return option.value();
                        }
                    }
                }

                terminal.writer().println("Invalid selection.");
                terminal.writer().flush();
            }
        } catch (EndOfFileException | UserInterruptException e) {
            return options.get(0).value();
        }
    }

    public static boolean confirm(String title, String message) throws IOException {
        try (Terminal terminal = newTerminal()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            while (true) {
                String answer = reader.readLine(title + " - " + message + " [Y/n]: ");

                if (answer == null || answer.isBlank()) {
                    return true;
                }

                String normalized = answer.trim().toLowerCase();

                if (normalized.equals("y") || normalized.equals("yes")) {
                    return true;
                }
                if (normalized.equals("n") || normalized.equals("no")) {
                    return false;
                }
            }
        } catch (EndOfFileException | UserInterruptException e) {
            return false;
        }
    }

    public static void showMessage(String title, String message) {
        System.out.println(title + ": " + message);
    }

    private static Terminal newTerminal() throws IOException {
        return TerminalBuilder.builder()
                .system(true)
                .build();
    }

}
