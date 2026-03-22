package dev.vibemc.game;

import com.google.gson.*;
import dev.vibemc.Main;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.zip.*;

/**
 * Downloads and installs:
 *  - Minecraft 1.21.4 client jar + libraries + assets
 *  - Latest Fabric loader for 1.21.4
 *  - Writes a merged launch profile JSON
 */
public class FabricSetup {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String MC_VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_META_BASE     = "https://meta.fabricmc.net/v2";
    private static final String RESOURCES_BASE        = "https://resources.download.minecraft.net/";

    public interface ProgressCallback {
        void update(String task, int done, int total);
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * @return path to the merged Fabric profile JSON file, ready for GameLauncher
     */
    public static File setup(ProgressCallback cb) throws Exception {
        cb.update("Fetching version manifest...", 0, 100);

        // 1. Resolve Minecraft 1.21.4 version JSON URL
        String manifestJson = fetchString(MC_VERSION_MANIFEST);
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        JsonArray versions  = manifest.getAsJsonArray("versions");
        String mcJsonUrl = null;
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (Main.GAME_VERSION.equals(v.get("id").getAsString())) {
                mcJsonUrl = v.get("url").getAsString();
                break;
            }
        }
        if (mcJsonUrl == null) throw new Exception("Version " + Main.GAME_VERSION + " not found in manifest");

        cb.update("Fetching Minecraft version JSON...", 5, 100);
        String mcJson = fetchString(mcJsonUrl);
        JsonObject mcProfile = JsonParser.parseString(mcJson).getAsJsonObject();

        // 2. Download Minecraft client jar
        cb.update("Downloading Minecraft client...", 10, 100);
        JsonObject downloads = mcProfile.getAsJsonObject("downloads");
        JsonObject client    = downloads.getAsJsonObject("client");
        String clientUrl     = client.get("url").getAsString();
        String clientSha1    = client.get("sha1").getAsString();
        File versionDir      = new File(Main.VERSIONS_DIR, Main.GAME_VERSION);
        versionDir.mkdirs();
        File clientJar = new File(versionDir, Main.GAME_VERSION + ".jar");
        downloadIfMissing(clientUrl, clientJar, clientSha1, cb, 10, 30);

        // Save vanilla version JSON
        File mcJsonFile = new File(versionDir, Main.GAME_VERSION + ".json");
        if (!mcJsonFile.exists()) Files.writeString(mcJsonFile.toPath(), mcJson);

        // 3. Download libraries
        cb.update("Downloading libraries...", 30, 100);
        JsonArray libs = mcProfile.getAsJsonArray("libraries");
        downloadLibraries(libs, cb, 30, 60);

        // 4. Download assets
        cb.update("Downloading assets...", 60, 100);
        String assetIndex    = mcProfile.getAsJsonObject("assetIndex").get("id").getAsString();
        String assetIndexUrl = mcProfile.getAsJsonObject("assetIndex").get("url").getAsString();
        downloadAssets(assetIndex, assetIndexUrl, cb, 60, 85);

        // 5. Get latest Fabric loader
        cb.update("Fetching latest Fabric loader...", 85, 100);
        String fabricLoaderVersion = getLatestFabricLoader();
        File fabricProfileFile = installFabric(fabricLoaderVersion, mcProfile, cb, 85, 97);

        cb.update("Done!", 100, 100);
        return fabricProfileFile;
    }

    // ── Fabric ────────────────────────────────────────────────────────────────

    private static String getLatestFabricLoader() throws Exception {
        String json = fetchString(FABRIC_META_BASE + "/versions/loader/" + Main.GAME_VERSION);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        // First entry = latest stable
        for (JsonElement el : arr) {
            JsonObject obj    = el.getAsJsonObject();
            JsonObject loader = obj.getAsJsonObject("loader");
            boolean stable    = loader.has("stable") && loader.get("stable").getAsBoolean();
            if (stable) return loader.get("version").getAsString();
        }
        // Fallback: just take first
        return arr.get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
    }

