package net.villagerzock.cloudcore.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionHelper {
    public static Map<String, Map<String, String>> loadServerVersionMap() {
        try {
            System.out.println("Loading server versions from PaperMC, Fabric and Vanilla...");

            HttpClient client = HttpClient.newHttpClient();
            Gson gson = new Gson();

            Map<String, Map<String, String>> result = new HashMap<>();

            for (String project : List.of("paper", "folia")) {
                System.out.println("Loading " + project + " versions...");

                Map<String, String> versions = new HashMap<>();

                JsonObject projectInfo = gson.fromJson(
                        client.send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create("https://api.papermc.io/v2/projects/" + project))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()
                        ).body(),
                        JsonObject.class
                );

                for (JsonElement versionElement : projectInfo.getAsJsonArray("versions")) {
                    String version = versionElement.getAsString();

                    if (isSnapshotVersion(version)) {
                        continue;
                    }

                    JsonObject builds = gson.fromJson(
                            client.send(
                                    HttpRequest.newBuilder()
                                            .uri(URI.create(
                                                    "https://api.papermc.io/v2/projects/"
                                                            + project
                                                            + "/versions/"
                                                            + version
                                                            + "/builds"
                                            ))
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString()
                            ).body(),
                            JsonObject.class
                    );

                    if (!builds.has("builds")) {
                        continue;
                    }

                    JsonArray buildArray = builds.getAsJsonArray("builds");

                    if (buildArray == null || buildArray.isEmpty()) {
                        continue;
                    }

                    JsonObject latestBuild = buildArray
                            .get(buildArray.size() - 1)
                            .getAsJsonObject();

                    int build = latestBuild.get("build").getAsInt();

                    String file = latestBuild
                            .getAsJsonObject("downloads")
                            .getAsJsonObject("application")
                            .get("name")
                            .getAsString();

                    versions.put(
                            version,
                            "https://api.papermc.io/v2/projects/"
                                    + project
                                    + "/versions/"
                                    + version
                                    + "/builds/"
                                    + build
                                    + "/downloads/"
                                    + file
                    );
                }

                result.put(project, sortVersionsDescending(versions));
            }

            System.out.println("Loading fabric versions...");

            Map<String, String> fabricVersions = new HashMap<>();

            JsonArray loaders = gson.fromJson(
                    client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://meta.fabricmc.net/v2/versions/loader"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    ).body(),
                    JsonArray.class
            );

            String loaderVersion = loaders
                    .get(0)
                    .getAsJsonObject()
                    .get("version")
                    .getAsString();

            JsonArray gameVersions = gson.fromJson(
                    client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://meta.fabricmc.net/v2/versions/game"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    ).body(),
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
                    client.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    ).body(),
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
                        client.send(
                                HttpRequest.newBuilder()
                                        .uri(URI.create(versionInfo.get("url").getAsString()))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString()
                        ).body(),
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