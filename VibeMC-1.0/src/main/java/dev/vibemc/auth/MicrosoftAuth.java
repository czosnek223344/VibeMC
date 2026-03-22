package dev.vibemc.auth;

import com.google.gson.*;
import dev.vibemc.auth.AccountManager.Account;
import dev.vibemc.Main;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Token-based auth — no Azure app needed.
 *
 * Auth chain (same as mineflayer/BrewBot internally):
 *   1. MS OAuth refresh_token → new MS access_token + refresh_token
 *   2. MS access_token → Xbox Live token  (RpsTicket MUST have "d=" prefix for OAuth tokens)
 *   3. XBL token → XSTS token
 *   4. XSTS token → Minecraft access_token
 *   5. MC access_token → /minecraft/profile  (UUID + username, REQUIRED for valid session)
 *
 * The "invalid session" error on servers is caused by steps 2-5 being wrong.
 */
public class MicrosoftAuth {

    private static final String CLIENT_ID      = "00000000402b5328";
    private static final String PRISM_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
    // MUST be XboxLive.signin scope — NOT the old service:: scope
    // The service:: scope produces a different token type that breaks modern session auth
    private static final String SCOPE = "XboxLive.signin offline_access";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final Gson GSON = new GsonBuilder().create();

    // ── Scan tokens folder ────────────────────────────────────────────────────

