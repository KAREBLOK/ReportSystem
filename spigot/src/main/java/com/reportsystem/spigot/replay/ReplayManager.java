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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ReplayManager {

    private final JavaPlugin plugin;
    private final ReplayDAO replayDAO;
    private final ReplayControlManager controlManager;
    private final RecordingManager recordingManager;

    // Aktif replay'ler (viewer UUID -> ReplayPlayer)
    private final Map<UUID, ReplayPlayer> activeReplays = new ConcurrentHashMap<>();

    // Replay başlatılıyor durumu (spam önleme)
    private final java.util.Set<UUID> startingReplays = ConcurrentHashMap.newKeySet();

    // Viewer'ların orijinal konumları (replay bitince geri dönmek için)
    private final Map<UUID, Location> viewerOriginalLocations = new ConcurrentHashMap<>();

    // Viewer'ların orijinal sunucuları (BungeeCord - cross-server replay için)
    private final Map<UUID, String> viewerOriginalServers = new ConcurrentHashMap<>();

    // Oyuncu replay tercihleri (replay'ler arası kalıcı)
    private final Map<UUID, ReplayPreferences> playerPreferences = new ConcurrentHashMap<>();

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
    public CompletableFuture<Boolean> startReplay(int reportId, Player viewer) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ReportSystemSpigot.getInstance().debug("[REPLAY] Starting replay request for report #" + reportId + " - viewer: " + viewer.getName());

        UUID uuid = viewer.getUniqueId();

        // Already watching a replay or already starting one?
        if (activeReplays.containsKey(uuid) || !startingReplays.add(uuid)) {
            ((ReportSystemSpigot) plugin).getMessageManager().sendMessage(viewer, "replay.already-watching");
            future.complete(false);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Cross-server kontrolü
                ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
                if (spigotPlugin.getConfigManager().isBungeeCordEnabled()) {
                    try {
                        com.reportsystem.common.models.Report report = spigotPlugin.getReportService().getReportById(reportId);
                        if (report != null && report.getServerName() != null) {
                            String currentServer = spigotPlugin.getServerName();
                            String replayServer = report.getServerName();

                            if (!currentServer.equalsIgnoreCase(replayServer)) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    try {
                                        viewerOriginalServers.put(uuid, currentServer);
                                        spigotPlugin.storePendingReplay(uuid, reportId);
                                        sendPlayerToServer(viewer, replayServer);
                                        viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.cross-server").replace("%server%", replayServer)));
                                        future.complete(true);
                                    } finally {
                                        startingReplays.remove(uuid);
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[REPLAY] Could not check server name for cross-server replay", e);
                    }
                }

                // Veritabanından asenkron olarak çek
                Optional<Replay> replayOpt = replayDAO.getReplayByReportId(reportId);
                if (!replayOpt.isPresent()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().warning("[REPLAY] Replay not found in database for report #" + reportId);
                        spigotPlugin.getMessageManager().sendMessage(viewer, "replay.not-found");
                        startingReplays.remove(uuid);
                        future.complete(false);
                    });
                    return;
                }

                Replay replay = replayOpt.get();
                if (replay.getData() == null || replay.getData().length == 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().severe("[REPLAY] Replay data is null or empty for report #" + reportId);
                        spigotPlugin.getMessageManager().sendMessage(viewer, "replay.data-empty");
                        startingReplays.remove(uuid);
                        future.complete(false);
                    });
                    return;
                }

                // Deserialization (Ağır İşlem - Async devam eder)
                ReplayPlayer replayPlayer = new ReplayPlayer(plugin, replay);

                // Bukkit API işlemlerini ana thread'e döndür
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!viewer.isOnline()) {
                            future.complete(false);
                            return;
                        }

                        viewerOriginalLocations.put(uuid, viewer.getLocation().clone());
                        Location startLocation = findSafeStartLocation(replayPlayer, viewer);

                        if (startLocation == null) {
                            viewerOriginalLocations.remove(uuid);
                            spigotPlugin.getMessageManager().sendMessage(viewer, "replay.world-missing");
                            startingReplays.remove(uuid);
                            future.complete(false);
                            return;
                        }

                        boolean crossWorld = !startLocation.getWorld().equals(viewer.getWorld());

                        if (crossWorld) {
                            viewer.teleportAsync(startLocation).thenAccept(success -> {
                                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                    try {
                                        if (!viewer.isOnline()) {
                                            future.complete(false);
                                            return;
                                        }
                                        viewer.setFallDistance(0f);
                                        finishReplayStart(viewer, replayPlayer, startLocation, reportId);
                                        future.complete(true);
                                    } finally {
                                        startingReplays.remove(uuid);
                                    }
                                }, 20L);
                            });
                        } else {
                            finishReplayStart(viewer, replayPlayer, startLocation, reportId);
                            teleportViewerToReplayStart(viewer, startLocation, replayPlayer, reportId);
                            future.complete(true);
                            startingReplays.remove(uuid);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "[REPLAY] Error in main thread startReplay finalizer", e);
                        future.complete(false);
                        startingReplays.remove(uuid);
                    }
                });

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().log(Level.SEVERE, "[REPLAY] Error while starting replay for report #" + reportId, e);
                    ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
                    spigotPlugin.getMessageManager().sendMessage(viewer, "replay.error-unexpected");
                    spigotPlugin.getMessageManager().sendMessage(viewer, "general.error.details", "%error%", e.getMessage());
                    startingReplays.remove(uuid);
                    future.complete(false);
                });
            }
        });
        return future;
    }

    private void finishReplayStart(Player viewer, ReplayPlayer replayPlayer, Location startLocation, int reportId) {
        replayPlayer.start(startLocation, viewer);
        hideViewerFromPlayers(viewer);
        controlManager.giveControlItems(viewer, replayPlayer);
        applyPreferences(viewer, replayPlayer);
        activeReplays.put(viewer.getUniqueId(), replayPlayer);

        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        // Anti-cheat bypass — replay sirasinda Timer/Blink/Flight false-positive'lerini engelle
        if (spigotPlugin.getAntiCheatBypassManager() != null) {
            spigotPlugin.getAntiCheatBypassManager().exempt(viewer);
        }

        viewer.playSound(startLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
        String title = spigotPlugin.getMessageManager().getMessage("replay.title-started");
        String subtitle = spigotPlugin.getMessageManager().getMessage("replay.subtitle-started")
                .replace("%player%", replayPlayer.getReplay().getRecordedPlayer())
                .replace("%report_id%", String.valueOf(reportId));
        viewer.sendTitle(
                spigotPlugin.getMessageManager().colorize(title),
                spigotPlugin.getMessageManager().colorize(subtitle),
                10, 40, 10);
        spigotPlugin.getMessageManager().sendMessage(viewer, "replay.started");
    }

    /**
     * Replay başlangıcı için güvenli konum bulur (Gereksiz chunk yüklemeleri optimize edildi)
     */
    private Location findSafeStartLocation(ReplayPlayer replayPlayer, Player viewer) {
        // Replay'in kaydedildiği dünyayı al
        String replayWorldName = replayPlayer.getReplay().getWorldName();
        org.bukkit.World replayWorld = Bukkit.getWorld(replayWorldName);

        // Dünya bulunamazsa replay'i başlatmayı reddet
        // (fallback olarak viewer'ın dünyasını kullanmak koordinatları anlamsız kılar)
        if (replayWorld == null) {
            plugin.getLogger().warning("[REPLAY] Replay dünyası bulunamadı: " + replayWorldName +
                    " - Replay başlatılamıyor (dünya silinmiş veya yüklenmemiş).");
            return null;
        }

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
                    replayWorld,
                    firstLocation.getX(),
                    firstLocation.getY(),
                    firstLocation.getZ(),
                    firstLocation.getYaw(),
                    firstLocation.getPitch()
            );

            // İzleyici zaten SPECTATOR modunda olacağı için (duvarın içinden geçebilir),
            // sunucuyu dondurmamak adına Senkron Chunk yükleme (getChunkAt().load()) veya 
            // isSafeLocation üzerinden detaylı blok kontrolleri kaldırıldı.
            // Sadece karaktere hafifçe offset vererek (3 blok arkasında kalarak) döndürüyoruz.
            // Chunk'lar teleportAsync ile asenkron yüklenecek (Lag oluşmayacak).

            double yawRad = Math.toRadians(characterLocation.getYaw());
            double offsetX = -Math.sin(yawRad) * 3.0; // Arkaya doğru gitmesi için -
            double offsetZ = Math.cos(yawRad) * 3.0;  // Arkaya doğru gitmesi için +
            
            Location viewerStartLoc = characterLocation.clone().add(offsetX, 1.0, offsetZ);
            
            // Baktığı yönü karaktere doğru ayarla
            org.bukkit.util.Vector direction = characterLocation.toVector().subtract(viewerStartLoc.toVector());
            viewerStartLoc.setDirection(direction);

            return viewerStartLoc;
        } else {
            // Fallback: viewer'ın mevcut konumu
            return viewer.getLocation();
        }
    }

    /**
     * Eski replay'leri siler
     */
    public void deleteOldReplays(int days) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ReportSystemSpigot.getInstance().debug("[REPLAY] Auto-delete check for replays older than " + days + " days");

                // ReplayDAO metodu çağır
                int deletedCount = replayDAO.deleteOldReplays(days);

                if (deletedCount > 0) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY] Deleted " + deletedCount + " old replays");
                } else {
                    ReportSystemSpigot.getInstance().debug("[REPLAY] No old replays found to delete");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[REPLAY] Database error while deleting old replays", e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[REPLAY] Unexpected error while deleting old replays", e);
            }
        });
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
            viewer.setFallDistance(0f);

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

            ReportSystemSpigot.getInstance().debug("[REPLAY-TELEPORT] " + viewer.getName() + " teleported to replay start location");

        }, 10L);
    }

    /**
     * Oyuncunun izlediği replay'i durdurur - GÜNCELLENEN METOD
     */
    public void stopReplay(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        ReplayPlayer replayPlayer = activeReplays.get(viewerUUID); // remove yerine get kullan

        if (replayPlayer != null) {
            ReportSystemSpigot.getInstance().debug("[REPLAY] Stopping replay for " + viewer.getName());

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
                    ReportSystemSpigot.getInstance().debug("[REPLAY] Sending " + viewer.getName() +
                            " back to original server '" + originalServer + "' from '" + currentServer + "'");

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline()) {
                            viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.return-server").replace("%server%", originalServer)));
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
                        showViewerToPlayers(viewer);
                        reshowOverwatchNPCs(viewer);
                        ReportSystemSpigot.getInstance().debug("[REPLAY] Restored original location for " + viewer.getName());

                        // Send message
                        viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.return-location")));
                    }
                }, 10L); // 10 tick bekle, NPC'lerin temizlenmesi için
            } else {
                // Konum yok ama yine de göster
                showViewerToPlayers(viewer);
                reshowOverwatchNPCs(viewer);
                plugin.getLogger().warning("[REPLAY] No original location found for " + viewer.getName());
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY] Replay stop completed for " + viewer.getName());
        } else {
            ReportSystemSpigot spigot = (ReportSystemSpigot) plugin;
            viewer.sendMessage(spigot.getMessageManager().colorize(spigot.getMessageManager().getMessage("replay.not-watching")));
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
        ReportSystemSpigot.getInstance().debug("[REPLAY] Stopping all active replays");

        // Her viewer için kontrol itemlerini temizle
        for (Map.Entry<UUID, ReplayPlayer> entry : activeReplays.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) {
                controlManager.removeControlItems(viewer);
                ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
                viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.stopped-shutdown")));
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
        ReportSystemSpigot.getInstance().debug("[REPLAY] Cleared all saved viewer locations and servers");

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

        // Anti-cheat bypass'i temizle (set'te kalmasin)
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
        if (spigotPlugin.getAntiCheatBypassManager() != null) {
            spigotPlugin.getAntiCheatBypassManager().unexempt(playerUuid);
        }

        // Aktif kaydı durdur
        if (recordingManager.isRecording(playerUuid)) {
            recordingManager.stopRecording(playerUuid);
        }

        // İzlediği replay'i durdur
        ReplayPlayer replayPlayer = activeReplays.remove(playerUuid);
        if (replayPlayer != null) {
            ReportSystemSpigot.getInstance().debug("[REPLAY] Player quit during replay: " + player.getName());

            // Kontrol itemlerini temizle
            controlManager.removeControlItems(player);

            // Viewer'ı kaldır
            replayPlayer.removeViewer(player);

            // Replay'i durdur (başka viewer yoksa)
            replayPlayer.stop();

            // Clean up saved original location and server (player quit, no need to restore)
            viewerOriginalLocations.remove(playerUuid);
            viewerOriginalServers.remove(playerUuid);
            ReportSystemSpigot.getInstance().debug("[REPLAY] Cleaned up saved location and server for quit player " + player.getName());

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
            ReportSystemSpigot.getInstance().debug("[REPLAY] Removed player from activeReplays: " + playerUUID);
        }
    }

    /**
     * Viewer'ı orijinal konumuna geri ışınlar - YENİ METOD
     */
    public void restoreViewerLocation(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        // Anti-cheat bypass kaldir (replay bitti, normal kurallar geri donsun)
        if (spigotPlugin.getAntiCheatBypassManager() != null) {
            spigotPlugin.getAntiCheatBypassManager().unexempt(viewer);
        }

        // Check if viewer needs to return to original server (BungeeCord)
        String originalServer = viewerOriginalServers.remove(viewerUUID);

        if (originalServer != null && spigotPlugin.getConfigManager().isBungeeCordEnabled()) {
            // Cross-server replay - send player back to original server
            String currentServer = spigotPlugin.getServerName();

            if (!currentServer.equalsIgnoreCase(originalServer)) {
                ReportSystemSpigot.getInstance().debug("[REPLAY] Sending " + viewer.getName() +
                        " back to original server '" + originalServer + "' from '" + currentServer + "'");

                showViewerToPlayers(viewer);

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.return-server").replace("%server%", originalServer)));
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
                    viewer.setFallDistance(0f);

                    // Teleporttan SONRA göster - önce gösterince spawn paketi gönderilmiyordu
                    showViewerToPlayers(viewer);

                    // Overwatch NPC'lerini tekrar göster (packet entity'ler teleport sonrası kaybolur)
                    reshowOverwatchNPCs(viewer);

                    ReportSystemSpigot.getInstance().debug("[REPLAY] Restored original location for " + viewer.getName() +
                            " - World: " + originalLocation.getWorld().getName() +
                            " - Location: " + String.format("%.1f, %.1f, %.1f",
                                    originalLocation.getX(), originalLocation.getY(), originalLocation.getZ()));

                    // Send message
                    viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.return-location")));
                }
            }, 10L); // 10 tick bekle, NPC'lerin temizlenmesi için
        } else {
            // Konum yok ama yine de göster
            showViewerToPlayers(viewer);
            reshowOverwatchNPCs(viewer);
            plugin.getLogger().warning("[REPLAY] No original location found for " + viewer.getName() +
                    " - cannot restore location");
        }
    }



    /**
     * Replay izleyen oyuncuyu diğer tüm oyunculardan gizler
     * ve diğer tüm oyuncuları viewer'dan gizler (scoreboard BELOW_NAME çakışmasını önler)
     */
    private void hideViewerFromPlayers(Player viewer) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                online.hidePlayer(plugin, viewer);  // Viewer'ı diğerlerinden gizle
                viewer.hidePlayer(plugin, online);   // Diğerlerini viewer'dan gizle (BELOW_NAME kalp sorunu için)
            }
        }
        ReportSystemSpigot.getInstance().debug("[REPLAY] Hidden " + viewer.getName() + " from all players (bidirectional)");
    }

    /**
     * Replay biten oyuncuyu tekrar herkese gösterir
     * ve diğer tüm oyuncuları viewer'a tekrar gösterir
     */
    private void showViewerToPlayers(Player viewer) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                online.showPlayer(plugin, viewer);  // Viewer'ı tekrar göster
                viewer.showPlayer(plugin, online);   // Diğerlerini viewer'a tekrar göster
            }
        }
        ReportSystemSpigot.getInstance().debug("[REPLAY] Showing " + viewer.getName() + " to all players again (bidirectional)");
    }

    public Location getOriginalLocation(UUID playerUUID) {
        return viewerOriginalLocations.get(playerUUID);
    }

    public void setOriginalLocation(UUID playerUUID, Location location) {
        viewerOriginalLocations.put(playerUUID, location);
    }

    public void clearOriginalLocation(UUID playerUUID) {
        viewerOriginalLocations.remove(playerUUID);
    }

    public ReplayPreferences getPreferences(UUID playerUUID) {
        return playerPreferences.computeIfAbsent(playerUUID, k -> new ReplayPreferences());
    }

    /**
     * Kayitli tercihleri yeni baslamis replay'e uygular
     */
    private void applyPreferences(org.bukkit.entity.Player viewer, ReplayPlayer replayPlayer) {
        ReplayPreferences prefs = playerPreferences.get(viewer.getUniqueId());
        if (prefs == null) return;

        // Glow
        if (prefs.isGlowEnabled()) {
            replayPlayer.getNpcManager().setGlowEnabled(true);
        }

        // Gece gorusu
        if (prefs.isNightVision()) {
            viewer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY] Applied preferences for " + viewer.getName() +
                " - Glow: " + prefs.isGlowEnabled() +
                ", NightVision: " + prefs.isNightVision());
    }

    /**
     * Replay sonrası Overwatch NPC'lerini tekrar gösterir (packet entity'ler teleport sonrası kaybolur)
     */
    private void reshowOverwatchNPCs(Player viewer) {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;
        if (spigotPlugin.getNPCManager() != null) {
            // Teleport sonrası client'ın chunk yüklemesi için kısa gecikme
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline()) {
                    spigotPlugin.getNPCManager().showAllNPCsToPlayer(viewer);
                    ReportSystemSpigot.getInstance().debug("[REPLAY] Re-sent Overwatch NPC packets to " + viewer.getName());
                }
            }, 5L);
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
     * Web replay dosyasi olusturur (async).
     * ReplayAction listesini .packets.txt formatina donusturup dosyaya yazar.
     *
     * @param reportId rapor ID
     * @return viewer URL'i
     */
    public CompletableFuture<String> generateWebReplay(int reportId) {
        ReportSystemSpigot spigotPlugin = (ReportSystemSpigot) plugin;

        if (!spigotPlugin.getConfigManager().isWebReplayEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Replay> replayOpt = replayDAO.getReplayByReportId(reportId);
                if (!replayOpt.isPresent()) {
                    plugin.getLogger().warning("[WebReplay] Replay bulunamadi: report #" + reportId);
                    return null;
                }

                Replay replay = replayOpt.get();
                if (replay.getData() == null || replay.getData().length == 0) {
                    plugin.getLogger().warning("[WebReplay] Replay verisi bos: report #" + reportId);
                    return null;
                }

                // Aksiyonlari deserialize et - baslangic konumunu bulmak icin
                java.util.List<com.reportsystem.common.replay.actions.ReplayAction> actions;
                try {
                    actions = com.reportsystem.common.replay.ReplaySerializer.deserialize(
                            replay.getData(), replay.isCompressed());
                } catch (Exception e) {
                    plugin.getLogger().warning("[WebReplay] Deserialize hatasi: " + e.getMessage());
                    actions = null;
                }

                // Oyuncunun hareket alanini hesapla (tum LocationAction'lardan bounding box)
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                boolean foundLocation = false;

                if (actions != null) {
                    for (com.reportsystem.common.replay.actions.ReplayAction action : actions) {
                        if (action instanceof com.reportsystem.common.replay.actions.LocationAction
                                && action.isMainPlayer()) {
                            com.reportsystem.common.replay.actions.LocationAction loc =
                                    (com.reportsystem.common.replay.actions.LocationAction) action;
                            minX = Math.min(minX, loc.getX());
                            minY = Math.min(minY, loc.getY());
                            minZ = Math.min(minZ, loc.getZ());
                            maxX = Math.max(maxX, loc.getX());
                            maxY = Math.max(maxY, loc.getY());
                            maxZ = Math.max(maxZ, loc.getZ());
                            foundLocation = true;
                        }
                    }
                }

                if (!foundLocation) {
                    minX = 0; minY = 64; minZ = 0;
                    maxX = 0; maxY = 64; maxZ = 0;
                }

                // Main thread'de terrain tara (hareket alani + margin)
                java.util.List<int[]> terrainBlocks = scanTerrainSync(
                        replay.getWorldName(),
                        (int) minX, (int) minY, (int) minZ,
                        (int) maxX, (int) maxY, (int) maxZ);

                com.reportsystem.spigot.webreplay.WebReplayExporter exporter =
                        new com.reportsystem.spigot.webreplay.WebReplayExporter(
                                plugin,
                                spigotPlugin.getConfigManager().getWebReplayStoragePath(),
                                spigotPlugin.getConfigManager().getWebReplayBaseUrl(),
                                spigotPlugin.getConfigManager().getWebReplayViewerUrl()
                        );

                String localUrl = exporter.export(replay, terrainBlocks);

                // API upload (kareblok.tc'ye yukle)
                String apiUrl = spigotPlugin.getConfigManager().getWebReplayApiUrl();
                if (apiUrl != null && !apiUrl.isEmpty()) {
                    try {
                        // Rapor bilgilerini al
                        String reporter = "";
                        String reason = "";
                        String serverName = "";
                        try {
                            com.reportsystem.common.models.Report report =
                                    spigotPlugin.getReportService().getReportById(reportId);
                            if (report != null) {
                                reporter = report.getReporterName() != null ? report.getReporterName() : "";
                                reason = report.getReason() != null ? report.getReason() : "";
                                serverName = report.getServerName() != null ? report.getServerName() : "";
                            }
                        } catch (Exception ignored) {}

                        String licenseKey = spigotPlugin.getConfigManager().getLicenseKey();

                        java.io.File replayFile = new java.io.File(
                                spigotPlugin.getConfigManager().getWebReplayStoragePath(),
                                "replay_" + reportId + ".packets.txt");

                        if (replayFile.exists()) {
                            com.reportsystem.spigot.webreplay.WebReplayUploader uploader =
                                    new com.reportsystem.spigot.webreplay.WebReplayUploader(
                                            plugin, apiUrl, licenseKey);

                            int durationSec = (int)(replay.getDuration() / 1000);
                            if (durationSec <= 0) durationSec = 45;

                            String viewerUrl = uploader.upload(replayFile, reportId,
                                    replay.getRecordedPlayer(), reporter, reason, serverName, durationSec);

                            if (viewerUrl != null) {
                                plugin.getLogger().info("[WebReplay] API upload basarili: " + viewerUrl);
                                return viewerUrl;
                            }
                        }
                    } catch (Exception uploadEx) {
                        plugin.getLogger().warning("[WebReplay] API upload hatasi: " + uploadEx.getMessage());
                    }
                }

                return localUrl;
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[WebReplay] Export hatasi: report #" + reportId, e);
                return null;
            }
        });
    }

    /**
     * Main thread'de terrain bloklarini tarar.
     * Oyuncunun hareket alani (bounding box) + margin kadar alanı tarar.
     * Async thread'den cagirilir, Bukkit.getScheduler().callSyncMethod() ile main thread'e gider.
     *
     * @return her eleman: {x, y, z, blockStateId}
     */
    private java.util.List<int[]> scanTerrainSync(String worldName,
                                                   int bbMinX, int bbMinY, int bbMinZ,
                                                   int bbMaxX, int bbMaxY, int bbMaxZ) {
        try {
            return org.bukkit.Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[WebReplay] Dunya bulunamadi: " + worldName);
                    return java.util.Collections.<int[]>emptyList();
                }

                // Terrain tarama: 2 chunk (32 blok) margin — performansli ve stabil
                int margin = 32;
                int scanMinX = bbMinX - margin;
                int scanMaxX = bbMaxX + margin;
                int scanMinZ = bbMinZ - margin;
                int scanMaxZ = bbMaxZ + margin;
                int scanMinY = Math.max(world.getMinHeight(), bbMinY - 15);
                int scanMaxY = Math.min(world.getMaxHeight() - 1, bbMaxY + 25);

                // Max 96 blok genislik (6 chunk)
                int maxSpan = 96;
                if (scanMaxX - scanMinX > maxSpan) {
                    int centerX = (scanMinX + scanMaxX) / 2;
                    scanMinX = centerX - maxSpan / 2;
                    scanMaxX = centerX + maxSpan / 2;
                }
                if (scanMaxZ - scanMinZ > maxSpan) {
                    int centerZ = (scanMinZ + scanMaxZ) / 2;
                    scanMinZ = centerZ - maxSpan / 2;
                    scanMaxZ = centerZ + maxSpan / 2;
                }

                java.util.List<int[]> blocks = new java.util.ArrayList<>(8000);
                int totalScanned = 0;
                int maxBlocks = 8000; // Web client performansi icin max blok limiti

                outer:
                for (int x = scanMinX; x <= scanMaxX; x++) {
                    for (int z = scanMinZ; z <= scanMaxZ; z++) {
                        for (int y = scanMinY; y <= scanMaxY; y++) {
                            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                            if (block.getType().isAir()) continue;
                            totalScanned++;

                            // Sadece gorunen bloklari gonder (en az 1 komsusu air)
                            if (isExposedToAir(world, x, y, z)) {
                                int stateId = com.reportsystem.spigot.webreplay.WebReplayBlockStateMap
                                        .getBlockStateIdFromBlock(block);
                                blocks.add(new int[]{x, y, z, stateId});

                                if (blocks.size() >= maxBlocks) break outer;
                            }
                        }
                    }
                }

                plugin.getLogger().info("[WebReplay] Terrain tarandi: " + blocks.size() +
                        " gorunen blok / " + totalScanned + " toplam (" + worldName + " alan: " +
                        (scanMaxX - scanMinX + 1) + "x" + (scanMaxY - scanMinY + 1) + "x" + (scanMaxZ - scanMinZ + 1) +
                        ") [MC " + org.bukkit.Bukkit.getMinecraftVersion() +
                        ", NMS: " + com.reportsystem.spigot.webreplay.WebReplayBlockStateMap.isNmsAvailable() + "]");
                return blocks;
            }).get();
        } catch (Exception e) {
            plugin.getLogger().warning("[WebReplay] Terrain tarama hatasi: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Blogun en az bir komsusunun air (veya sivi/seffaf) olup olmadigini kontrol eder.
     * Sadece gorunen (yuzey) bloklari web replay'e gonderilir.
     */
    private boolean isExposedToAir(org.bukkit.World world, int x, int y, int z) {
        return isTransparent(world, x + 1, y, z) ||
               isTransparent(world, x - 1, y, z) ||
               isTransparent(world, x, y + 1, z) ||
               isTransparent(world, x, y - 1, z) ||
               isTransparent(world, x, y, z + 1) ||
               isTransparent(world, x, y, z - 1);
    }

    private boolean isTransparent(org.bukkit.World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return true;
        org.bukkit.Material type = world.getBlockAt(x, y, z).getType();
        return type.isAir() || !type.isSolid();
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

            ReportSystemSpigot.getInstance().debug("[REPLAY] Sent BungeeCord message to connect player " + player.getName() +
                    " to server: " + serverName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[REPLAY] Failed to send player to server: " + serverName, e);
            player.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.server-connect-error")));
        }
    }
}