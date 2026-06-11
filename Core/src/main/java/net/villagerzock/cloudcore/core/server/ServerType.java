package net.villagerzock.cloudcore.core.server;

import net.villagerzock.cloudcore.core.config.Config;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public enum ServerType {
    PAPER(true,false,ServerType::setupPaper),
    FOLIA(true,false,ServerType::setupPaper),
    FABRIC(false,true),
    VANILLA(false,false)
    ;
    private final boolean hasPlugins;
    private final boolean hasMods;
    private final BiConsumer<Path,String> setupForVelocity;

    ServerType(boolean hasPlugins, boolean hasMods) {
        this(hasPlugins,hasMods,((path,secret) -> {}));
    }

    ServerType(boolean hasPlugins, boolean hasMods, BiConsumer<Path,String> setupForVelocity) {
        this.hasPlugins = hasPlugins;
        this.hasMods = hasMods;
        this.setupForVelocity = setupForVelocity;
    }

    public boolean hasPlugins() {
        return hasPlugins;
    }

    public boolean isModded() {
        return hasMods;
    }

    public void setupForVelocity(Path path, String secret){
        setupForVelocity.accept(path,secret);
    }


    private static void setupPaper(Path path, String secret){
        Path configFile = path.resolve("config/paper-global.yml");

        Yaml yaml = new Yaml();

        Map<String, Object> root;

        if (Files.exists(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                Object loaded = yaml.load(in);

                if (loaded instanceof Map<?, ?> map) {
                    root = (Map<String, Object>) map;
                } else {
                    root = new LinkedHashMap<>();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                Files.createDirectories(configFile.getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            root = new LinkedHashMap<>();
        }

        Map<String, Object> proxies = (Map<String, Object>) root.computeIfAbsent(
                "proxies",
                k -> new LinkedHashMap<>()
        );

        Map<String, Object> velocity = (Map<String, Object>) proxies.computeIfAbsent(
                "velocity",
                k -> new LinkedHashMap<>()
        );

        velocity.put("enabled", true);
        velocity.put("online-mode", Config.getInstance().getProxy().onlineMode);
        velocity.put("secret", secret);

        try (Writer writer = Files.newBufferedWriter(
                configFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            yaml.dump(root, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
