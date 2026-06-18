package net.villagerzock.backend.websocket;

import java.util.List;

public record ConsoleMessage(String console, List<String> lines) {
}
