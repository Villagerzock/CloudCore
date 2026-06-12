package net.villagerzock.cloudcore.core.config;

import lombok.Getter;
import net.villagerzock.cloudcore.core.server.ServerManager;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.InputResult;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static net.villagerzock.cloudcore.core.Main.terminal;

public class Config {

    private static Config instance;

    @Getter
    public MariaDb mariadb = new MariaDb();

    @Getter
    public ProxyConfig proxy = new ProxyConfig();

    @Getter
    public LobbyConfig lobby = new LobbyConfig();

    public void save() {
        Path path = ServerManager.BASE_DIR.resolve("config.yml");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Representer representer = new NoClassTagRepresenter(options);

        Yaml yaml = new Yaml(representer,options);
        try {
            Files.createDirectories(path.getParent());
            instance = new Config();
            instance.mariadb = launchDatabaseConfigWizard();


            try (Writer writer = Files.newBufferedWriter(path)) {
                yaml.dump(instance, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public static class MariaDb {
        @Getter
        public int port = 3306;
        @Getter
        public String user = "root";
        @Getter
        public String password = "password";
    }

    public static class ProxyConfig {
        @Getter
        public boolean onlineMode = true;
        @Getter
        public String motd = "<#09add3>A Velocity Server";
    }

    public static class LobbyConfig {
        public enum Mode {
            STATIC,
            VARIABLE
        }

        @Getter
        public String server = null;

        @Getter
        public Mode mode = Mode.STATIC;

        @Getter
        public Integer from = null;

        @Getter
        public Integer to = null;

        @Getter
        public Integer amount = 1;
    }



    public static void load() {
        Path path = ServerManager.BASE_DIR.resolve("config.yml");
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Representer representer = new NoClassTagRepresenter(options);

        Yaml yaml = new Yaml(representer,options);

        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());

                instance = new Config();
                instance.mariadb = launchDatabaseConfigWizard();


                try (Writer writer = Files.newBufferedWriter(path)) {
                    yaml.dump(instance, writer);
                }

                return;
            }

            try (InputStream in = Files.newInputStream(path)) {
                instance = yaml.loadAs(in, Config.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private static MariaDb launchDatabaseConfigWizard() throws IOException {
        ConsolePrompt prompt = new ConsolePrompt(terminal);
        PromptBuilder builder = prompt.getPromptBuilder();

        builder.createText()
                .addLine("Setup Database")
                .addPrompt();

        builder.createInputPrompt()
                .name("port")
                .message("What Port is MariaDB running on?")
                .defaultValue("3306")
                .addPrompt();

        builder.createInputPrompt()
                .name("user")
                .message("What is the name of the MariaDB User?")
                .defaultValue("cloudcore")
                .addPrompt();

        builder.createInputPrompt()
                .name("password")
                .message("What is the Password?")
                .mask('*')
                .addPrompt();

        Map<String, ? extends PromptResultItemIF> result = prompt.prompt(builder.build());

        String port = ((InputResult) result.get("port")).getResult();
        String user = ((InputResult) result.get("user")).getResult();
        String password = ((InputResult) result.get("password")).getResult();

        MariaDb db = new MariaDb();

        db.port = Integer.parseInt(port);
        db.user = user;
        db.password = password;

        return db;
    }

    public static Config getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Config not loaded");
        }

        return instance;
    }
}