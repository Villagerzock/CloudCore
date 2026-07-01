package net.villagerzock.cloudcore.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class VersionHelper {
    public static Map<String, Map<String, String>> loadServerVersionMap() {
        AtomicReference<HttpResponse<String>> lastResponse = new AtomicReference<>();

        try {
            System.out.println("Loading server versions from PaperMC, Fabric and Vanilla...");

            HttpClient client = HttpClient.newHttpClient();
            Gson gson = new Gson();

            java.util.function.Function<String, String> request = url -> {
                try {
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .header("User-Agent", "CloudCore/1.0.0 (marvin.sieber@rsnweb.ch)")
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

                    lastResponse.set(response);
                    return response.body();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to request " + url, e);
                }
            };

            Map<String, Map<String, String>> result = new HashMap<>();

            for (String project : List.of("paper", "folia")) {
                System.out.println("Loading " + project + " versions...");

                Map<String, String> versions = new HashMap<>();

                JsonObject projectInfo = gson.fromJson(
                        request.apply("https://fill.papermc.io/v3/projects/" + project),
                        JsonObject.class
                );

                JsonObject versionGroups = projectInfo.getAsJsonObject("versions");

                if (versionGroups == null) {
                    continue;
                }

                for (Map.Entry<String, JsonElement> versionGroup : versionGroups.entrySet()) {
                    JsonArray versionArray = versionGroup.getValue().getAsJsonArray();

                    for (JsonElement versionElement : versionArray) {
                        String version = versionElement.getAsString();

                        if (isSnapshotVersion(version)) {
                            continue;
                        }

                        JsonArray builds = gson.fromJson(
                                request.apply(
                                        "https://fill.papermc.io/v3/projects/"
                                                + project
                                                + "/versions/"
                                                + version
                                                + "/builds"
                                ),
                                JsonArray.class
                        );

                        if (builds == null || builds.isEmpty()) {
                            continue;
                        }

                        String downloadUrl = null;

                        for (JsonElement buildElement : builds) {
                            JsonObject build = buildElement.getAsJsonObject();

                            if (!build.has("channel") || !build.get("channel").getAsString().equalsIgnoreCase("STABLE")) {
                                continue;
                            }

                            JsonObject downloads = build.getAsJsonObject("downloads");

                            if (downloads == null || !downloads.has("server:default")) {
                                continue;
                            }

                            JsonObject serverDefault = downloads.getAsJsonObject("server:default");

                            if (serverDefault == null || !serverDefault.has("url")) {
                                continue;
                            }

                            downloadUrl = serverDefault.get("url").getAsString();
                            break;
                        }

                        if (downloadUrl == null) {
                            continue;
                        }

                        versions.put(version, downloadUrl);
                    }
                }

                result.put(project, sortVersionsDescending(versions));
            }

            System.out.println("Loading fabric versions...");

            Map<String, String> fabricVersions = new HashMap<>();

            JsonArray loaders = gson.fromJson(
                    request.apply("https://meta.fabricmc.net/v2/versions/loader"),
                    JsonArray.class
            );

            String loaderVersion = loaders
                    .get(0)
                    .getAsJsonObject()
                    .get("version")
                    .getAsString();

            JsonArray gameVersions = gson.fromJson(
                    request.apply("https://meta.fabricmc.net/v2/versions/game"),
                    JsonArray.class
            );

            for (JsonElement element : gameVersions) {
                JsonObject game = element.getAsJsonObject();

                if (!game.get("stable").getAsBoolean()) {
                    continue;
                }

                String version = game.get("version").getAsString();

                if (isSnapshotVersion(version)) {
                    continue;
                }

                fabricVersions.put(
                        version,
                        "https://meta.fabricmc.net/v2/versions/loader/"
                                + version
                                + "/"
                                + loaderVersion
                                + "/server/jar"
                );
            }

            result.put("fabric", sortVersionsDescending(fabricVersions));

            System.out.println("Loading vanilla versions...");

            Map<String, String> vanillaVersions = new HashMap<>();

            JsonObject vanillaManifest = gson.fromJson(
                    request.apply("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"),
                    JsonObject.class
            );

            for (JsonElement element : vanillaManifest.getAsJsonArray("versions")) {
                JsonObject versionInfo = element.getAsJsonObject();

                if (!versionInfo.get("type").getAsString().equals("release")) {
                    continue;
                }

                String version = versionInfo.get("id").getAsString();

                if (isSnapshotVersion(version)) {
                    continue;
                }

                JsonObject versionJson = gson.fromJson(
                        request.apply(versionInfo.get("url").getAsString()),
                        JsonObject.class
                );

                JsonObject downloads = versionJson.getAsJsonObject("downloads");

                if (downloads == null || !downloads.has("server")) {
                    continue;
                }

                JsonObject serverDownload = downloads.getAsJsonObject("server");

                if (serverDownload == null || !serverDownload.has("url")) {
                    continue;
                }

                vanillaVersions.put(
                        version,
                        serverDownload.get("url").getAsString()
                );
            }

            result.put("vanilla", sortVersionsDescending(vanillaVersions));

            System.out.println("Loaded server versions.");
            return result;
        } catch (Exception e) {
            HttpResponse<String> response = lastResponse.get();

            if (response != null) {
                try {
                    Path file = Files.createTempFile("cloudcore-response-", ".txt");
                    Files.writeString(file, response.body());

                    throw new RuntimeException(
                            "Failed to generate server version map\n"
                                    + "Server Response has been stored in: "
                                    + file.toAbsolutePath(),
                            e
                    );
                } catch (IOException ignored) {
                    // Falls selbst das Speichern fehlschlägt
                }
            }

            throw new RuntimeException("Failed to generate server version map", e);
        }
    }

    private static boolean isSnapshotVersion(String version) {
        String lower = version.toLowerCase();

        return lower.contains("snapshot")
                || lower.contains("pre")
                || lower.contains("rc")
                || lower.contains("-");
    }

    private static Map<String, String> sortVersionsDescending(Map<String, String> versions) {
        return versions.entrySet()
                .stream()
                .sorted((a, b) -> compareVersions(b.getKey(), a.getKey()))
                .collect(
                        java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                java.util.LinkedHashMap::new
                        )
                );
    }

    private static int compareVersions(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");

        int length = Math.max(aParts.length, bParts.length);

        for (int i = 0; i < length; i++) {
            int aValue = i < aParts.length ? parseVersionPart(aParts[i]) : 0;
            int bValue = i < bParts.length ? parseVersionPart(bParts[i]) : 0;

            if (aValue != bValue) {
                return Integer.compare(aValue, bValue);
            }
        }

        return 0;
    }

    private static int parseVersionPart(String part) {
        StringBuilder number = new StringBuilder();

        for (char c : part.toCharArray()) {
            if (!Character.isDigit(c)) {
                break;
            }

            number.append(c);
        }

        if (number.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(number.toString());
    }
}