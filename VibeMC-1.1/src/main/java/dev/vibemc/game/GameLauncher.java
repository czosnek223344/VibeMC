package dev.vibemc.game;

import com.google.gson.*;
import dev.vibemc.Main;
import dev.vibemc.auth.AccountManager.Account;
import dev.vibemc.config.Settings;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GameLauncher {

    public static Process launch(Account account, PrintStream console) throws Exception {
        File profileFile = FabricSetup.getInstalledProfileFile();
        if (profileFile == null)
            throw new IOException("Fabric not installed. Click PLAY to install first.");

        JsonObject profile = JsonParser.parseString(
            Files.readString(profileFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();

        File clientJar = new File(Main.VERSIONS_DIR,
            Main.GAME_VERSION + "/" + Main.GAME_VERSION + ".jar");
        if (!clientJar.exists())
            throw new IOException("Minecraft jar missing: " + clientJar);

        // Read vanilla JSON once — used for assetIndex, libraries, game args
        JsonObject vanilla = null;
        File vanillaJson = new File(Main.VERSIONS_DIR,
            Main.GAME_VERSION + "/" + Main.GAME_VERSION + ".json");
        if (vanillaJson.exists())
            vanilla = JsonParser.parseString(Files.readString(vanillaJson.toPath(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();

        if (vanilla == null)
            throw new IOException("Vanilla " + Main.GAME_VERSION + ".json not found.\n"
                + "Delete the versions folder and reinstall via PLAY.");

        String assetIndex = Main.GAME_VERSION;
        if (vanilla.has("assetIndex"))
            assetIndex = vanilla.getAsJsonObject("assetIndex").get("id").getAsString();

        String javaExe   = resolveJava();
        String classpath = buildClasspath(profile, vanilla, clientJar);
        String mainClass = profile.has("mainClass")
            ? profile.get("mainClass").getAsString()
            : "net.minecraft.client.main.Main";

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);

        // RAM
        cmd.add("-Xmx" + Settings.ramMb + "m");
        cmd.add("-Xms" + Math.min(Settings.ramMb, 512) + "m");

        // Allow native libs (LWJGL, JNA) on Java 17+ without warnings
        cmd.add("--enable-native-access=ALL-UNNAMED");

        // CPU threads limit
        if (Settings.maxCpuThreads > 0) {
            cmd.add("-XX:ActiveProcessorCount=" + Settings.maxCpuThreads);
        }

        // Debug logging
        if (Settings.debugLogging) {
            cmd.add("-Dlog4j2.level=DEBUG");
        }

        // User JVM args — strip flags we set explicitly to avoid duplicates
        for (String arg : Settings.jvmArgs.trim().split("\\s+")) {
            if (!arg.isEmpty()
                    && !arg.startsWith("-Xmx")
                    && !arg.startsWith("-Xms")
                    && !arg.startsWith("-XX:ActiveProcessorCount")
                    && !arg.startsWith("--enable-native-access")
                    && !arg.startsWith("-Dlog4j2.level"))
                cmd.add(arg);
        }

        // Profile JVM args (Fabric injects -cp ${classpath}, natives_directory, etc)
        boolean profileInjectsClasspath = false;
        boolean profileInjectsNatives   = false;
        if (profile.has("arguments") && profile.getAsJsonObject("arguments").has("jvm")) {
            for (JsonElement el : profile.getAsJsonObject("arguments").getAsJsonArray("jvm")) {
                if (!el.isJsonPrimitive()) continue;
                String raw = el.getAsString();
                if (raw.contains("${classpath}"))        profileInjectsClasspath = true;
                if (raw.contains("natives_directory"))   profileInjectsNatives   = true;
                String arg = resolvePlaceholders(raw, account, assetIndex, classpath);
                if (!arg.isBlank()) cmd.add(arg);
            }
        }

        if (!profileInjectsClasspath) { cmd.add("-cp"); cmd.add(classpath); }

        File nativesDir = new File(Main.VERSIONS_DIR, Main.GAME_VERSION + "/natives");
        nativesDir.mkdirs();
        if (!profileInjectsNatives)
            cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());

        // Main class
        cmd.add(mainClass);

        // Game args (vanilla first, then Fabric extras)
        cmd.addAll(buildGameArgs(profile, vanilla, account, assetIndex));

        // Window size
        if (!Settings.fullscreen) {
            cmd.add("--width");  cmd.add(String.valueOf(Settings.gameWidth));
            cmd.add("--height"); cmd.add(String.valueOf(Settings.gameHeight));
        } else {
            cmd.add("--fullscreen");
        }

        String cmdStr = String.join(" ", cmd);
        if (Settings.dumpLaunchCmd) System.out.println("[VibeMC] Launching: " + cmdStr);
        if (console != null) console.println("[VibeMC] Launching: " + cmdStr);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Main.MINECRAFT_DIR);

        // Fix APPDATA — MC needs real APPDATA for crash reports / launcher profiles
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) pb.environment().put("APPDATA", appdata);
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Pipe output
        final PrintStream out = console != null ? console : System.out;
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) out.println(line);
            } catch (Exception ignored) {}
        }, "MC-Output");
        reader.setDaemon(true);
        reader.start();

        return process;
    }

    // ── Classpath ─────────────────────────────────────────────────────────────

    private static String buildClasspath(JsonObject fabric, JsonObject vanilla, File clientJar) {
        // Dedup by "group:artifact:classifier" — Fabric wins over vanilla for same artifact
        Map<String, File> byKey = new LinkedHashMap<>();

        if (vanilla != null && vanilla.has("libraries")) {
            for (JsonElement el : vanilla.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                if (lib.has("rules") && !rulesAllow(lib.getAsJsonArray("rules"))) continue;
                if (!lib.has("name")) continue;
                String name = lib.get("name").getAsString();
                File f = null;
                if (lib.has("downloads")) {
                    JsonObject downloads = lib.getAsJsonObject("downloads");
                    if (downloads.has("artifact")) {
                        String path = downloads.getAsJsonObject("artifact").has("path")
                            ? downloads.getAsJsonObject("artifact").get("path").getAsString() : null;
                        if (path != null) f = new File(Main.LIBRARIES_DIR, path);
                    }
                }
                if (f == null) f = mavenToFile(name);
                if (f != null && f.exists()) byKey.put(artifactKey(name), f);
            }
        }

        if (fabric != null && fabric.has("libraries")) {
            for (JsonElement el : fabric.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                if (lib.has("rules") && !rulesAllow(lib.getAsJsonArray("rules"))) continue;
                if (!lib.has("name")) continue;
                String name = lib.get("name").getAsString();
                File f = mavenToFile(name);
                if (f != null && f.exists()) byKey.put(artifactKey(name), f); // overwrites vanilla
            }
        }

        List<String> paths = new ArrayList<>();
        for (File f : byKey.values()) paths.add(f.getAbsolutePath());
        paths.add(clientJar.getAbsolutePath());
        return String.join(File.pathSeparator, paths);
    }

    private static String artifactKey(String name) {
        String[] p = name.split(":");
        // group:artifact[:classifier] — version intentionally excluded so Fabric overrides vanilla
        if (p.length >= 4) return p[0] + ":" + p[1] + ":" + p[3];
        return p.length >= 2 ? p[0] + ":" + p[1] : name;
    }

    // ── Game args ─────────────────────────────────────────────────────────────

    private static List<String> buildGameArgs(JsonObject fabric, JsonObject vanilla,
                                               Account account, String assetIndex) {
        // Build as ordered list of pairs to avoid value-level deduplication bugs
        List<String> args = new ArrayList<>();
        Set<String> addedKeys = new LinkedHashSet<>();

        // Vanilla first — contains auth args, game dir, assets etc
        if (vanilla != null && vanilla.has("arguments")
                && vanilla.getAsJsonObject("arguments").has("game")) {
            addGameArgElements(vanilla.getAsJsonObject("arguments").getAsJsonArray("game"),
                account, assetIndex, args, addedKeys);
        }

        // Fabric extras
        if (fabric != null && fabric.has("arguments")
                && fabric.getAsJsonObject("arguments").has("game")) {
            addGameArgElements(fabric.getAsJsonObject("arguments").getAsJsonArray("game"),
                account, assetIndex, args, addedKeys);
        }

        return args;
    }

    private static void addGameArgElements(JsonArray elements, Account account,
            String assetIndex, List<String> args, Set<String> addedKeys) {
        // Process pairs: --key value
        List<String> resolved = new ArrayList<>();
        for (JsonElement el : elements) {
            if (el.isJsonPrimitive())
                resolved.add(resolvePlaceholders(el.getAsString(), account, assetIndex, ""));
        }
        // Walk resolved args as pairs
        int i = 0;
        while (i < resolved.size()) {
            String cur = resolved.get(i);
            if (cur.startsWith("--") && i + 1 < resolved.size() && !resolved.get(i+1).startsWith("--")) {
                // key-value pair — deduplicate by key
                if (!addedKeys.contains(cur)) {
                    addedKeys.add(cur);
                    args.add(cur);
                    args.add(resolved.get(i+1));
                }
                i += 2;
            } else {
                // standalone flag
                if (!addedKeys.contains(cur)) { addedKeys.add(cur); args.add(cur); }
                i++;
            }
        }
    }

    // ── Placeholder resolution ────────────────────────────────────────────────

    private static String resolvePlaceholders(String s, Account account,
                                               String assetIndex, String classpath) {
        String assetsRoot = Main.ASSETS_DIR.getAbsolutePath();
        String nativesDir = new File(Main.VERSIONS_DIR,
            Main.GAME_VERSION + "/natives").getAbsolutePath();
        return s
            .replace("${auth_player_name}", account.username)
            .replace("${auth_uuid}",         formatUuid(account.uuid))
            .replace("${auth_access_token}", account.accessToken)
            .replace("${auth_session}",
                "token:" + account.accessToken + ":" + formatUuid(account.uuid))
            .replace("${user_type}",         "msa")
            // MC 1.21.4 game args include these — must be resolved to avoid literal placeholder strings
            .replace("${user_properties}",   "{}")   // MSA accounts have empty user properties
            .replace("${clientid}",          "")      // telemetry client ID — empty is fine
            .replace("${auth_xuid}",         "")      // Xbox UID — not captured, empty is fine
            .replace("${version_name}",      Main.GAME_VERSION)
            .replace("${game_directory}",    Main.MINECRAFT_DIR.getAbsolutePath())
            .replace("${assets_root}",       assetsRoot)
            .replace("${assets_index_name}", assetIndex)
            .replace("${game_assets}",       assetsRoot)
            .replace("${version_type}",      "release")
            .replace("${launcher_name}",     Main.LAUNCHER_NAME)
            .replace("${launcher_version}",  "1.0.0")
            .replace("${natives_directory}", nativesDir)
            .replace("${classpath}",         classpath)
            .replace("${library_directory}", Main.LIBRARIES_DIR.getAbsolutePath())
            .replace("${classpath_separator}", File.pathSeparator);
    }

    private static String formatUuid(String uuid) {
        if (uuid == null || uuid.contains("-")) return uuid != null ? uuid : "";
        if (uuid.length() != 32) return uuid;
        return uuid.substring(0,8)+"-"+uuid.substring(8,12)+"-"
             + uuid.substring(12,16)+"-"+uuid.substring(16,20)+"-"+uuid.substring(20);
    }

    // ── Java detection ────────────────────────────────────────────────────────

    public static String resolveJava() {
        if (!Settings.javaPath.isBlank()) return Settings.javaPath;

        String os = System.getProperty("os.name","").toLowerCase();
        String exe = os.contains("win") ? "java.exe" : "java";

        // Check JAVA_HOME first — but only if it's a usable version
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            File f = new File(javaHome, "bin" + File.separator + exe);
            if (f.exists() && parseJavaVersion(javaHome) >= 17)
                return f.getAbsolutePath();
        }

        // Common installation dirs per OS
        List<String> searchBases = new ArrayList<>();
        if (os.contains("win")) {
            searchBases.add("C:\\Program Files\\Eclipse Adoptium");
            searchBases.add("C:\\Program Files\\Microsoft");
            searchBases.add("C:\\Program Files\\Java");
            searchBases.add("C:\\Program Files\\Zulu");
            searchBases.add("C:\\Program Files\\BellSoft");
            searchBases.add("C:\\Program Files\\Amazon Corretto");
            searchBases.add("C:\\Program Files\\GraalVM");
        } else if (os.contains("mac")) {
            searchBases.add("/Library/Java/JavaVirtualMachines");
            searchBases.add(System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines");
            // Homebrew (Apple Silicon and Intel)
            searchBases.add("/opt/homebrew/opt");
            searchBases.add("/usr/local/opt");
        } else {
            // Linux standard paths
            searchBases.add("/usr/lib/jvm");
            searchBases.add("/usr/local/lib/jvm");
            // SDKMAN installs (most common way to get Java 25 on Linux)
            searchBases.add(System.getProperty("user.home") + "/.sdkman/candidates/java");
            searchBases.add("/opt/java");
            searchBases.add("/opt/jdk");
        }

        List<File> candidates = new ArrayList<>();
        for (String base : searchBases) {
            File dir = new File(base);
            if (!dir.exists()) continue;
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                // Mac JDK structure: Contents/Home/bin/java
                File jmac = new File(child, "Contents/Home/bin/" + exe);
                if (jmac.exists()) { candidates.add(jmac); continue; }
                File jbin = new File(child, "bin" + File.separator + exe);
                if (jbin.exists()) candidates.add(jbin);
            }
        }

        if (candidates.isEmpty()) return "java"; // fall back to PATH

        // Prefer Java 21 (minimum for MC 1.21.4), then newer versions (22, 23, 24, 25…),
        // then Java 17-20 as fallbacks, then anything older — fall back to PATH.
        // Score: lower = more preferred.
        candidates.sort((a, b) -> {
            int va = parseJavaVersion(a.getAbsolutePath());
            int vb = parseJavaVersion(b.getAbsolutePath());
            // Java 21 → 1, 22 → 2, 23 → 3, 24 → 4, 25 → 5, …  (all valid for MC 1.21.4)
            // Java 17-20 → 51-54  (minimal, may work)
            // Anything older → 200  (avoid)
            int sa = va >= 21 ? (va - 20) : va >= 17 ? (50 + (21 - va)) : 200;
            int sb = vb >= 21 ? (vb - 20) : vb >= 17 ? (50 + (21 - vb)) : 200;
            return Integer.compare(sa, sb);
        });

        // Only return if Java >= 17 (MC 1.21.4 requires 21, but 17+ may work)
        String best = candidates.get(0).getAbsolutePath();
        int bestVer = parseJavaVersion(best);
        if (bestVer < 17) {
            System.err.println("[Java] WARNING: Best found Java is " + bestVer
                + " (< 17) at " + best + " — falling back to PATH 'java'");
            return "java";
        }
        System.out.println("[Java] Using Java " + bestVer + ": " + best);
        return best;
    }

    private static int parseJavaVersion(String path) {
        // Match version number from many JDK naming conventions:
        // Temurin: jdk-21.0.5  Zulu: zulu21.40  Liberica: liberica-jdk-21  Corretto: corretto-21
        // Microsoft: microsoft-jdk-21  Semeru: semeru-17  Oracle: jdk-21 / jre-21
        // Homebrew: openjdk@25  GraalVM: graalvm-jdk-21
        java.util.regex.Pattern[] patterns = {
            java.util.regex.Pattern.compile("[Jj][Dd][Kk][-_.]?(\\d+)"),
            java.util.regex.Pattern.compile("[Jj][Rr][Ee][-_.]?(\\d+)"),
            java.util.regex.Pattern.compile("[Tt]emurin[-_.]?(\\d+)"),
            java.util.regex.Pattern.compile("[Zz]ulu(\\d+)"),
            java.util.regex.Pattern.compile("[Cc]orretto[-_.]?(\\d+)"),
            java.util.regex.Pattern.compile("[Ss]emeru[^0-9]+(\\d+)"),
            java.util.regex.Pattern.compile("[Ll]iberica[^0-9]+(\\d+)"),
            java.util.regex.Pattern.compile("[Mm]icrosoft[^0-9]+(\\d+)"),
            // Homebrew installs: openjdk@25, openjdk@21, etc.
            java.util.regex.Pattern.compile("@(\\d+)"),
        };
        for (java.util.regex.Pattern p : patterns) {
            java.util.regex.Matcher m = p.matcher(path);
            if (m.find()) {
                try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
            }
        }
        return 99; // unknown version — treated as low priority but not rejected
    }

    // ── OS helpers ────────────────────────────────────────────────────────────

    private static boolean rulesAllow(JsonArray rules) {
        boolean allowed = false;
        String os = System.getProperty("os.name","").toLowerCase();
        String osName = os.contains("win") ? "windows" : os.contains("mac") ? "osx" : "linux";
        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean matches = true;
            if (rule.has("os")) {
                String ruleOs = rule.getAsJsonObject("os").has("name")
                    ? rule.getAsJsonObject("os").get("name").getAsString() : "";
                matches = ruleOs.isEmpty() || ruleOs.equals(osName);
            }
            if ("allow".equals(action) && matches)    allowed = true;
            if ("disallow".equals(action) && matches) allowed = false;
        }
        return allowed;
    }

    private static File mavenToFile(String name) {
        try {
            String[] p = name.split(":");
            if (p.length < 3) return null;
            String group      = p[0].replace('.', '/');
            String artifact   = p[1];
            String version    = p[2];
            String classifier = p.length > 3 ? p[3] : null;
            String jar = classifier != null
                ? artifact + "-" + version + "-" + classifier + ".jar"
                : artifact + "-" + version + ".jar";
            return new File(Main.LIBRARIES_DIR,
                group + "/" + artifact + "/" + version + "/" + jar);
        } catch (Exception e) { return null; }
    }
}
