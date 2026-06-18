package net.villagerzock.backend.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ConsoleService {
    public List<String> getWelcomeLines(String console) {
        return List.of(
                "\u001b[1;32mCloudCore live console\u001b[0m",
                "Connected to " + console + ". Enter a command."
        );
    }

    public List<String> execute(String console, String command) {
        String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return List.of(
                "> " + command,
                "\u001b[2m" + timestamp + "\u001b[0m \u001b[32mINFO\u001b[0m [" + console
                        + "] Dummy service accepted command: " + command
        );
    }
}
