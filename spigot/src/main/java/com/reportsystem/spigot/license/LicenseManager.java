package com.reportsystem.spigot.license;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * KAREBLOK.TC - ReportSystem Lisans Yoneticisi
 *
 * Guvenlik: HMAC-SHA256 yanit dogrulama, sifrelenmis disk cache, bypass yok.
 */
public class LicenseManager {

    private final Plugin plugin;
    private final String licenseKey;
    private final String apiUrl;
    private final String hwid;
    private final EncryptionUtil encryption;
    private final File cacheFile;

    private static final long GRACE_PERIOD_MS = 3L * 60L * 60L * 1000L; // 3 saat

    private volatile boolean isValid = false;
    private volatile boolean isChecked = false;
    private volatile String licenseStatus = "unchecked";
    private volatile String errorMessage = null;

    public LicenseManager(Plugin plugin, String licenseKey, String apiUrl) {
        this.plugin = plugin;
        this.licenseKey = licenseKey;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;

        this.hwid = generateHWID();
        this.encryption = new EncryptionUtil(licenseKey != null ? licenseKey : "", hwid);
        this.cacheFile = new File(plugin.getDataFolder(), "data/.license_cache");
    }

    /**
     * Baslangic lisans dogrulama - API ulasilamazsa plugin ACILMAZ.
     */
    public CompletableFuture<Boolean> verifyLicense() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.getLogger().info("===========================================");
                plugin.getLogger().info("KAREBLOK.TC - Lisans Dogrulama Baslatildi");
                plugin.getLogger().info("===========================================");

                if (licenseKey == null || licenseKey.isEmpty() || licenseKey.equals("RS-XXXX-XXXX-XXXX")) {
                    errorMessage = "Lisans anahtari bulunamadi! Lutfen config.yml dosyasina gecerli bir lisans anahtari girin.";
                    plugin.getLogger().severe(errorMessage);
                    isValid = false;
                    isChecked = true;
                    return false;
                }

