package com.reportsystem.spigot.replay;

import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReplayRecorder {

    private final JavaPlugin plugin;
    private final Player player;
    private final int reportId;

    private final List<ReplayAction> actions = new ArrayList<>();
    private long startTime;
    private BukkitTask recordingTask;
    private Location lastLocation;

    // Son kaydedilen değerler (gereksiz kayıtları önlemek için)
    private float lastYaw;
    private float lastPitch;

    public ReplayRecorder(JavaPlugin plugin, Player player, int reportId) {
        this.plugin = plugin;
        this.player = player;
        this.reportId = reportId;
    }

    /**
     * Kaydı başlatır
     */
    public void startRecording() {
        startTime = System.currentTimeMillis();
        lastLocation = player.getLocation();
        lastYaw = lastLocation.getYaw();
        lastPitch = lastLocation.getPitch();

        // İlk location'ı kaydet
        recordLocation();

        // Player info kaydet (skin bilgisi)
        recordPlayerInfo();

        // Her tick location kontrol et
        recordingTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline()) {
                checkAndRecordLocation();
            }
        }, 1L, 1L);

        plugin.getLogger().info("Replay kaydı başladı: " + player.getName());
    }

    /**
     * Kaydı durdurur ve Replay objesi döndürür
     */
    public Replay stopRecording() throws IOException {
        if (recordingTask != null) {
            recordingTask.cancel();
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000; // saniye cinsinden

        // Action'ları serialize et
        byte[] data = ReplaySerializer.serialize(actions, true); // compressed

        // Replay objesi oluştur - Constructor kullan
        Replay replay = new Replay(
                0, // id (otomatik atanacak)
                reportId, // reportId
                player.getName(), // recordedPlayer
                player.getUniqueId().toString(), // recordedPlayerUuid
                duration, // duration
                System.currentTimeMillis(), // createdAt
                actions.size(), // actionCount
                0, // viewCount (yeni replay için 0)
                data, // data
                true // compressed
        );

        plugin.getLogger().info("Replay kaydı durduruldu: " + player.getName() +
                " (Süre: " + duration + "s, Aksiyon: " + actions.size() + ")");

        return replay;
    }

    /**
     * Location değişimini kontrol eder ve kaydeder
     */
    private void checkAndRecordLocation() {
        Location currentLocation = player.getLocation();

        // Konum veya bakış açısı değiştiyse kaydet
        if (!currentLocation.equals(lastLocation) ||
                Math.abs(currentLocation.getYaw() - lastYaw) > 1 ||
                Math.abs(currentLocation.getPitch() - lastPitch) > 1) {

            recordLocation();
            lastLocation = currentLocation.clone();
            lastYaw = currentLocation.getYaw();
            lastPitch = currentLocation.getPitch();
        }
    }

    /**
     * Mevcut location'ı kaydeder
     */
    private void recordLocation() {
        Location loc = player.getLocation();

        LocationAction action = new LocationAction(
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                player.isOnGround()
        );

        addAction(action);
    }

    /**
     * Player bilgilerini kaydeder
     */
    private void recordPlayerInfo() {
        // Skin bilgisi için profile property'leri al
        String skinTexture = "";
        String skinSignature = "";

        PlayerInfoAction action = new PlayerInfoAction(
                player.getName(),
                skinTexture,
                skinSignature
        );

        addAction(action);
    }

    /**
     * Action ekler
     */
    public void addAction(ReplayAction action) {
        // Timestamp'i action'a set edemiyorsak, sadece listeye ekle
        // Replay oynatılırken action'lar sırayla oynatılacak
        actions.add(action);
    }

    // Getter'lar
    public int getReportId() { return reportId; }
    public int getActionCount() { return actions.size(); }
    public Player getPlayer() { return player; }
}