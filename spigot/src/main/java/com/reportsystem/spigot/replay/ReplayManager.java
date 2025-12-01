package com.reportsystem.spigot.replay;

import com.reportsystem.common.database.ReplayDAO;
import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.actions.LocationAction;
import com.reportsystem.common.replay.actions.ReplayAction;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.recording.RecordingManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ReplayManager {

    private final JavaPlugin plugin;
    private final ReplayDAO replayDAO;
    private final ReplayControlManager controlManager;
    private final RecordingManager recordingManager;

    // Aktif replay'ler (viewer UUID -> ReplayPlayer)
    private final Map<UUID, ReplayPlayer> activeReplays = new HashMap<>();

    // Viewer'ların orijinal konumları (replay bitince geri dönmek için)
    private final Map<UUID, Location> viewerOriginalLocations = new HashMap<>();

    // Viewer'ların orijinal sunucuları (BungeeCord - cross-server replay için)
    private final Map<UUID, String> viewerOriginalServers = new HashMap<>();

    public ReplayManager(JavaPlugin plugin, ReplayDAO replayDAO, RecordingManager recordingManager) {
        this.plugin = plugin;
        this.replayDAO = replayDAO;
        this.recordingManager = recordingManager;
        this.controlManager = new ReplayControlManager((ReportSystemSpigot) plugin, this);
    }

    /**
     * Bir oyuncu için replay kaydı başlatır
     */
    public void startRecording(Player player, int reportId, int durationSeconds, String reporterName, String reason) {
        recordingManager.startRecording(player, reportId, durationSeconds, reporterName, reason);
    }

    /**
     * Bir oyuncu için replay kaydını durdurur
     */
    public CompletableFuture<Boolean> stopRecording(Player player) {
        return recordingManager.stopRecording(player.getUniqueId());
    }

    /**
     * Bir rapor için replay başlatır
     */
    public boolean startReplay(int reportId, Player viewer) {
        try {
            plugin.getLogger().info("[REPLAY] Starting replay for report #" + reportId + " - viewer: " + viewer.getName());

            // Replay'i veritabanından çek
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

            // Cross-server kontrolü - Report'u çek ve sunucu kontrolü yap
            if (spigotPlugin.getConfigManager().isBungeeCordEnabled()) {
                try {
                    com.reportsystem.common.models.Report report = spigotPlugin.getReportService().getReportById(reportId);
                    if (report != null && report.getServerName() != null) {
                        String currentServer = spigotPlugin.getServerName();
                        String replayServer = report.getServerName();

                        // Eğer replay farklı sunucudaysa, oyuncuyu oraya gönder
                        if (!currentServer.equalsIgnoreCase(replayServer)) {
                            plugin.getLogger().info("[REPLAY] Replay is on different server. Current: " + currentServer +
                                    ", Replay: " + replayServer + " - Sending player to " + replayServer);

                            // Save original server (to return after replay ends)
                            viewerOriginalServers.put(viewer.getUniqueId(), currentServer);
                            plugin.getLogger().info("[REPLAY] Saved original server '" + currentServer + "' for " + viewer.getName());

                            // Pending replay kaydet
                            spigotPlugin.storePendingReplay(viewer.getUniqueId(), reportId);

                            // Oyuncuyu hedef sunucuya gönder
                            sendPlayerToServer(viewer, replayServer);

                            viewer.sendMessage("§e✦ Replay " + replayServer + " sunucusunda. Oraya gönderiliyorsunuz...");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[REPLAY] Could not check server name for cross-server replay", e);
                    // Hata olursa devam et, aynı sunucudaymış gibi davran
                }
            }

            Optional<Replay> replayOpt = replayDAO.getReplayByReportId(reportId);
            if (!replayOpt.isPresent()) {
                plugin.getLogger().warning("[REPLAY] Replay not found in database for report #" + reportId);
                spigotPlugin.getMessageManager().sendMessage(viewer, "replay.not-found");
                return false;
            }

            Replay replay = replayOpt.get();
            plugin.getLogger().info("[REPLAY] Replay found - ID: " + replay.getId() +
                ", Player: " + replay.getRecordedPlayer() +
                ", Compressed: " + replay.isCompressed() +
                ", Data size: " + (replay.getData() != null ? replay.getData().length : 0) + " bytes");

            // Data check
            if (replay.getData() == null || replay.getData().length == 0) {
                plugin.getLogger().severe("[REPLAY] Replay data is null or empty for report #" + reportId);
                spigotPlugin.getMessageManager().sendMessage(viewer, "replay.data-empty");
                return false;
            }

            // Already watching a replay?
            if (activeReplays.containsKey(viewer.getUniqueId())) {
                spigotPlugin.getMessageManager().sendMessage(viewer, "replay.already-watching");
                return false;
            }

            plugin.getLogger().info("[REPLAY] Creating ReplayPlayer instance...");

            // Create ReplayPlayer
            ReplayPlayer replayPlayer = new ReplayPlayer(plugin, replay);

            plugin.getLogger().info("[REPLAY] ReplayPlayer created successfully - actions count: " + replayPlayer.getActions().size());

            // Save viewer's original location (to restore after replay ends)
            viewerOriginalLocations.put(viewer.getUniqueId(), viewer.getLocation().clone());
            plugin.getLogger().info("[REPLAY] Saved original location for " + viewer.getName());

            // Find first location and calculate safe teleportation position
            Location startLocation = findSafeStartLocation(replayPlayer, viewer);

            // Start replay
            replayPlayer.start(startLocation, viewer);

            // Teleport viewer to safe position
            teleportViewerToReplayStart(viewer, startLocation, replayPlayer, reportId);

            // Give control items
            controlManager.giveControlItems(viewer, replayPlayer);

            // Add to active replays list
            activeReplays.put(viewer.getUniqueId(), replayPlayer);

            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.started");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] Database error while loading replay for report #" + reportId, e);
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-database");
            spigotPlugin.getMessageManager().sendMessage(viewer, "general.error.details", "%error%", e.getMessage());
            return false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] IOException while deserializing replay data for report #" + reportId, e);
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-read");
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-corrupt", "%error%", e.getMessage());
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] ClassNotFoundException - replay action class not found for report #" + reportId, e);
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-class");
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-version", "%error%", e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] Unexpected error while starting replay for report #" + reportId, e);
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-unexpected");
            spigotPlugin.getMessageManager().sendMessage(viewer, "general.error.details", "%error%", e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Replay başlangıcı için güvenli konum bulur
     */
    private Location findSafeStartLocation(ReplayPlayer replayPlayer, Player viewer) {
        // İlk location action'ını bul
        LocationAction firstLocation = null;
        for (ReplayAction action : replayPlayer.getActions()) {
            if (action instanceof LocationAction) {
                firstLocation = (LocationAction) action;
                break;
            }
        }

        if (firstLocation != null) {
            // Ana karakterin başlangıç konumu
            Location characterLocation = new Location(
                    viewer.getWorld(),
                    firstLocation.getX(),
                    firstLocation.getY(),
                    firstLocation.getZ(),
                    firstLocation.getYaw(),
                    firstLocation.getPitch()
            );

            // Güvenli izleyici konumu hesapla (karakterin 3-5 blok yanında)
            return calculateSafeViewerPosition(characterLocation);
        } else {
            // Fallback: viewer'ın mevcut konumu
            return viewer.getLocation();
        }
    }

    /**
     * Karakterin yanında güvenli izleyici konumu hesaplar
     */
    private Location calculateSafeViewerPosition(Location characterLocation) {
        // Farklı mesafeler ve açılar dene
        double[] distances = {3.0, 4.0, 5.0, 2.0}; // Önce 3-5 blok, sonra 2 blok
        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315}; // 8 farklı yön

        for (double distance : distances) {
            for (double angle : angles) {
                // Açıyı radyana çevir
                double radians = Math.toRadians(angle);

                // Yeni konum hesapla
                double offsetX = Math.cos(radians) * distance;
                double offsetZ = Math.sin(radians) * distance;

                Location testLocation = characterLocation.clone().add(offsetX, 0, offsetZ);

                // Güvenli mi kontrol et
                if (isSafeLocation(testLocation)) {
                    // Karaktere bakacak şekilde yaw hesapla
                    testLocation.setYaw(calculateYawToLookAt(testLocation, characterLocation));
                    testLocation.setPitch(0); // Düz bakış

                    plugin.getLogger().info("[REPLAY-TELEPORT] Güvenli konum bulundu: " +
                            String.format("%.1f, %.1f, %.1f (mesafe: %.1f, açı: %.0f°)",
                                    testLocation.getX(), testLocation.getY(), testLocation.getZ(), distance, angle));

                    return testLocation;
                }
            }
        }

        // Hiç güvenli konum bulunamadıysa, karakterin biraz üstü
        Location fallback = characterLocation.clone().add(0, 2, 0);
        plugin.getLogger().warning("[REPLAY-TELEPORT] Güvenli konum bulunamadı, fallback kullanılıyor");
        return fallback;
    }

    /**
     * Eski replay'leri siler
     */
    public void deleteOldReplays(int days) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("[REPLAY] Auto-delete check for replays older than " + days + " days");

                // ReplayDAO metodu çağır
                int deletedCount = replayDAO.deleteOldReplays(days);

                if (deletedCount > 0) {
                    plugin.getLogger().info("[REPLAY] Deleted " + deletedCount + " old replays");
                } else {
                    plugin.getLogger().info("[REPLAY] No old replays found to delete");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[REPLAY] Database error while deleting old replays", e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[REPLAY] Unexpected error while deleting old replays", e);
            }
        });
    }

    /**
     * Konumun güvenli olup olmadığını kontrol eder
     */
    private boolean isSafeLocation(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return false;

        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();

        // Oyuncunun ayakları ve kafası için blokları kontrol et
        Material groundBlock = world.getBlockAt(blockX, blockY - 1, blockZ).getType();
        Material feetBlock = world.getBlockAt(blockX, blockY, blockZ).getType();
        Material headBlock = world.getBlockAt(blockX, blockY + 1, blockZ).getType();

        // Zemin katı olmalı (air olmamalı)
        boolean hasGround = !groundBlock.isAir() && groundBlock.isSolid();

        // Ayaklar ve kafa air olmalı (geçilebilir)
        boolean feetClear = feetBlock.isAir() || isPassable(feetBlock);
        boolean headClear = headBlock.isAir() || isPassable(headBlock);

        // Y koordinatı reasonable aralıkta olmalı
        boolean validHeight = blockY >= -64 && blockY <= 320;

        // Lava veya diğer tehlikeli blokların içinde olmamalı
        boolean notDangerous = !isDangerousBlock(feetBlock) && !isDangerousBlock(headBlock);

        return hasGround && feetClear && headClear && validHeight && notDangerous;
    }

    /**
     * Blokun geçilebilir olup olmadığını kontrol eder
     */
    private boolean isPassable(Material material) {
        return material.isAir() ||
                material == Material.WATER ||
                material == Material.TALL_GRASS ||
                material == Material.SHORT_GRASS ||
                material.name().contains("SIGN") ||
                material.name().contains("TORCH");
    }

    /**
     * Blokun tehlikeli olup olmadığını kontrol eder
     */
    private boolean isDangerousBlock(Material material) {
        return material == Material.LAVA ||
                material == Material.FIRE ||
                material == Material.MAGMA_BLOCK ||
                material == Material.CACTUS ||
                material == Material.SWEET_BERRY_BUSH;
    }

    /**
     * A konumundan B konumuna bakmak için gereken yaw açısını hesaplar
     */
    private float calculateYawToLookAt(Location from, Location to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();

        double yaw = Math.atan2(-deltaX, deltaZ) * 180.0 / Math.PI;
        return (float) yaw;
    }

    /**
     * Viewer'ı replay başlangıcına teleport eder
     */
    private void teleportViewerToReplayStart(Player viewer, Location targetLocation, ReplayPlayer replayPlayer, int reportId) {
        // Teleportasyon efekti için önce particle göster
        Location currentLoc = viewer.getLocation();
        currentLoc.getWorld().spawnParticle(
                Particle.PORTAL,
                currentLoc.clone().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );

        // Ses efekti
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // 10 tick sonra teleport et (smooth geçiş için)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            viewer.teleport(targetLocation);

            // Teleportasyon sonrası efektler
            targetLocation.getWorld().spawnParticle(
                    Particle.PORTAL,
                    targetLocation.clone().add(0, 1, 0),
                    30, 0.3, 0.5, 0.3, 0.05
            );

            viewer.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);

            // Show title
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            String title = spigotPlugin.getMessageManager().getMessage("replay.title-started");
            String subtitle = spigotPlugin.getMessageManager().getMessage("replay.subtitle-started")
                    .replace("%player%", replayPlayer.getReplay().getRecordedPlayer())
                    .replace("%report_id%", String.valueOf(reportId));
            viewer.sendTitle(
                    spigotPlugin.getMessageManager().colorize(title),
                    spigotPlugin.getMessageManager().colorize(subtitle),
                    10, 40, 10);

            plugin.getLogger().info("[REPLAY-TELEPORT] " + viewer.getName() + " teleported to replay start location");

        }, 10L);
    }

    /**
     * Oyuncunun izlediği replay'i durdurur - GÜNCELLENEN METOD
     */
    public void stopReplay(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        ReplayPlayer replayPlayer = activeReplays.get(viewerUUID); // remove yerine get kullan

        if (replayPlayer != null) {
            plugin.getLogger().info("[REPLAY] Stopping replay for " + viewer.getName());

            // 1. ÖNCE ReplayPlayer'ı durdur (bu NPC'leri ve blokları temizleyecek)
            replayPlayer.stop();

            // 2. SONRA activeReplays'den kaldır
            activeReplays.remove(viewerUUID);

            // 3. Check if viewer needs to return to original server (BungeeCord)
            ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
            String originalServer = viewerOriginalServers.remove(viewerUUID);

            if (originalServer != null && spigotPlugin.getConfigManager().isBungeeCordEnabled()) {
                // Cross-server replay - send player back to original server
                String currentServer = spigotPlugin.getServerName();

                if (!currentServer.equalsIgnoreCase(originalServer)) {
                    plugin.getLogger().info("[REPLAY] Sending " + viewer.getName() +
                            " back to original server '" + originalServer + "' from '" + currentServer + "'");

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline()) {
                            viewer.sendMessage("§a✦ Replay bitti! " + originalServer + " sunucusuna geri dönüyorsunuz...");
                            sendPlayerToServer(viewer, originalServer);
                        }
                    }, 10L);

                    // Don't restore location - player is being sent to another server
                    viewerOriginalLocations.remove(viewerUUID);
                    return;
                }
            }

            // 4. Restore viewer's original location (same server)
            Location originalLocation = viewerOriginalLocations.remove(viewerUUID);
            if (originalLocation != null && viewer.isOnline()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.teleport(originalLocation);
                        plugin.getLogger().info("[REPLAY] Restored original location for " + viewer.getName());

                        // Send message
                        viewer.sendMessage("§a✦ Replay bitti! Başlangıç konumunuza döndürüldünüz.");
                    }
                }, 10L); // 10 tick bekle, NPC'lerin temizlenmesi için
            } else {
                // Fallback: Force chunk refresh if no original location saved
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (viewer.isOnline()) {
                        Location loc = viewer.getLocation();
                        viewer.teleport(loc.clone().add(0, 0.1, 0));
                        viewer.teleport(loc);
                        plugin.getLogger().info("[REPLAY] Force teleport completed for " + viewer.getName());
                    }
                }, 10L);
            }

            // Double check - activeReplays'den kaldırıldığından emin ol
            if (activeReplays.containsKey(viewerUUID)) {
                activeReplays.remove(viewerUUID);
                plugin.getLogger().warning("[REPLAY] Had to force remove " + viewer.getName() + " from activeReplays");
            }

            plugin.getLogger().info("[REPLAY] Replay stop completed for " + viewer.getName());
        } else {
            viewer.sendMessage("§cŞu anda bir replay izlemiyorsunuz!");
        }
    }

    /**
     * Oyuncunun aktif bir replay izleyip izlemediğini kontrol eder
     */
    public boolean isWatchingReplay(Player player) {
        return activeReplays.containsKey(player.getUniqueId());
    }

    /**
     * Oyuncunun izlediği replay'i döndürür
     */
    public ReplayPlayer getViewerReplay(Player player) {
        return activeReplays.get(player.getUniqueId());
    }

    /**
     * Oyuncunun kayıt edilip edilmediğini kontrol eder
     */
    public boolean isRecording(UUID playerUUID) {
        return recordingManager.isRecording(playerUUID);
    }

    /**
     * Tüm aktif kayıtları durdurur (genellikle plugin kapanırken)
     */
    public CompletableFuture<Void> stopAllRecordings() {
        return CompletableFuture.runAsync(() -> {
            recordingManager.stopAll();
        });
    }

    /**
     * Tüm aktif replay'leri durdurur (plugin kapanırken) - GÜNCELLENEN METOD
     */
    public void stopAllReplays() {
        plugin.getLogger().info("[REPLAY] Stopping all active replays");

        // Her viewer için kontrol itemlerini temizle
        for (Map.Entry<UUID, ReplayPlayer> entry : activeReplays.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) {
                controlManager.removeControlItems(viewer);
                viewer.sendMessage("§cReplay durduruldu (sunucu kapanıyor)!");
            }
        }

        // Kontrol manager'ı temizle
        controlManager.cleanup();

        // Tüm replay'leri durdur
        for (ReplayPlayer replayPlayer : activeReplays.values()) {
            replayPlayer.stop();
        }
        activeReplays.clear();

        // Tüm kaydedilmiş konumları ve sunucuları temizle
        viewerOriginalLocations.clear();
        viewerOriginalServers.clear();
        plugin.getLogger().info("[REPLAY] Cleared all saved viewer locations and servers");

        plugin.getLogger().info("Tüm aktif replay'ler durduruldu ve temizlendi.");
    }

    /**
     * Belirli bir rapor için replay olup olmadığını kontrol eder
     */
    public CompletableFuture<Boolean> hasReplay(int reportId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return replayDAO.getReplayByReportId(reportId).isPresent();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Replay kontrol hatası", e);
                return false;
            }
        });
    }

    /**
     * Replay istatistiklerini döndürür
     */
    public String getStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("§6=== Replay İstatistikleri ===\n");
        stats.append("§eAktif Kayıtlar: §f").append(recordingManager.getActiveRecordingCount()).append("\n");
        stats.append("§eAktif İzleyiciler: §f").append(activeReplays.size()).append("\n");

        if (!activeReplays.isEmpty()) {
            stats.append("\n§eİzleyenler:\n");
            for (Map.Entry<UUID, ReplayPlayer> entry : activeReplays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    ReplayPlayer replayPlayer = entry.getValue();
                    stats.append("§7- §f").append(player.getName())
                            .append(" §7(")
                            .append(replayPlayer.getReplay().getRecordedPlayer())
                            .append(", %").append(String.format("%.1f", replayPlayer.getProgress() * 100))
                            .append(")\n");
                }
            }
        }

        return stats.toString();
    }

    /**
     * Oyuncu ayrıldığında temizlik yapar - GÜNCELLENEN METOD
     */
    public void handlePlayerQuit(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Aktif kaydı durdur
        if (recordingManager.isRecording(playerUuid)) {
            recordingManager.stopRecording(playerUuid);
        }

        // İzlediği replay'i durdur
        ReplayPlayer replayPlayer = activeReplays.remove(playerUuid);
        if (replayPlayer != null) {
            plugin.getLogger().info("[REPLAY] Player quit during replay: " + player.getName());

            // Kontrol itemlerini temizle
            controlManager.removeControlItems(player);

            // Viewer'ı kaldır
            replayPlayer.removeViewer(player);

            // Replay'i durdur (başka viewer yoksa)
            replayPlayer.stop();

            // Clean up saved original location and server (player quit, no need to restore)
            viewerOriginalLocations.remove(playerUuid);
            viewerOriginalServers.remove(playerUuid);
            plugin.getLogger().info("[REPLAY] Cleaned up saved location and server for quit player " + player.getName());

            // Double check - activeReplays'den tamamen kaldırıldığından emin ol
            if (activeReplays.containsKey(playerUuid)) {
                activeReplays.remove(playerUuid);
                plugin.getLogger().warning("[REPLAY] Had to force remove quit player " + player.getName() + " from activeReplays");
            }
        }
    }

    /**
     * Belirli bir replay'i siler
     */
    public CompletableFuture<Boolean> deleteReplay(int reportId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Replay> replay = replayDAO.getReplayByReportId(reportId);
                if (replay.isPresent()) {
                    replayDAO.deleteReplay(replay.get().getId());
                    plugin.getLogger().info("Replay silindi: Rapor #" + reportId);
                    return true;
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Replay silme hatası", e);
                return false;
            }
        });
    }

    /**
     * Aktif replay'den oyuncuyu kaldırır - YENİ METOD
     */
    public void removeActiveReplay(UUID playerUUID) {
        if (activeReplays.remove(playerUUID) != null) {
            plugin.getLogger().info("[REPLAY] Removed player from activeReplays: " + playerUUID);
        }
    }

    /**
     * Viewer'ı orijinal konumuna geri ışınlar - YENİ METOD
     */
    public void restoreViewerLocation(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        // Check if viewer needs to return to original server (BungeeCord)
        String originalServer = viewerOriginalServers.remove(viewerUUID);

        if (originalServer != null && spigotPlugin.getConfigManager().isBungeeCordEnabled()) {
            // Cross-server replay - send player back to original server
            String currentServer = spigotPlugin.getServerName();

            if (!currentServer.equalsIgnoreCase(originalServer)) {
                plugin.getLogger().info("[REPLAY] Sending " + viewer.getName() +
                        " back to original server '" + originalServer + "' from '" + currentServer + "'");

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage("§a✦ Replay bitti! " + originalServer + " sunucusuna geri dönüyorsunuz...");
                        sendPlayerToServer(viewer, originalServer);
                    }
                }, 10L);

                // Don't restore location - player is being sent to another server
                viewerOriginalLocations.remove(viewerUUID);
                return;
            }
        }

        // Restore viewer's original location (same server)
        Location originalLocation = viewerOriginalLocations.remove(viewerUUID);
        if (originalLocation != null && viewer.isOnline()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline()) {
                    viewer.teleport(originalLocation);
                    plugin.getLogger().info("[REPLAY] Restored original location for " + viewer.getName() +
                            " - World: " + originalLocation.getWorld().getName() +
                            " - Location: " + String.format("%.1f, %.1f, %.1f",
                                    originalLocation.getX(), originalLocation.getY(), originalLocation.getZ()));

                    // Send message
                    viewer.sendMessage("§a✦ Replay bitti! Başlangıç konumunuza döndürüldünüz.");
                }
            }, 10L); // 10 tick bekle, NPC'lerin temizlenmesi için
        } else {
            plugin.getLogger().warning("[REPLAY] No original location found for " + viewer.getName() +
                    " - cannot restore location");
        }
    }



    // Getter'lar
    public ReplayControlManager getControlManager() {
        return controlManager;
    }

    public Map<UUID, ReplayPlayer> getActiveReplays() {
        return new HashMap<>(activeReplays);
    }

    public Map<UUID, RecordingManager.RecordingInfo> getActiveRecorders() {
        return recordingManager.getActiveRecordings();
    }

    /**
     * BungeeCord ile oyuncuyu başka sunucuya gönderir
     */
    private void sendPlayerToServer(Player player, String serverName) {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        try {
            com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

            plugin.getLogger().info("[REPLAY] Sent BungeeCord message to connect player " + player.getName() +
                    " to server: " + serverName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] Failed to send player to server: " + serverName, e);
            player.sendMessage("§c✦ Sunucuya bağlanırken bir hata oluştu!");
        }
    }
}