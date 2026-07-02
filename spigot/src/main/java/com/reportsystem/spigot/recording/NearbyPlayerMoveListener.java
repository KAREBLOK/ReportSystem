package com.reportsystem.spigot.recording;

import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerMoveEvent listener for instant nearby player tracking
 * Opsiyonel - config'den açılabilir
 * Periyodik tracker'a ek olarak daha smooth hareket için kullanılır
 */
public class NearbyPlayerMoveListener implements Listener {

    private final ReportSystemSpigot plugin;
    private final RecordingManager recordingManager;
    private final boolean enabled;
    private final double trackingDistance;
    private final double movementThreshold;

    // Son kaydedilen location'lar (spam önlemek için)
    private final Map<UUID, Location> lastRecordedLocations = new ConcurrentHashMap<>();

    public NearbyPlayerMoveListener(ReportSystemSpigot plugin, RecordingManager recordingManager) {
        this.plugin = plugin;
        this.recordingManager = recordingManager;

        // Config'den ayarları yükle
        this.enabled = plugin.getConfig().getBoolean("replay.nearby-player-tracking.use-move-event", false);
        this.trackingDistance = plugin.getConfig().getDouble("replay.nearby-player-tracking.distance", 32.0);
        this.movementThreshold = plugin.getConfig().getDouble("replay.nearby-player-tracking.movement-threshold", 0.05);

        if (enabled) {
            plugin.getLogger().info("[NEARBY-MOVE-LISTENER] Enabled - tracking distance: " + trackingDistance);
        } else {
            plugin.getLogger().info("[NEARBY-MOVE-LISTENER] Disabled in config");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) {
            return;
        }

        Player movedPlayer = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Pozisyon değişmemişse atla (sadece yön değişimi)
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        // Tüm aktif recording session'ları kontrol et
        Map<UUID, RecordingSession> activeRecordings = recordingManager.getActiveRecordingSessions();

        if (activeRecordings.isEmpty()) {
            return; // Aktif recording yoksa işlem yapma
        }

        for (Map.Entry<UUID, RecordingSession> entry : activeRecordings.entrySet()) {
            UUID recordedPlayerUUID = entry.getKey();
            RecordingSession session = entry.getValue();

            // Hareket eden oyuncu recorded player'ın kendisi mi?
            if (movedPlayer.getUniqueId().equals(recordedPlayerUUID)) {
                continue; // Recorded player'ın hareketi zaten packet listener tarafından kaydediliyor
            }

            // Recorded player online mı?
            Player recordedPlayer = plugin.getServer().getPlayer(recordedPlayerUUID);
            if (recordedPlayer == null || !recordedPlayer.isOnline()) {
                continue;
            }

            // Aynı world'de mi?
            if (!movedPlayer.getWorld().equals(recordedPlayer.getWorld())) {
                continue;
            }

            // Mesafe kontrolü (squared distance for performance)
            double distanceSquared = to.distanceSquared(recordedPlayer.getLocation());
            double maxDistanceSquared = trackingDistance * trackingDistance;

            if (distanceSquared <= maxDistanceSquared) {
                // Nearby player hareket etti, kaydet
                recordNearbyPlayerMove(movedPlayer, to, session);
            }
        }
    }

    /**
     * Nearby player hareketini kaydeder (spam önleyerek)
     */
    private void recordNearbyPlayerMove(Player player, Location newLocation, RecordingSession session) {
        UUID playerUUID = player.getUniqueId();

        // Son kaydedilen location'ı kontrol et
        Location lastLocation = lastRecordedLocations.get(playerUUID);

        // Eğer son location yoksa veya movement threshold'u aştıysa kaydet
        if (lastLocation == null || lastLocation.distanceSquared(newLocation) > (movementThreshold * movementThreshold)) {
            NearbyPlayerAction moveAction = new NearbyPlayerAction(
                    NearbyPlayerAction.ActionType.PLAYER_MOVE,
                    player.getUniqueId(),
                    player.getName(),
                    newLocation.getX(),
                    newLocation.getY(),
                    newLocation.getZ(),
                    newLocation.getYaw(),
                    newLocation.getPitch(),
                    "", ""
            );

            session.addAction(moveAction);
            lastRecordedLocations.put(playerUUID, newLocation.clone());
        }
    }

    /**
     * Temizlik - oyuncu quit olduğunda
     */
    public void cleanup(UUID playerUUID) {
        lastRecordedLocations.remove(playerUUID);
    }

    /**
     * Tüm cache'i temizle
     */
    public void cleanupAll() {
        lastRecordedLocations.clear();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
