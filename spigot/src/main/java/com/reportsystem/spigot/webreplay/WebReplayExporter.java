package com.reportsystem.spigot.webreplay;

import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.PlayerInfoAction;
import com.reportsystem.common.replay.actions.ReplayAction;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Replay verisini async olarak .packets.txt formatina donusturup dosyaya yazar.
 * Ana thread'e dokunmaz - TPS etkisi sifir.
 */
public class WebReplayExporter {

    private final JavaPlugin plugin;
    private final File storageDir;
    private final String baseUrl;
    private final String viewerUrl;

    public WebReplayExporter(JavaPlugin plugin, String storagePath, String baseUrl, String viewerUrl) {
        this.plugin = plugin;
        this.storageDir = new File(storagePath);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.viewerUrl = viewerUrl.endsWith("/") ? viewerUrl : viewerUrl + "/";

        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    /**
     * Replay verisini async olarak donusturup dosyaya yazar.
     *
     * @param replay      veritabanindan gelen Replay objesi
     * @return viewer URL'i (CompletableFuture)
     */
    public CompletableFuture<String> exportAsync(Replay replay) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return export(replay);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[WebReplay] Export hatasi (report #" + replay.getReportId() + ")", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Senkron donusum ve dosya yazma (terrain verisi olmadan, eski uyumluluk).
     */
    public String export(Replay replay) throws IOException {
        return export(replay, null);
    }

    /**
     * Senkron donusum ve dosya yazma (terrain verisi ile).
     *
     * @param replay       veritabanindan gelen Replay objesi
     * @param terrainBlocks terrain bloklari, her eleman: {x, y, z, blockStateId}. Null olabilir.
     */
    public String export(Replay replay, List<int[]> terrainBlocks) throws IOException {
        // Aksiyonlari deserialize et
        List<ReplayAction> actions;
        try {
            actions = ReplaySerializer.deserialize(replay.getData(), replay.isCompressed());
        } catch (Exception e) {
            throw new IOException("Replay deserialize hatasi: " + e.getMessage(), e);
        }

        if (actions == null || actions.isEmpty()) {
            throw new IOException("Replay bos - aksiyon yok");
        }

        // Skin bilgisini bul
        String skinTexture = null;
        String skinSignature = null;
        for (ReplayAction action : actions) {
            if (action instanceof PlayerInfoAction && action.isMainPlayer()) {
                PlayerInfoAction info = (PlayerInfoAction) action;
                skinTexture = info.getSkinTexture();
                skinSignature = info.getSkinSignature();
                break;
            }
        }

        // Donustur
        WebReplayConverter converter = new WebReplayConverter();
        String content = converter.convert(
                actions,
                replay.getRecordedPlayer(),
                replay.getRecordedPlayerUuid(),
                skinTexture,
                skinSignature,
                terrainBlocks
        );

        if (content.isEmpty()) {
            throw new IOException("Donusum sonucu bos");
        }

        // Dosyaya yaz
        String fileName = "replay_" + replay.getReportId() + ".packets.txt";
        File outputFile = new File(storageDir, fileName);

        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(content);
        }

        plugin.getLogger().info("[WebReplay] Replay #" + replay.getReportId() +
                " basariyla export edildi: " + outputFile.getAbsolutePath() +
                " (" + (content.length() / 1024) + " KB)" +
                (terrainBlocks != null ? " [terrain: " + terrainBlocks.size() + " blok]" : " [terrain yok]"));

        // Viewer URL'i olustur
        return buildViewerUrl(fileName);
    }

    /**
     * Viewer URL'i olusturur.
     * Ornek: https://mcraft.fun/?replayFileUrl=https://cdn.site.com/replays/replay_42.packets.txt
     */
    public String buildViewerUrl(String fileName) {
        String fileUrl = baseUrl + fileName;
        return viewerUrl + "?replayFileUrl=" + fileUrl;
    }

    /**
     * Bir replay dosyasinin daha once export edilip edilmedigini kontrol eder.
     */
    public boolean isExported(int reportId) {
        String fileName = "replay_" + reportId + ".packets.txt";
        return new File(storageDir, fileName).exists();
    }

    /**
     * Export edilmis replay dosyasini siler.
     */
    public boolean deleteExport(int reportId) {
        String fileName = "replay_" + reportId + ".packets.txt";
        File file = new File(storageDir, fileName);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Mevcut bir replay icin viewer URL'i dondurur (dosya varsa).
     */
    public String getViewerUrl(int reportId) {
        String fileName = "replay_" + reportId + ".packets.txt";
        if (new File(storageDir, fileName).exists()) {
            return buildViewerUrl(fileName);
        }
        return null;
    }

    public File getStorageDir() {
        return storageDir;
    }
}
