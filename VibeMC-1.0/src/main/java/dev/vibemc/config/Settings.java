package dev.vibemc.config;

import com.google.gson.*;
import dev.vibemc.Main;

import java.io.*;
import java.nio.file.Files;

public class Settings {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Memory ────────────────────────────────────────────────────────────────
    public static int ramMb = 2048;

    // ── JVM ───────────────────────────────────────────────────────────────────
    public static String jvmArgs =
        "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 " +
        "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 " +
        "-XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 " +
        "-XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
        "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
        "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
        "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    // ── Java ──────────────────────────────────────────────────────────────────
    public static String javaPath = "";   // blank = auto-detect

    // ── Launcher window ───────────────────────────────────────────────────────
    public static int  windowWidth  = 960;
    public static int  windowHeight = 620;

    // ── Game window ───────────────────────────────────────────────────────────
    public static int     gameWidth   = 854;
    public static int     gameHeight  = 480;
    public static boolean fullscreen  = false;

    // ── Behaviour ─────────────────────────────────────────────────────────────
    public static boolean keepOpen         = false;
    public static boolean autoUpdateFabric = true;
    public static boolean showConsole      = false;

    // ── Performance limits (passed as JVM/env hints) ──────────────────────────
    // Max threads Minecraft may use (0 = unlimited)
    public static int maxCpuThreads = 0;
    // VRAM limit hint in MB (0 = unlimited, informational only — MC doesn't have a flag)
    public static int maxVramMb = 0;

    // ── Debug ─────────────────────────────────────────────────────────────────
    public static boolean debugLogging    = false;   // -Dlog4j2.level=DEBUG
    public static boolean dumpLaunchCmd   = true;    // always print launch command
    public static boolean skipHashCheck   = false;   // skip sha1 verification on assets

    // ── Auth ──────────────────────────────────────────────────────────────────
    public static String lastAccountUuid = "";

    // ── Persist / Load ────────────────────────────────────────────────────────

    public static void load() {
        if (!Main.SETTINGS_FILE.exists()) { save(); return; }
        try {
            JsonObject o = JsonParser.parseString(
                Files.readString(Main.SETTINGS_FILE.toPath())).getAsJsonObject();

            ramMb            = getInt (o, "ramMb",            ramMb);
            jvmArgs          = getStr (o, "jvmArgs",          jvmArgs);
            javaPath         = getStr (o, "javaPath",         javaPath);
            windowWidth      = getInt (o, "windowWidth",      windowWidth);
            windowHeight     = getInt (o, "windowHeight",     windowHeight);
            gameWidth        = getInt (o, "gameWidth",        gameWidth);
            gameHeight       = getInt (o, "gameHeight",       gameHeight);
            fullscreen       = getBool(o, "fullscreen",       fullscreen);
            keepOpen         = getBool(o, "keepOpen",         keepOpen);
            autoUpdateFabric = getBool(o, "autoUpdateFabric", autoUpdateFabric);
            showConsole      = getBool(o, "showConsole",      showConsole);
            maxCpuThreads    = getInt (o, "maxCpuThreads",    maxCpuThreads);
            maxVramMb        = getInt (o, "maxVramMb",        maxVramMb);
            debugLogging     = getBool(o, "debugLogging",     debugLogging);
            dumpLaunchCmd    = getBool(o, "dumpLaunchCmd",    dumpLaunchCmd);
            skipHashCheck    = getBool(o, "skipHashCheck",    skipHashCheck);
            lastAccountUuid  = getStr (o, "lastAccountUuid",  lastAccountUuid);
        } catch (Exception e) {
            System.err.println("[Settings] Load failed: " + e.getMessage());
            save();
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("ramMb",            ramMb);
        o.addProperty("jvmArgs",          jvmArgs);
        o.addProperty("javaPath",         javaPath);
        o.addProperty("windowWidth",      windowWidth);
        o.addProperty("windowHeight",     windowHeight);
        o.addProperty("gameWidth",        gameWidth);
        o.addProperty("gameHeight",       gameHeight);
        o.addProperty("fullscreen",       fullscreen);
        o.addProperty("keepOpen",         keepOpen);
        o.addProperty("autoUpdateFabric", autoUpdateFabric);
        o.addProperty("showConsole",      showConsole);
        o.addProperty("maxCpuThreads",    maxCpuThreads);
        o.addProperty("maxVramMb",        maxVramMb);
        o.addProperty("debugLogging",     debugLogging);
        o.addProperty("dumpLaunchCmd",    dumpLaunchCmd);
        o.addProperty("skipHashCheck",    skipHashCheck);
        o.addProperty("lastAccountUuid",  lastAccountUuid);
        try (Writer w = new FileWriter(Main.SETTINGS_FILE)) { GSON.toJson(o, w); }
        catch (Exception e) { System.err.println("[Settings] Save failed: " + e.getMessage()); }
    }

    /** Always 1 TB — let the user decide */
    public static int maxRamMb() { return 1048576; }

    /** Physical RAM in MB — for display only */
    public static int physicalRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return (int)(os.getTotalMemorySize() / 1024 / 1024);
        } catch (Exception e) { return 0; }
    }

    /** Number of logical CPU cores */
    public static int cpuCores() { return Runtime.getRuntime().availableProcessors(); }

    private static int    getInt (JsonObject o, String k, int    d) { return o.has(k)?o.get(k).getAsInt()    :d; }
    private static String getStr (JsonObject o, String k, String d) { return o.has(k)?o.get(k).getAsString() :d; }
    private static boolean getBool(JsonObject o, String k, boolean d){ return o.has(k)?o.get(k).getAsBoolean():d; }
}
