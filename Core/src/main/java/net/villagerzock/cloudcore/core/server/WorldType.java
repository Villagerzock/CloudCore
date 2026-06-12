package net.villagerzock.cloudcore.core.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;

public enum WorldType implements IWorldType {
    DEFAULT((worldDir, superflatType, seed) -> {
        createWorldDirectories(worldDir);
        writeServerProperties(worldDir, "minecraft:normal", null, seed);
    }),

    SUPERFLAT((worldDir, superflatType, seed) -> {
        createWorldDirectories(worldDir);
        writeServerProperties(worldDir, "minecraft:flat", createSuperflatSettings(superflatType), seed);
    });

    private final IWorldType worldTypeGenerator;

    WorldType(IWorldType worldTypeGenerator) {
        this.worldTypeGenerator = worldTypeGenerator;
    }

    @Override
    public void create(Path worldDir, String superflatType, String seed) throws IOException {
        worldTypeGenerator.create(worldDir, superflatType, seed);
    }

    private static void createWorldDirectories(Path worldDir) throws IOException {
        Files.createDirectories(worldDir);
        Files.createDirectories(worldDir.resolve("datapacks"));
    }

    private static void writeServerProperties(Path worldDir, String levelType, String generatorSettings, String seedText) throws IOException {
        Properties properties = new Properties();

        properties.setProperty("level-name", worldDir.getFileName().toString());
        properties.setProperty("level-seed", String.valueOf(parseSeed(seedText)));
        properties.setProperty("level-type", levelType);

        if (generatorSettings != null && !generatorSettings.isBlank()) {
            properties.setProperty("generator-settings", generatorSettings);
        }

        properties.setProperty("online-mode", "false");
        properties.setProperty("enforce-secure-profile", "false");
        properties.setProperty("enable-command-block", "true");
        properties.setProperty("spawn-protection", "0");

        try (OutputStream out = Files.newOutputStream(worldDir.resolve("server.properties"))) {
            properties.store(out, "CloudCore generated server.properties");
        }
    }

    private static String createSuperflatSettings(String superflatType) {
        boolean isVoid = superflatType == null
                || superflatType.isBlank()
                || superflatType.equalsIgnoreCase("void")
                || superflatType.equalsIgnoreCase("the_void")
                || superflatType.equalsIgnoreCase("minecraft:void")
                || superflatType.equalsIgnoreCase("minecraft:the_void");

        if (isVoid) {
            return """
					{"biome":"minecraft:the_void","features":false,"lakes":false,"layers":[]}
					""".trim();
        }

        return """
				{"biome":"minecraft:plains","features":false,"lakes":false,"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
				""".trim();
    }

    private static long parseSeed(String seedText) {
        if (seedText == null || seedText.isBlank()) {
            return new Random().nextLong();
        }

        try {
            return Long.parseLong(seedText);
        } catch (NumberFormatException ignored) {
            return Objects.hash(seedText);
        }
    }
}