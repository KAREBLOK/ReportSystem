package com.reportsystem.spigot.license;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * KAREBLOK.TC - ReportSystem Lisans Yöneticisi
 *
 * Bu sınıf, eklentinin lisans doğrulamasını yapar ve sunucu başlatılırken
 * API'ye istek atarak lisansın geçerliliğini kontrol eder.
 */
public class LicenseManager {

    private final Plugin plugin;
    private final String licenseKey;
    private final String apiUrl;

    private boolean isValid = false;
    private boolean isChecked = false;
    private String licenseStatus = "unchecked";
    private String errorMessage = null;

    /**
     * LicenseManager constructor
     *
     * @param plugin Plugin instance
     * @param licenseKey Config'den okunan lisans anahtarı
     * @param apiUrl API base URL (örn: https://kareblok.tc)
     */
    public LicenseManager(Plugin plugin, String licenseKey, String apiUrl) {
        this.plugin = plugin;
        this.licenseKey = licenseKey;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    /**
     * Lisansı API üzerinden doğrular (Asenkron)
     *
     * @return CompletableFuture<Boolean> - Lisans geçerliyse true
     */
    public CompletableFuture<Boolean> verifyLicense() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                plugin.getLogger().info("===========================================");
                plugin.getLogger().info("KAREBLOK.TC - Lisans Doğrulama Başlatıldı");
                plugin.getLogger().info("===========================================");

                // Lisans anahtarı kontrolü
                if (licenseKey == null || licenseKey.isEmpty() || licenseKey.equals("RS-XXXX-XXXX-XXXX")) {
                    errorMessage = "Lisans anahtarı bulunamadı! Lütfen config.yml dosyasına geçerli bir lisans anahtarı girin.";
                    plugin.getLogger().severe(errorMessage);
                    plugin.getLogger().severe("Lisans anahtarınızı https://kareblok.tc adresinden satın alabilirsiniz.");
                    isValid = false;
                    isChecked = true;
                    return false;
                }

                // HWID oluştur (sunucu donanım ID'si)
                String hwid = generateHWID();

                // Sunucu IP'sini al
                String serverIp = getServerIP();

                // API isteği için JSON body hazırla
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("license_key", licenseKey);
                requestBody.addProperty("hwid", hwid);
                requestBody.addProperty("server_ip", serverIp);
                requestBody.addProperty("minecraft_version", Bukkit.getVersion());
                requestBody.addProperty("plugin_version", plugin.getDescription().getVersion());
                requestBody.addProperty("server_name", Bukkit.getMotd()); // Server MOTD'sini kullan

                // API'ye istek gönder
                URL url = new URL(apiUrl + "/api/license/verify.php");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("User-Agent", "ReportSystem/" + plugin.getDescription().getVersion());
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // Body'yi gönder
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Yanıtı oku
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

                // JSON yanıtı parse et
                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                boolean success = jsonResponse.get("success").getAsBoolean();
                String message = jsonResponse.get("message").getAsString();

                if (success) {
                    // Lisans geçerli!
                    isValid = true;
                    licenseStatus = "active";

                    // Detaylı bilgileri al
                    JsonObject data = jsonResponse.has("data") ? jsonResponse.getAsJsonObject("data") : new JsonObject();
                    boolean firstActivation = data.has("first_activation") && data.get("first_activation").getAsBoolean();

                    plugin.getLogger().info("✓ Lisans başarıyla doğrulandı!");
                    plugin.getLogger().info("✓ Ürün: " + (data.has("product") ? data.get("product").getAsString() : "ReportSystem"));

                    if (firstActivation) {
                        plugin.getLogger().info("✓ Bu sunucu için ilk aktivasyon gerçekleştirildi!");
                    }

                    if (data.has("expires_at") && !data.get("expires_at").isJsonNull()) {
                        plugin.getLogger().info("✓ Süre Sonu: " + data.get("expires_at").getAsString());
                    } else {
                        plugin.getLogger().info("✓ Lisans Tipi: Ömür Boyu");
                    }

                    plugin.getLogger().info("===========================================");
                    plugin.getLogger().info("KAREBLOK.TC'yi tercih ettiğiniz için teşekkürler!");
                    plugin.getLogger().info("===========================================");

                } else {
                    // Lisans geçersiz
                    isValid = false;
                    licenseStatus = "invalid";
                    errorMessage = message;

                    plugin.getLogger().severe("✗ Lisans Doğrulama Başarısız!");
                    plugin.getLogger().severe("✗ Hata: " + message);

                    JsonObject data = jsonResponse.has("data") ? jsonResponse.getAsJsonObject("data") : new JsonObject();

                    if (data.has("hwid_mismatch") && data.get("hwid_mismatch").getAsBoolean()) {
                        plugin.getLogger().severe("✗ Bu lisans başka bir sunucuya kayıtlıdır!");
                        plugin.getLogger().severe("✗ Sunucu değişikliği için https://kareblok.tc/yonetim adresinden destek alın.");
                    }

                    plugin.getLogger().severe("===========================================");
                }

                isChecked = true;
                return isValid;

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Lisans doğrulaması sırasında bir hata oluştu:", e);
                errorMessage = "API bağlantı hatası: " + e.getMessage();
                isValid = false;
                isChecked = true;

                plugin.getLogger().severe("===========================================");
                plugin.getLogger().severe("UYARI: Lisans API'sine ulaşılamadı!");
                plugin.getLogger().severe("Lütfen internet bağlantınızı ve firewall ayarlarınızı kontrol edin.");
                plugin.getLogger().severe("API URL: " + apiUrl);
                plugin.getLogger().severe("===========================================");

                return false;
            }
        });
    }

    /**
     * Sunucu için benzersiz bir HWID (Hardware ID) oluşturur
     *
     * @return HWID string (SHA-256 hash)
     */
    private String generateHWID() {
        try {
            // Sunucu özelliklerini topla
            StringBuilder hwInfo = new StringBuilder();
            hwInfo.append(System.getProperty("os.name"));
            hwInfo.append(System.getProperty("os.arch"));
            hwInfo.append(System.getProperty("os.version"));
            hwInfo.append(System.getProperty("user.name"));
            hwInfo.append(InetAddress.getLocalHost().getHostName());

            // Java özellikleri ekle
            hwInfo.append(System.getProperty("java.version"));
            hwInfo.append(System.getProperty("java.vendor"));

            // Minecraft/Bukkit özellikleri ekle
            hwInfo.append(Bukkit.getVersion());
            hwInfo.append(Bukkit.getPort());

            // SHA-256 hash oluştur
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hwInfo.toString().getBytes(StandardCharsets.UTF_8));

            // Hex string'e çevir
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "HWID oluşturulurken hata:", e);
            return "UNKNOWN-HWID-" + System.currentTimeMillis();
        }
    }

    /**
     * Sunucunun IP adresini alır
     *
     * @return IP adresi string
     */
    private String getServerIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "IP adresi alınırken hata:", e);
            return "0.0.0.0";
        }
    }

    /**
     * Lisans geçerli mi?
     *
     * @return true = geçerli, false = geçersiz
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Lisans kontrolü yapıldı mı?
     *
     * @return true = kontrol yapıldı, false = henüz kontrol edilmedi
     */
    public boolean isChecked() {
        return isChecked;
    }

    /**
     * Lisans durumunu döndürür
     *
     * @return "unchecked", "active", "invalid", vb.
     */
    public String getLicenseStatus() {
        return licenseStatus;
    }

    /**
     * Hata mesajını döndürür (varsa)
     *
     * @return Hata mesajı veya null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Lisans anahtarını döndürür (gizlenmiş halde)
     *
     * @return RS-XXXX-****-**** formatında gizlenmiş key
     */
    public String getMaskedLicenseKey() {
        if (licenseKey == null || licenseKey.length() < 10) {
            return "INVALID-KEY";
        }

        String[] parts = licenseKey.split("-");
        if (parts.length != 4) {
            return "INVALID-FORMAT";
        }

        return parts[0] + "-" + parts[1] + "-****-****";
    }
}