    private static File installFabric(String loaderVersion, JsonObject mcProfile,
                                       ProgressCallback cb, int progStart, int progEnd) throws Exception {
        // Download Fabric profile JSON
        String url = FABRIC_META_BASE + "/versions/loader/" + Main.GAME_VERSION + "/" + loaderVersion + "/profile/json";
        cb.update("Downloading Fabric " + loaderVersion + "...", progStart, 100);
        String fabricJson = fetchString(url);
        JsonObject fabricProfile = JsonParser.parseString(fabricJson).getAsJsonObject();

        // Download Fabric libraries
        JsonArray fabricLibs = fabricProfile.getAsJsonArray("libraries");
        downloadLibraries(fabricLibs, cb, progStart, progEnd - 2);

        // Save merged profile
        String profileId = Main.GAME_VERSION + "-fabric-" + loaderVersion;
        File profileDir  = new File(Main.VERSIONS_DIR, profileId);
        profileDir.mkdirs();
        File profileFile = new File(profileDir, profileId + ".json");
        Files.writeString(profileFile.toPath(), GSON.toJson(fabricProfile));

        return profileFile;
    }

    // ── Libraries ─────────────────────────────────────────────────────────────

    private static void downloadLibraries(JsonArray libs, ProgressCallback cb,
                                           int progStart, int progEnd) {
        int total = libs.size();
        int done  = 0;
        for (JsonElement el : libs) {
            done++;
            JsonObject lib = el.getAsJsonObject();

            // Check rules (skip if not applicable to this OS)
            if (lib.has("rules") && !rulesAllow(lib.getAsJsonArray("rules"))) continue;

            // Maven artifact: group:artifact:version
            if (lib.has("name")) {
                String name = lib.get("name").getAsString();
                File dest = mavenPathToFile(name);
                if (!dest.exists()) {
                    // Check if downloads section has it
                    if (lib.has("downloads")) {
                        JsonObject downloads = lib.getAsJsonObject("downloads");
                        if (downloads.has("artifact")) {
                            JsonObject artifact = downloads.getAsJsonObject("artifact");
                            String artifactUrl  = artifact.get("url").getAsString();
                            String sha1         = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
                            try { downloadIfMissing(artifactUrl, dest, sha1, cb, progStart, progEnd); }
                            catch (Exception e) { System.err.println("[Libs] Failed: " + name + ": " + e.getMessage()); }
                        }
                    } else {
                        // Fabric / Maven central - construct URL from name
                        String url = mavenUrl(name);
                        try { downloadIfMissing(url, dest, null, cb, progStart, progEnd); }
                        catch (Exception e) { System.err.println("[Libs] Failed: " + name + ": " + e.getMessage()); }
                    }
                }
            }

            int progress = progStart + (int) ((double) done / total * (progEnd - progStart));
            cb.update("Libraries " + done + "/" + total, progress, 100);
        }
    }

    // ── Assets ────────────────────────────────────────────────────────────────

    private static void downloadAssets(String indexId, String indexUrl,
                                        ProgressCallback cb, int progStart, int progEnd) throws Exception {
        File indexDir  = new File(Main.ASSETS_DIR, "indexes");
        File objectDir = new File(Main.ASSETS_DIR, "objects");
        indexDir.mkdirs();
        objectDir.mkdirs();

        File indexFile = new File(indexDir, indexId + ".json");
        if (!indexFile.exists()) {
            String indexJson = fetchString(indexUrl);
            Files.writeString(indexFile.toPath(), indexJson);
        }

        String indexJson  = Files.readString(indexFile.toPath());
        JsonObject index  = JsonParser.parseString(indexJson).getAsJsonObject();
        JsonObject objects = index.getAsJsonObject("objects");

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(objects.entrySet());
        int total = entries.size();
        java.util.concurrent.atomic.AtomicInteger doneCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Download in parallel (4 threads) for speed
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : entries) {
            JsonObject obj  = entry.getValue().getAsJsonObject();
            String hash     = obj.get("hash").getAsString();
            String prefix   = hash.substring(0, 2);
            File dest       = new File(objectDir, prefix + "/" + hash);

            // Submit every entry so progress always reaches 100%
            futures.add(pool.submit(() -> {
                try {
                    if (!dest.exists()) {
                        String url = RESOURCES_BASE + prefix + "/" + hash;
                        downloadIfMissing(url, dest, hash, null, 0, 0);
                    }
                } catch (Exception e) {
                    System.err.println("[Assets] Failed " + hash + ": " + e.getMessage());
                } finally {
                    int d = doneCount.incrementAndGet();
                    int progress = progStart + (int) ((double) d / total * (progEnd - progStart));
                    cb.update("Assets " + d + "/" + total, progress, 100);
                }
            }));
        }

