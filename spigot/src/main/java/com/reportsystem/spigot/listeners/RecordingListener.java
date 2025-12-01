package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.DeathAction;
import com.reportsystem.common.replay.actions.LocationAction;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class RecordingListener implements Listener {

    private final ReportSystemSpigot plugin;

    public RecordingListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Aktif kayıt var mı kontrol et
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            plugin.getLogger().info("[RECORDING] Player rejoined while recording: " + player.getName());

            // Kayıt devam ediyor bildirimi
            player.sendMessage("§e⚠ Hareketleriniz kaydedilmeye devam ediyor!");
        }

        // Pending replay var mı kontrol et (cross-server replay için)
        Integer pendingReportId = plugin.getPendingReplay(player.getUniqueId());
        if (pendingReportId != null) {
            plugin.getLogger().info("[REPLAY] Player " + player.getName() +
                    " joined with pending replay for report #" + pendingReportId);

            // Kısa bir gecikmeyle replay'i başlat (spawn tamamlansın diye)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getReplayManager().startReplay(pendingReportId, player);
                player.sendMessage("§a✦ Replay otomatik olarak başlatılıyor...");
            }, 20L); // 1 saniye gecikme
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Çıkan oyuncu kaydediliyorsa
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            plugin.getLogger().info("[RECORDING] Recorded player quit: " + player.getName());

            // Kayıt devam edecek, oyuncu geri geldiğinde kaldığı yerden devam eder
            // Ancak maksimum süre dolduğunda otomatik olarak duracak
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Ölen oyuncu kaydediliyorsa
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            RecordingSession session = plugin.getRecordingManager().getSession(player.getUniqueId());

            if (session != null) {
                Location deathLoc = player.getLocation();
                String deathMessage = event.getDeathMessage() != null ? event.getDeathMessage() : player.getName() + " died";

                // Ölüm action'ını kaydet
                DeathAction deathAction = new DeathAction(
                        deathMessage,
                        deathLoc.getX(),
                        deathLoc.getY(),
                        deathLoc.getZ()
                );
                session.addAction(deathAction);

                plugin.getLogger().info("[RECORDING-DEBUG] Player death recorded: " + deathMessage);
            }

            plugin.getLogger().info("[RECORDING] Recorded player died: " + player.getName());
            // Respawn sonrası devam edecek
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Respawn olan oyuncu kaydediliyorsa
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) {
            RecordingSession session = plugin.getRecordingManager().getSession(player.getUniqueId());

            if (session != null) {
                Location respawnLoc = event.getRespawnLocation();

                plugin.getLogger().info("[RECORDING] Recorded player respawned: " + player.getName() +
                        " at " + respawnLoc.getWorld().getName() + " " +
                        String.format("%.1f, %.1f, %.1f", respawnLoc.getX(), respawnLoc.getY(), respawnLoc.getZ()));

                // World değişti mi kontrol et
                String currentWorld = session.getWorldName();
                String newWorld = respawnLoc.getWorld().getName();
                if (!newWorld.equals(currentWorld)) {
                    plugin.getLogger().info("[RECORDING-DEBUG] World changed on respawn: " +
                            currentWorld + " -> " + newWorld);
                    session.setWorldName(newWorld);
                }

                // Respawn location'ını LocationAction olarak kaydet
                // 1 tick sonra ekle (oyuncu tam olarak respawn olduktan sonra)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    LocationAction respawnAction = new LocationAction(
                            respawnLoc.getX(),
                            respawnLoc.getY(),
                            respawnLoc.getZ(),
                            respawnLoc.getYaw(),
                            respawnLoc.getPitch(),
                            true // onGround
                    );
                    session.addAction(respawnAction);

                    plugin.getLogger().info("[RECORDING-DEBUG] Respawn LocationAction recorded: " +
                            String.format("%.1f, %.1f, %.1f", respawnLoc.getX(), respawnLoc.getY(), respawnLoc.getZ()));
                }, 1L); // 1 tick gecikme
            }
        }
    }
}