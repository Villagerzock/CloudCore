package net.villagerzock.cloudcore.core.config;

import lombok.Getter;
import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.cloudcore.core.console.ConsolePrompts;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {

    private static Config instance;

    @Getter
    public MariaDb mariadb = new MariaDb();

    @Getter
    public ProxyConfig proxy = new ProxyConfig();

    @Getter
    public LobbyConfig lobby = new LobbyConfig();

    @Getter
    public boolean useWebPanel = true;

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
        @Getter
        public String maintenanceMotd = "<red>This Server is under Maintenance!";
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
        String port = ConsolePrompts.input("Setup Database", "What Port is MariaDB running on?", "3306", false);
        String user = ConsolePrompts.input("Setup Database", "What is the name of the MariaDB User?", "cloudcore", false);
        String password = ConsolePrompts.input("Setup Database", "What is the Password?", "", true);

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