        // Wait for all downloads
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        pool.shutdown();
    }

    // ── File download ─────────────────────────────────────────────────────────

    public static void downloadIfMissing(String url, File dest, String expectedSha1,
                                          ProgressCallback cb, int progStart, int progEnd) throws Exception {
        if (dest.exists()) {
            if (expectedSha1 == null || sha1(dest).equalsIgnoreCase(expectedSha1)) return;
            dest.delete(); // re-download if hash mismatch
        }
        dest.getParentFile().mkdirs();

        if (cb != null) cb.update("Downloading " + dest.getName() + "...", progStart, 100);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + ": " + url);

        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        try (InputStream in = resp.body(); OutputStream out = new FileOutputStream(tmp)) {
            in.transferTo(out);
        }
        tmp.renameTo(dest);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean rulesAllow(JsonArray rules) {
        boolean allowed = false;
        String os = System.getProperty("os.name", "").toLowerCase();
        String osName = os.contains("win") ? "windows" : os.contains("mac") ? "osx" : "linux";

        for (JsonElement el : rules) {
            JsonObject rule   = el.getAsJsonObject();
            String action     = rule.get("action").getAsString();
            boolean matches   = true;

            if (rule.has("os")) {
                String ruleOs = rule.getAsJsonObject("os").has("name")
                        ? rule.getAsJsonObject("os").get("name").getAsString() : "";
                matches = ruleOs.isEmpty() || ruleOs.equals(osName);
            }

            if ("allow".equals(action) && matches) allowed = true;
            if ("disallow".equals(action) && matches) allowed = false;
        }
        return allowed;
    }

    private static File mavenPathToFile(String name) {
        // Supports: group:artifact:version  OR  group:artifact:version:classifier
        String[] parts   = name.split(":");
        String group     = parts[0].replace('.', '/');
        String artifact  = parts[1];
        String version   = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        String jar       = classifier != null
                ? artifact + "-" + version + "-" + classifier + ".jar"
                : artifact + "-" + version + ".jar";
        return new File(Main.LIBRARIES_DIR, group + "/" + artifact + "/" + version + "/" + jar);
    }

    private static String mavenUrl(String name) {
        String[] parts    = name.split(":");
        String group      = parts[0].replace('.', '/');
        String artifact   = parts[1];
        String version    = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        String jar        = classifier != null
                ? artifact + "-" + version + "-" + classifier + ".jar"
                : artifact + "-" + version + ".jar";
        // Try Fabric maven first for fabric artifacts, else maven central
        String base = name.startsWith("net.fabricmc") || name.startsWith("org.ow2.asm")
                ? "https://maven.fabricmc.net/"
                : "https://repo1.maven.org/maven2/";
        return base + group + "/" + artifact + "/" + version + "/" + jar;
    }

    public static String sha1(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String fetchString(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET().timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new IOException("HTTP " + resp.statusCode() + ": " + url);
        return resp.body();
    }

    /** Returns the path to the Fabric profile JSON (already installed) */
    public static File getInstalledProfileFile() {
        File versionsDir = Main.VERSIONS_DIR;
        if (!versionsDir.exists()) return null;
        File[] entries = versionsDir.listFiles();
        if (entries == null) return null;
        for (File f : entries) {
            if (f.isDirectory() && f.getName().startsWith(Main.GAME_VERSION + "-fabric-")) {
                File json = new File(f, f.getName() + ".json");
                if (json.exists()) return json;
            }
        }
        return null;
    }

    public static boolean isInstalled() {
        return getInstalledProfileFile() != null;
    }
}
