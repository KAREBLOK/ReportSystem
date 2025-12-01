package com.reportsystem.spigot.telemetry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * KAREBLOK.TC - Telemetri Yöneticisi
 * Sunucu istatistiklerini toplar ve API'ye gönderir (opsiyonel)
 */
public class TelemetryManager {

    private final ReportSystemSpigot plugin;
    private final Gson gson = new Gson();
    private final String apiUrl;
    private final boolean enabled;

    private long serverStartTime;

    public TelemetryManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("telemetry.enabled", true);
        this.apiUrl = plugin.getConfig().getString("license.api-url", "https://kareblok.tc") + "/api/license/telemetry.php";
        this.serverStartTime = System.currentTimeMillis();
    }

    /**
     * Telemetri sistemini başlatır (periyodik gönderim)
     */
    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[Telemetry] Telemetri sistemi devre dışı");
            return;
        }

        plugin.getLogger().info("[Telemetry] Telemetri sistemi aktif");

        // İlk gönderimi 1 dakika sonra yap
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            sendTelemetry();
        }, 20L * 60); // 60 saniye

        // Her 30 dakikada bir heartbeat gönder
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            sendTelemetry();
        }, 20L * 60 * 30, 20L * 60 * 30); // 30 dakika
    }

    /**
     * Telemetri verilerini toplar ve gönderir
     */
    public CompletableFuture<Boolean> sendTelemetry() {
        if (!enabled) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Veri topla
                Map<String, Object> data = collectData();

                // JSON'a çevir
                String jsonData = gson.toJson(data);

                // HTTP POST isteği gönder
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // 10 saniye
                conn.setReadTimeout(10000);

                // Veriyi gönder
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Yanıt kodunu kontrol et
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    plugin.getLogger().fine("[Telemetry] Başarıyla gönderildi");
                    return true;
                } else {
                    plugin.getLogger().warning("[Telemetry] Hata: HTTP " + responseCode);
                    return false;
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[Telemetry] Gönderim hatası: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Telemetri verilerini toplar
     */
    private Map<String, Object> collectData() {
        Map<String, Object> data = new HashMap<>();

        // Lisans bilgileri
        String licenseKey = plugin.getConfig().getString("license.license-key", "");
        String hwid = generateHWID();

        data.put("license_key", licenseKey);
        data.put("hwid", hwid);

        // Sunucu bilgileri
        data.put("server_name", plugin.getServerName());
        data.put("minecraft_version", Bukkit.getVersion());
        data.put("server_type", Bukkit.getName()); // Spigot, Paper, etc.
        data.put("plugin_version", plugin.getDescription().getVersion());
        data.put("online_mode", Bukkit.getOnlineMode() ? 1 : 0);

        // İstatistikler
        data.put("player_count", Bukkit.getOnlinePlayers().size());
        data.put("max_players", Bukkit.getMaxPlayers());

        // ReportSystem istatistikleri
        try {
            int totalReports = plugin.getReportService().getAllReports().size();
            data.put("total_reports", totalReports);
        } catch (Exception e) {
            data.put("total_reports", 0);
        }

        try {
            int activeRecordings = plugin.getRecordingManager().getActiveRecordingCount();
            data.put("total_recordings", activeRecordings);
        } catch (Exception e) {
            data.put("total_recordings", 0);
        }

        data.put("total_replays", 0); // TODO: ReplayDAO'dan çekilebilir

        // Performans
        try {
            double tps = getTPS();
            data.put("tps", tps);
        } catch (Exception e) {
            data.put("tps", 20.0);
        }

        // Bellek kullanımı
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024; // MB
        long memoryMax = runtime.maxMemory() / 1024 / 1024; // MB
        data.put("memory_used_mb", memoryUsed);
        data.put("memory_max_mb", memoryMax);

        // Özellikler
        Map<String, Boolean> features = new HashMap<>();
        features.put("recording", plugin.getConfig().getBoolean("recording.enabled", true));
        features.put("replay", plugin.getConfig().getBoolean("replay.enabled", true));
        features.put("overwatch", plugin.getConfig().getBoolean("overwatch.enabled", false));
        features.put("webhook", plugin.getConfig().getBoolean("webhook.enabled", false));
        features.put("bungeecord", plugin.getConfig().getBoolean("bungeecord.enabled", false));
        data.put("features_enabled", features);

        // Veritabanı tipi
        String dbType = plugin.getConfig().getString("database.type", "sqlite");
        data.put("database_type", dbType);

        // Sunucu çalışma süresi (dakika)
        long uptimeMinutes = (System.currentTimeMillis() - serverStartTime) / 1000 / 60;
        data.put("server_uptime_minutes", uptimeMinutes);

        return data;
    }

    /**
     * Server TPS'ini hesaplar (tahmini)
     */
    private double getTPS() {
        try {
            // Reflection ile TPS al (Paper/Spigot)
            Object server = Bukkit.getServer();
            java.lang.reflect.Field tpsField = server.getClass().getDeclaredField("recentTps");
            tpsField.setAccessible(true);
            double[] tpsArray = (double[]) tpsField.get(server);
            return Math.min(tpsArray[0], 20.0); // Son 1 dakika TPS
        } catch (Exception e) {
            // Reflection başarısız olursa 20.0 döndür
            return 20.0;
        }
    }

    /**
     * Sunucu için benzersiz bir HWID (Hardware ID) oluşturur
     */
    private String generateHWID() {
        try {
            // Sunucu özelliklerini topla
            StringBuilder hwInfo = new StringBuilder();
            hwInfo.append(System.getProperty("os.name"));
            hwInfo.append(System.getProperty("os.arch"));
            hwInfo.append(System.getProperty("os.version"));
            hwInfo.append(System.getProperty("user.name"));
            hwInfo.append(java.net.InetAddress.getLocalHost().getHostName());

            // Java özellikleri ekle
            hwInfo.append(System.getProperty("java.version"));
            hwInfo.append(System.getProperty("java.vendor"));

            // Minecraft/Bukkit özellikleri ekle
            hwInfo.append(Bukkit.getVersion());
            hwInfo.append(Bukkit.getPort());

            // SHA-256 hash oluştur
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwInfo.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Hex string'e çevir
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            plugin.getLogger().warning("HWID oluşturulurken hata: " + e.getMessage());
            return "UNKNOWN-HWID-" + System.currentTimeMillis();
        }
    }

    /**
     * Telemetri etkin mi?
     */
    public boolean isEnabled() {
        return enabled;
    }
}