                return doVerify(true);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Lisans dogrulama sirasinda kritik hata:", e);
                errorMessage = "Kritik hata: " + e.getMessage();
                isValid = false;
                isChecked = true;
                return false;
            }
        });
    }

    /**
     * Runtime lisans dogrulama - API ulasilamazsa cache'e bakar (3 saat grace).
     */
    public CompletableFuture<Boolean> verifyLicenseRuntime() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doVerify(false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Runtime lisans dogrulama hatasi:", e);
                return checkGracePeriod();
            }
        });
    }

    /**
     * Ana dogrulama mantigi.
     */
    private boolean doVerify(boolean isStartup) {
        try {
            String serverIp = getServerIP();

            // API istegi
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("license_key", licenseKey);
            requestBody.addProperty("hwid", hwid);
            requestBody.addProperty("server_ip", serverIp);
            requestBody.addProperty("minecraft_version", Bukkit.getVersion());
            requestBody.addProperty("plugin_version", plugin.getDescription().getVersion());
            requestBody.addProperty("server_name", Bukkit.getMotd());

            URL url = new URL(apiUrl + "/api/license/verify.php");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "ReportSystem/" + plugin.getDescription().getVersion());
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseBody = response.toString();

            // HMAC dogrulama (zorunlu)
            String receivedSignature = connection.getHeaderField("X-Signature");
            if (receivedSignature == null || receivedSignature.isEmpty()
                    || !encryption.verifyHMAC(responseBody, receivedSignature)) {
                plugin.getLogger().severe("API yanit imzasi gecersiz veya eksik!");
                errorMessage = "API yanit dogrulamasi basarisiz";
                isValid = false;
                isChecked = true;
                return false;
            }

            // JSON parse
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            boolean success = jsonResponse.get("success").getAsBoolean();
            String message = jsonResponse.get("message").getAsString();

            if (success) {
                isValid = true;
                licenseStatus = "active";
                errorMessage = null;

                // Cache kaydet
                saveCache();

                JsonObject data = jsonResponse.has("data") ? jsonResponse.getAsJsonObject("data") : new JsonObject();
                boolean firstActivation = data.has("first_activation") && data.get("first_activation").getAsBoolean();

                plugin.getLogger().info("✓ Lisans basariyla dogrulandi!");
                plugin.getLogger().info("✓ Urun: " + (data.has("product") ? data.get("product").getAsString() : "ReportSystem"));
                if (firstActivation) {
                    plugin.getLogger().info("✓ Bu sunucu icin ilk aktivasyon gerceklestirildi!");
                }
                if (data.has("expires_at") && !data.get("expires_at").isJsonNull()) {
                    plugin.getLogger().info("✓ Sure Sonu: " + data.get("expires_at").getAsString());
                } else {
                    plugin.getLogger().info("✓ Lisans Tipi: Omur Boyu");
                }
                plugin.getLogger().info("===========================================");

                isChecked = true;
                return true;

            } else {
                // API acik sekilde "gecersiz" dedi - hic grace period yok
                isValid = false;
                licenseStatus = "invalid";
                errorMessage = message;
                deleteCache();

                plugin.getLogger().severe("✗ Lisans Dogrulama Basarisiz!");
                plugin.getLogger().severe("✗ Hata: " + message);

                JsonObject data = jsonResponse.has("data") ? jsonResponse.getAsJsonObject("data") : new JsonObject();
                if (data.has("hwid_mismatch") && data.get("hwid_mismatch").getAsBoolean()) {
                    plugin.getLogger().severe("✗ Bu lisans baska bir sunucuya kayitlidir!");
                }
                plugin.getLogger().severe("===========================================");

                isChecked = true;
                return false;
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API baglanti hatasi:", e);
            errorMessage = "API baglanti hatasi: " + e.getMessage();

            if (isStartup) {
                // Baslangicta API ulasilamazsa: plugin ACILMAZ
                plugin.getLogger().severe("===========================================");
                plugin.getLogger().severe("API'ye ulasilamiyor! Eklenti acilamaz.");
                plugin.getLogger().severe("API URL: " + apiUrl);
                plugin.getLogger().severe("===========================================");
                isValid = false;
                isChecked = true;
                return false;
            } else {
                // Runtime'da API ulasilamazsa: cache'e bak (grace period)
                return checkGracePeriod();
            }
        }
    }

    // ─── Cache Sistemi ───

    private boolean checkGracePeriod() {
        long lastValid = loadCacheTimestamp();
        if (lastValid > 0) {
            long elapsed = System.currentTimeMillis() - lastValid;
            if (elapsed < GRACE_PERIOD_MS) {
                long remainingMin = (GRACE_PERIOD_MS - elapsed) / (60 * 1000);
                plugin.getLogger().warning("[KAREBLOK.TC] API ulasilamiyor, cache gecerli (kalan: " + remainingMin + " dk)");
                isValid = true;
                isChecked = true;
                return true;
            } else {
                plugin.getLogger().severe("[KAREBLOK.TC] Grace period doldu! Eklenti devre disi.");
                deleteCache();
                isValid = false;
                isChecked = true;
                return false;
            }
        } else {
            plugin.getLogger().severe("[KAREBLOK.TC] API ulasilamiyor ve gecerli cache yok!");
            isValid = false;
            isChecked = true;
            return false;
        }
    }

    private void saveCache() {
        try {
            cacheFile.getParentFile().mkdirs();
            String data = String.valueOf(System.currentTimeMillis()) + "|" + licenseKey + "|" + hwid;
            String encrypted = encryption.encrypt(data);
            try (FileWriter writer = new FileWriter(cacheFile)) {
                writer.write(encrypted);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Cache kaydetme hatasi:", e);
        }
    }

    private long loadCacheTimestamp() {
        if (!cacheFile.exists()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            String encrypted = reader.readLine();
            if (encrypted == null || encrypted.isEmpty()) return -1;

            String decrypted = encryption.decrypt(encrypted);
            String[] parts = decrypted.split("\\|");
            if (parts.length < 3) return -1;

            // HWID ve license key kontrolu - farkli makinede kullanilmasini engelle
            String cachedKey = parts[1];
            String cachedHwid = parts[2];
            if (!cachedKey.equals(licenseKey) || !cachedHwid.equals(hwid)) {
                plugin.getLogger().warning("[KAREBLOK.TC] Cache HWID/key uyusmazligi!");
                deleteCache();
                return -1;
            }

            return Long.parseLong(parts[0]);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Cache okuma hatasi:", e);
            deleteCache();
            return -1;
        }
    }

    private void deleteCache() {
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    // ─── HWID ───

    private String generateHWID() {
        try {
            StringBuilder hwInfo = new StringBuilder();
            hwInfo.append(System.getProperty("os.name"));
            hwInfo.append(System.getProperty("os.arch"));
            hwInfo.append(System.getProperty("os.version"));
            hwInfo.append(System.getProperty("user.name"));
            hwInfo.append(InetAddress.getLocalHost().getHostName());
            hwInfo.append(System.getProperty("java.version"));
            hwInfo.append(System.getProperty("java.vendor"));
            hwInfo.append(Bukkit.getVersion());
            hwInfo.append(Bukkit.getPort());

            try {
                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = interfaces.nextElement();
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        for (byte b : mac) hwInfo.append(String.format("%02X", b));
                        break;
                    }
                }
            } catch (Exception ignored) {}

            return EncryptionUtil.sha256(hwInfo.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "HWID olusturulurken hata:", e);
            return "UNKNOWN-HWID-" + System.currentTimeMillis();
        }
    }

    private String getServerIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }

    // ─── Getters ───

    public boolean isValid() { return isValid; }
    public boolean isChecked() { return isChecked; }
    public String getLicenseStatus() { return licenseStatus; }
    public String getErrorMessage() { return errorMessage; }

    public String getMaskedLicenseKey() {
        if (licenseKey == null || licenseKey.length() < 10) return "INVALID-KEY";
        String[] parts = licenseKey.split("-");
        if (parts.length != 4) return "INVALID-FORMAT";
        return parts[0] + "-" + parts[1] + "-****-****";
    }
}
