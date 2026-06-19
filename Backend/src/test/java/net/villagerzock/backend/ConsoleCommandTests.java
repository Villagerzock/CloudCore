package net.villagerzock.backend;

import net.villagerzock.backend.websocket.ConsoleCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleCommandTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCommandWithoutSubscribeFlag() throws Exception {
        ConsoleCommand command = objectMapper.readValue(
                """
                {"console":"proxy","command":"status"}
                """,
                ConsoleCommand.class);

        assertThat(command.command()).isEqualTo("status");
        assertThat(command.subscribe()).isNull();
    }

    @Test
    void acceptsSubscribeWithoutCommand() throws Exception {
        ConsoleCommand command = objectMapper.readValue(
                """
                {"console":"proxy","subscribe":true}
                """,
                ConsoleCommand.class);

        assertThat(command.command()).isNull();
        assertThat(command.subscribe()).isTrue();
    }
}