    public static List<Account> scanTokensFolder() {
        File tokensDir = getTokensDir();
        tokensDir.mkdirs();
        System.out.println("[Auth] Scanning: " + tokensDir.getAbsolutePath());

        List<Account> result = new ArrayList<>();
        File[] playerDirs = tokensDir.listFiles(File::isDirectory);
        if (playerDirs == null || playerDirs.length == 0) {
            System.out.println("[Auth] No player folders found.");
            return result;
        }
        for (File dir : playerDirs) {
            System.out.println("[Auth] Checking: " + dir.getName());
            try {
                Account acc = loadAccountFromDir(dir);
                if (acc != null) {
                    System.out.println("[Auth] Loaded: " + acc.username
                        + " (expired=" + acc.isExpired()
                        + ", hasRefresh=" + acc.hasRefreshToken() + ")");
                    result.add(acc);
                }
            } catch (Exception e) {
                System.err.println("[Auth] Failed to load " + dir.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    public static Account loadAccountFromDir(File playerDir) throws Exception {
        File mcaFile = findCacheFile(playerDir, "mca");
        if (mcaFile == null) {
            System.out.println("[Auth] No mca-cache in " + playerDir.getName()
                + " — files: " + Arrays.toString(playerDir.listFiles()));
            return null;
        }

        JsonObject root = JsonParser.parseString(
            Files.readString(mcaFile.toPath()).trim()).getAsJsonObject();
        JsonObject mca = root.has("mca") ? root.getAsJsonObject("mca") : root;

        String accessToken = str(mca, "access_token", "");
        if (accessToken.isEmpty()) {
            System.out.println("[Auth] No access_token in " + mcaFile.getName());
            return null;
        }

        // "username" in mca-cache is actually the UUID (Mojang naming)
        String uuid     = str(mca, "username", playerDir.getName());
        String username = playerDir.getName();

        // Correct expiry: obtainedOn (epoch ms) + expires_in (seconds) * 1000
        long expiresAt = System.currentTimeMillis() + 86400_000L;
        if (mca.has("expires_in") && mca.has("obtainedOn")) {
            expiresAt = mca.get("obtainedOn").getAsLong()
                      + mca.get("expires_in").getAsLong() * 1000L;
        } else if (mca.has("expires_in")) {
            expiresAt = System.currentTimeMillis() + mca.get("expires_in").getAsLong() * 1000L;
        }

        // Read refresh token from live-cache
        String refreshToken = "";
        File liveFile = findCacheFile(playerDir, "live");
        if (liveFile != null) {
            try {
                JsonObject liveRoot = JsonParser.parseString(
                    Files.readString(liveFile.toPath()).trim()).getAsJsonObject();
                JsonObject live = liveRoot.has("live") ? liveRoot.getAsJsonObject("live") : liveRoot;
                refreshToken = str(live, "refresh_token", "");
            } catch (Exception e) {
                System.err.println("[Auth] Could not read live-cache: " + e.getMessage());
            }
        }

        return new Account(uuid, username, accessToken, refreshToken, expiresAt);
    }

    // ── Full refresh chain ────────────────────────────────────────────────────

    /**
     * Refreshes an expired account using refresh_token from live-cache.
     * Performs the full MSA → XBL → XSTS → MC → profile chain.
     * Fixes "invalid session" by using correct scope, d= prefix, and profile UUID.
     */
    public static Account refresh(Account old) throws Exception {
        if (!old.hasRefreshToken())
            throw new Exception("No refresh token. Import fresh tokens from BrewBot or Minecraft Launcher.");

        System.out.println("[Auth] Refreshing: " + old.username);

        // Try PRISM_CLIENT_ID first (device code logins), fall back to legacy CLIENT_ID (BrewBot)
        // Microsoft allows refresh with either ID for personal accounts
        String msToken = null, newRefresh = null;
        for (String clientId : new String[]{PRISM_CLIENT_ID, CLIENT_ID}) {
            try {
                String msResp = post(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    "client_id=" + urlEncode(clientId)
                        + "&refresh_token=" + urlEncode(old.refreshToken)
                        + "&grant_type=refresh_token"
                        + "&scope=" + urlEncode(SCOPE),
                    "application/x-www-form-urlencoded"
                );
                JsonObject msJson = JsonParser.parseString(msResp).getAsJsonObject();
                if (msJson.has("access_token")) {
                    msToken    = msJson.get("access_token").getAsString();
                    newRefresh = str(msJson, "refresh_token", old.refreshToken);
                    System.out.println("[Auth] MS token obtained with clientId=" + clientId);
                    break;
                }
            } catch (Exception e) {
                System.out.println("[Auth] clientId=" + clientId + " failed: " + e.getMessage());
            }
        }
        if (msToken == null)
            throw new Exception("MS token refresh failed with all known client IDs.\nTry logging in again via the + button.");

        return exchangeMsTokenToAccount(msToken, newRefresh != null ? newRefresh : old.refreshToken);
    }

    // ── Import from official Minecraft Launcher ───────────────────────────────

    public static List<Account> importFromOfficialLauncher() throws Exception {
        File accountsFile = findLauncherAccountsFile();
        if (accountsFile == null)
            throw new IOException("Minecraft Launcher not found. Install it and log in first.");

        System.out.println("[Auth] Reading launcher accounts: " + accountsFile.getAbsolutePath());
        String raw = Files.readString(accountsFile.toPath());
        System.out.println("[Auth] Preview: " + raw.substring(0, Math.min(300, raw.length())));
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

        if (!root.has("accounts"))
            throw new IOException("No accounts found. Log in to the official launcher first.");

        List<Account> result = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("accounts").entrySet()) {
            try {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject acc = entry.getValue().getAsJsonObject();

                // Skip non-MSA accounts
                if (acc.has("type") && !acc.get("type").getAsString().equalsIgnoreCase("MSA"))
                    continue;

                String username = null, uuid = null;
                for (String key : new String[]{"minecraftProfile", "profile"}) {
                    if (acc.has(key) && acc.get(key).isJsonObject()) {
                        JsonObject p = acc.getAsJsonObject(key);
                        if (username == null && p.has("name")) username = p.get("name").getAsString();
                        if (uuid     == null && p.has("id"))   uuid     = p.get("id").getAsString();
                    }
                }
                if (username == null && acc.has("username")) username = acc.get("username").getAsString();
                if (username == null) username = "Unknown";
                if (uuid     == null) uuid     = entry.getKey();

                String accessToken  = str(acc, "accessToken",  "");
                String refreshToken = str(acc, "refreshToken", "");

                if (accessToken.isEmpty() && refreshToken.isEmpty()) {
                    System.out.println("[Auth] Skipping " + username + " — no tokens. Open launcher first.");
                    continue;
                }

                long expiresAt = System.currentTimeMillis() + 86400_000L;
                if (acc.has("accessTokenExpiresAt")) {
                    try { expiresAt = java.time.Instant.parse(
                        acc.get("accessTokenExpiresAt").getAsString()).toEpochMilli();
                    } catch (Exception ignored) {}
                }

                // If access token is empty, refresh now to get a valid one
                if (accessToken.isEmpty()) {
                    System.out.println("[Auth] No access token for " + username + " — refreshing...");
                    Account tmp = new Account(uuid, username, "", refreshToken, 0);
                    tmp          = refresh(tmp);
                    accessToken  = tmp.accessToken;
                    refreshToken = tmp.refreshToken;
                    uuid         = tmp.uuid;         // use UUID from profile endpoint
                    username     = tmp.username;
                    expiresAt    = tmp.tokenExpiresAt;
                }

                if (accessToken.isEmpty()) {
                    System.out.println("[Auth] Could not get token for " + username);
                    continue;
                }

                saveToTokensFolder(username, accessToken, uuid, refreshToken, expiresAt);
                result.add(new Account(uuid, username, accessToken, refreshToken, expiresAt));
                System.out.println("[Auth] Imported: " + username);
            } catch (Exception e) {
                System.err.println("[Auth] Skipped " + entry.getKey() + ": " + e.getMessage());
            }
        }

        if (result.isEmpty())
            throw new IOException("No valid accounts found.\n"
                + "Log in to the official Minecraft Launcher and launch the game once.");
        return result;
    }

    public static boolean officialLauncherFound() {
        return findLauncherAccountsFile() != null;
    }

    private static File findLauncherAccountsFile() {
        List<File> candidates = new ArrayList<>();
        String appdata   = System.getenv("APPDATA");
        String localData = System.getenv("LOCALAPPDATA");
        String home      = System.getProperty("user.home");

        // Windows
        if (appdata != null) {
            candidates.add(new File(appdata + "\\.minecraft\\launcher_accounts.json"));
            candidates.add(new File(appdata + "\\.minecraft\\launcher_accounts_microsoft_store.json"));
        }
        if (localData != null) {
            candidates.add(new File(localData,
                "Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\minecraft\\launcher_accounts.json"));
        }
        // macOS
        candidates.add(new File(home, "Library/Application Support/minecraft/launcher_accounts.json"));
        // Linux
        candidates.add(new File(home, ".minecraft/launcher_accounts.json"));

        for (File f : candidates)
            if (f.exists() && f.length() > 10) return f;
        return null;
    }

    // ── Persist tokens to disk ────────────────────────────────────────────────

    public static void saveToTokensFolder(String username, String accessToken,
                                           String uuid, String refreshToken, long expiresAt) {
        try {
            File playerDir = new File(getTokensDir(), username);
            playerDir.mkdirs(); // CRITICAL — must exist before writing files

            // mca-cache
            JsonObject inner = new JsonObject();
            inner.addProperty("access_token", accessToken);
            inner.addProperty("username",     uuid);
            inner.addProperty("expires_in",   Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000));
            inner.addProperty("token_type",   "Bearer");
            inner.addProperty("obtainedOn",   System.currentTimeMillis());
            JsonObject mcaRoot = new JsonObject();
            mcaRoot.add("mca", inner);

            File mcaFile = findCacheFile(playerDir, "mca");
            if (mcaFile == null) mcaFile = new File(playerDir, "mca-cache");
            Files.writeString(mcaFile.toPath(), prettyJson(mcaRoot));
            System.out.println("[Auth] Saved mca-cache for " + username);

            // live-cache (refresh token)
            if (!refreshToken.isEmpty()) {
                JsonObject live = new JsonObject();
                live.addProperty("refresh_token", refreshToken);
                JsonObject liveRoot = new JsonObject();
                liveRoot.add("live", live);

                File liveFile = findCacheFile(playerDir, "live");
                if (liveFile == null) liveFile = new File(playerDir, "live-cache");
                Files.writeString(liveFile.toPath(), prettyJson(liveRoot));
            }
        } catch (Exception e) {
            System.err.println("[Auth] Could not save tokens for " + username + ": " + e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static File getTokensDir() {
        return new File(Main.BASE_DIR, "tokens");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static File findCacheFile(File dir, String keyword) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files)
            if (f.isFile() && f.getName().toLowerCase().contains(keyword.toLowerCase()))
                return f;
        return null;
    }

    private static JsonObject xblBody(String rpsTicket) {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName",   "user.auth.xboxlive.com");
        props.addProperty("RpsTicket",  rpsTicket); // caller must prepend "d=" for OAuth tokens
        JsonObject body = new JsonObject();
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType",    "JWT");
        body.add("Properties", props);
        return body;
    }

    private static JsonObject xstsBody(String xblToken) {
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", tokens);
        JsonObject body = new JsonObject();
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType",    "JWT");
        body.add("Properties", props);
        return body;
    }

    private static String xstsErrorMsg(long code) {
        if (code == 2148916233L) return "Microsoft account has no Xbox profile. Go to xbox.com and create one.";
        if (code == 2148916235L) return "Xbox Live is unavailable in your country.";
        if (code == 2148916236L) return "Account needs age verification.";
        if (code == 2148916237L) return "Account needs parental consent.";
        if (code == 2148916238L) return "Child account must be added to a family.";
        return "Unknown XSTS error code " + code;
    }

    /** Like post() but returns body even on 4xx — needed for polling endpoints */
    private static String postRaw(String url, String body, String ct) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", ct)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(25))
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static String post(String url, String body, String ct) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", ct)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(25))
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[Auth] POST " + url + " → " + resp.statusCode());
        if (resp.statusCode() >= 400)
            throw new IOException("HTTP " + resp.statusCode() + " from " + url + ": " + resp.body());
        return resp.body();
    }

    private static String getWithAuth(String url, String bearerToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + bearerToken)
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(25))
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[Auth] GET " + url + " → " + resp.statusCode());
        if (resp.statusCode() >= 400)
            throw new IOException("HTTP " + resp.statusCode() + " from " + url + ": " + resp.body());
        return resp.body();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key, String def) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull())
            ? o.get(key).getAsString() : def;
    }

    private static String prettyJson(JsonObject o) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    // ── Device code login (Prism's method) ───────────────────────────────────

    public static class DeviceCodeInfo {
        public final String userCode;
        public final String verificationUrl;
        public final String deviceCode;
        public final int    intervalSeconds;
        public DeviceCodeInfo(String userCode, String verificationUrl,
                              String deviceCode, int intervalSeconds) {
            this.userCode        = userCode;
            this.verificationUrl = verificationUrl;
            this.deviceCode      = deviceCode;
            this.intervalSeconds = intervalSeconds;
        }
    }

    /**
     * Step 1 of device code flow — get a code to show the user.
     * Uses Prism Launcher's client ID (works, no Azure registration needed).
     */
    public static DeviceCodeInfo requestDeviceCode() throws Exception {
        String resp = post(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            "client_id=" + urlEncode(PRISM_CLIENT_ID)
                + "&scope=" + urlEncode(SCOPE),
            "application/x-www-form-urlencoded"
        );
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
        return new DeviceCodeInfo(
            json.get("user_code").getAsString(),
            json.get("verification_uri").getAsString(),
            json.get("device_code").getAsString(),
            json.has("interval") ? json.get("interval").getAsInt() : 5
        );
    }

    /**
     * Step 2 — poll until user completes login, then do full auth chain.
     * Call from background thread; statusCallback runs on same thread.
     */
    public static Account pollDeviceCode(DeviceCodeInfo info,
            java.util.function.Consumer<String> statusCallback) throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
        long deadline = System.currentTimeMillis() + 15 * 60 * 1000L;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(info.intervalSeconds * 1000L);
            // Use postRaw — authorization_pending comes back as HTTP 400 JSON, not an error
            String resp = postRaw(tokenUrl,
                "client_id=" + urlEncode(PRISM_CLIENT_ID)
                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&device_code=" + urlEncode(info.deviceCode),
                "application/x-www-form-urlencoded"
            );
            JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
            if (json.has("error")) {
                String error = json.get("error").getAsString();
                if ("authorization_pending".equals(error)) continue;
                if ("slow_down".equals(error)) { Thread.sleep(5000); continue; }
                throw new Exception("Login failed: " + str(json, "error_description", error));
            }
            if (!json.has("access_token"))
                throw new Exception("Unexpected response: " + resp);
            if (statusCallback != null) statusCallback.accept("Authenticating with Xbox Live...");
            return exchangeMsTokenToAccount(
                json.get("access_token").getAsString(),
                str(json, "refresh_token", "")
            );
        }
        throw new Exception("Login timed out. Please try again.");
    }

    /** Completes XBL → XSTS → MC → profile from an MS access token */
    private static Account exchangeMsTokenToAccount(String msToken,
                                                     String refreshToken) throws Exception {
        // XBL
        String xblResp = post("https://user.auth.xboxlive.com/user/authenticate",
            GSON.toJson(xblBody("d=" + msToken)), "application/json");
        JsonObject xbl  = JsonParser.parseString(xblResp).getAsJsonObject();
        String xblToken = xbl.get("Token").getAsString();
        String uhs      = xbl.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        // XSTS
        String xstsResp = post("https://xsts.auth.xboxlive.com/xsts/authorize",
            GSON.toJson(xstsBody(xblToken)), "application/json");
        JsonObject xstsJson = JsonParser.parseString(xstsResp).getAsJsonObject();
        if (xstsJson.has("XErr"))
            throw new Exception(xstsErrorMsg(xstsJson.get("XErr").getAsLong()));
        String xstsToken = xstsJson.get("Token").getAsString();

        // MC token
        String mcResp = post("https://api.minecraftservices.com/authentication/login_with_xbox",
            "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}",
            "application/json");
        JsonObject mc      = JsonParser.parseString(mcResp).getAsJsonObject();
        String mcToken     = mc.get("access_token").getAsString();
        long   expiresIn   = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400L;
        long   expiresAt   = System.currentTimeMillis() + expiresIn * 1000L;

        // MC profile (real UUID)
        String profileResp = getWithAuth(
            "https://api.minecraftservices.com/minecraft/profile", mcToken);
        JsonObject profile = JsonParser.parseString(profileResp).getAsJsonObject();
        if (profile.has("error"))
            throw new Exception("Account doesn't own Minecraft: "
                + str(profile, "errorMessage", profileResp));

        String uuid     = str(profile, "id",   "");
        String username = str(profile, "name", "Unknown");

        saveToTokensFolder(username, mcToken, uuid, refreshToken, expiresAt);
        return new Account(uuid, username, mcToken, refreshToken, expiresAt);
    }
}
