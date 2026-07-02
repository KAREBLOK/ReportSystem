package com.reportsystem.spigot.webreplay;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Web replay dosyasini kareblok.tc API'sine yukler.
 * Lisans dogrulamasi API tarafinda yapilir.
 */
public class WebReplayUploader {

    private final JavaPlugin plugin;
    private final String apiUrl;
    private final String licenseKey;

    public WebReplayUploader(JavaPlugin plugin, String apiUrl, String licenseKey) {
        this.plugin = plugin;
        this.apiUrl = apiUrl;
        this.licenseKey = licenseKey;
    }

    /**
     * Replay dosyasini async olarak API'ye yukler.
     *
     * @param file           .packets.txt dosyasi
     * @param reportId       rapor ID
     * @param reportedPlayer raporlanan oyuncu
     * @param reporter       raporlayan oyuncu
     * @param reason         rapor sebebi
     * @param serverName     sunucu adi
     * @return viewer URL (basarili) veya null (basarisiz)
     */
    public CompletableFuture<String> uploadAsync(File file, int reportId,
                                                  String reportedPlayer, String reporter,
                                                  String reason, String serverName, int durationSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return upload(file, reportId, reportedPlayer, reporter, reason, serverName, durationSeconds);
            } catch (Exception e) {
                plugin.getLogger().warning("[WebReplay] Upload hatasi: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Senkron upload islemi.
     */
    public String upload(File file, int reportId, String reportedPlayer,
                         String reporter, String reason, String serverName, int durationSeconds) throws IOException {
        if (!file.exists()) {
            throw new IOException("Dosya bulunamadi: " + file.getAbsolutePath());
        }

        String boundary = "----ReportSystem" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000); // buyuk dosyalar icin 60sn
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("User-Agent", "ReportSystem-Plugin/1.0");

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

                // Lisans key
                writeField(writer, boundary, "license_key", licenseKey);

                // Metadata
                writeField(writer, boundary, "report_id", String.valueOf(reportId));
                writeField(writer, boundary, "reported_player", reportedPlayer);
                writeField(writer, boundary, "reporter", reporter);
                writeField(writer, boundary, "reason", reason);
                writeField(writer, boundary, "server_name", serverName);
                writeField(writer, boundary, "duration", String.valueOf(durationSeconds));

                // Dosya
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"replay_file\"; filename=\"")
                      .append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: text/plain\r\n\r\n");
                writer.flush();

                // Dosya icerigini binary olarak yaz
                Files.copy(file.toPath(), os);
                os.flush();

                writer.append("\r\n");
                writer.append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn);

            if (responseCode == 200) {
                // JSON response'dan URL'i cek: {"success":true,"url":"..."}
                String viewerUrl = extractJsonValue(responseBody, "url");
                if (viewerUrl != null) {
                    plugin.getLogger().info("[WebReplay] Upload basarili: " + viewerUrl);
                    return viewerUrl;
                } else {
                    plugin.getLogger().warning("[WebReplay] API yaniti URL icermiyor: " + responseBody);
                    return null;
                }
            } else {
                String error = extractJsonValue(responseBody, "error");
                plugin.getLogger().warning("[WebReplay] Upload basarisiz (" + responseCode + "): " +
                        (error != null ? error : responseBody));
                return null;
            }
        } finally {
            conn.disconnect();
        }
    }

    private void writeField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        writer.append(value != null ? value : "").append("\r\n");
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Basit JSON value cekme (kutuphane bagimliligini onlemek icin).
     */
    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
