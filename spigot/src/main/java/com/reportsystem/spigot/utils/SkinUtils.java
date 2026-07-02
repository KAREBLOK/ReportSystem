package com.reportsystem.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class SkinUtils {

    private static final String MOJANG_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long CACHE_TTL = 10 * 60 * 1000; // 10 min

    private static final Map<String, CachedSkin> cache = new ConcurrentHashMap<>();

    public static String[] getSkinData(Player player) {
        return new String[] { "", "" };
    }

    /**
     * Fetch skin from Mojang API async.
     * Callback receives (texture, signature) on main thread. Null on failure.
     */
    public static void fetchSkin(String playerName, JavaPlugin plugin, BiConsumer<String, String> callback) {
        String key = playerName.toLowerCase();

        CachedSkin cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cached.texture, cached.signature));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String uuid = fetchUUID(playerName);
                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null, null));
                    return;
                }

                String[] skin = fetchProfile(uuid);
                if (skin == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null, null));
                    return;
                }

                cache.put(key, new CachedSkin(skin[0], skin[1]));
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(skin[0], skin[1]));

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null, null));
            }
        });
    }

    private static String fetchUUID(String playerName) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(MOJANG_UUID_URL + playerName).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) return null;

        String response = readResponse(conn);
        int idIndex = response.indexOf("\"id\"");
        if (idIndex == -1) return null;
        int start = response.indexOf("\"", idIndex + 4) + 1;
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }

    private static String[] fetchProfile(String uuid) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(MOJANG_PROFILE_URL + uuid + "?unsigned=false").openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) return null;

        String response = readResponse(conn);
        String texture = extractJsonValue(response, "value");
        String signature = extractJsonValue(response, "signature");
        if (texture == null) return null;
        return new String[]{texture, signature};
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = json.indexOf("\"", idx + search.length()) + 1;
        int end = json.indexOf("\"", start);
        if (start <= 0 || end <= start) return null;
        return json.substring(start, end);
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static class CachedSkin {
        final String texture;
        final String signature;
        final long timestamp;

        CachedSkin(String texture, String signature) {
            this.texture = texture;
            this.signature = signature;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
    }
}
