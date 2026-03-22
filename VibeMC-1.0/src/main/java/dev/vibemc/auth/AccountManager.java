package dev.vibemc.auth;

import com.google.gson.*;
import dev.vibemc.Main;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class AccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Account> accounts = new ArrayList<>();

    public static class Account {
        public String uuid;
        public String username;
        public String accessToken;
        public String refreshToken;
        public long   tokenExpiresAt; // epoch ms

        public Account() {}
        public Account(String uuid, String username, String accessToken,
                       String refreshToken, long expiresAt) {
            this.uuid           = uuid;
            this.username       = username;
            this.accessToken    = accessToken;
            this.refreshToken   = refreshToken;
            this.tokenExpiresAt = expiresAt;
        }

        /** True if the access token is expired or will expire within 2 minutes */
        public boolean isExpired() {
            return System.currentTimeMillis() > tokenExpiresAt - 120_000;
        }

        public boolean hasRefreshToken() {
            return refreshToken != null && !refreshToken.isEmpty();
        }

        @Override public String toString() { return username; }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public static List<Account> getAll() { return accounts; }

    public static Account getByUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        return accounts.stream().filter(a -> uuid.equals(a.uuid)).findFirst().orElse(null);
    }

    /** Upsert by UUID */
    public static void add(Account a) {
        accounts.removeIf(x -> x.uuid.equals(a.uuid));
        accounts.add(a);
        save();
    }

    /** Remove by UUID — safe even if object was recreated */
    public static void remove(Account a) {
        accounts.removeIf(x -> x.uuid.equals(a.uuid));
        save();
    }

    public static void clear() {
        accounts.clear();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void load() {
        if (!Main.ACCOUNTS_FILE.exists()) return;
        try {
            String json = Files.readString(Main.ACCOUNTS_FILE.toPath());
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            accounts.clear();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                Account a = new Account();
                a.uuid           = str(o, "uuid",           "");
                a.username       = str(o, "username",       "Unknown");
                a.accessToken    = str(o, "accessToken",    "");
                a.refreshToken   = str(o, "refreshToken",   "");
                a.tokenExpiresAt = o.has("tokenExpiresAt") ? o.get("tokenExpiresAt").getAsLong() : 0;
                if (!a.uuid.isEmpty()) accounts.add(a);
            }
        } catch (Exception e) {
            System.err.println("[Accounts] Load failed: " + e.getMessage());
        }
    }

    public static void save() {
        JsonArray arr = new JsonArray();
        for (Account a : accounts) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid",           a.uuid);
            o.addProperty("username",       a.username);
            o.addProperty("accessToken",    a.accessToken);
            o.addProperty("refreshToken",   a.refreshToken);
            o.addProperty("tokenExpiresAt", a.tokenExpiresAt);
            arr.add(o);
        }
        try (Writer w = new FileWriter(Main.ACCOUNTS_FILE)) { GSON.toJson(arr, w); }
        catch (Exception e) { System.err.println("[Accounts] Save failed: " + e.getMessage()); }
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }
}
