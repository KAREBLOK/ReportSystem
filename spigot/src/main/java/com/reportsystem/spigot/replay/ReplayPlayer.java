package com.reportsystem.spigot.replay;

import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.*;

public class ReplayPlayer {

    private final JavaPlugin plugin;
    private final Replay replay;
    private final List<ReplayAction> actions;
    private final Set<Player> viewers = new HashSet<>();

    // Modüller
    private final ReplayActionPlayer actionPlayer;
    private final ReplayNPCManager npcManager;
    private final ReplayActionHandler actionHandler;
    private final ReplayStateManager stateManager;

    // Durum bilgileri
    private ReplayState state = ReplayState.STOPPED;
    private Location lastLocation;

    // Replay durumları
    public enum ReplayState {
        STOPPED, PLAYING, PAUSED, FINISHED
    }

    public ReplayPlayer(JavaPlugin plugin, Replay replay) throws IOException, ClassNotFoundException {
        this.plugin = plugin;
        this.replay = replay;

        // Action'ları deserialize et
        this.actions = ReplaySerializer.deserialize(replay.getData(), replay.isCompressed());
        replay.setActions(actions); // Cache için

        // Modülleri initialize et
        this.actionPlayer = new ReplayActionPlayer(plugin, this);
        this.npcManager = new ReplayNPCManager(plugin, this);
        this.actionHandler = new ReplayActionHandler(plugin, this, actions);
        this.stateManager = new ReplayStateManager(this);
    }

    /**
     * Replay'i başlatır
     */
    public void start(Location spawnLocation, Player... initialViewers) {
        if (state != ReplayState.STOPPED) {
            return;
        }

        // İlk location'ı bul
        LocationAction firstLocation = findFirstLocationAction();
        if (firstLocation != null) {
            spawnLocation = new Location(
                    spawnLocation.getWorld(),
                    firstLocation.getX(),
                    firstLocation.getY(),
                    firstLocation.getZ(),
                    firstLocation.getYaw(),
                    firstLocation.getPitch()
            );
        }

        this.lastLocation = spawnLocation.clone();

        // İzleyicileri ekle
        for (Player viewer : initialViewers) {
            addViewer(viewer);
        }

        // NPC'yi spawn et
        npcManager.spawnNPC(spawnLocation);

        // Oynatmayı başlat
        state = ReplayState.PLAYING;
        actionHandler.startPlayback();

        plugin.getLogger().info("[REPLAY] Replay started for " + replay.getRecordedPlayer());
    }

    /**
     * Replay'i duraklatır
     */
    public void pause() {
        if (state == ReplayState.PLAYING) {
            state = ReplayState.PAUSED;
            stateManager.setPaused(true);
            plugin.getLogger().info("[REPLAY] Replay paused");
        }
    }

    /**
     * Duraklatılmış replay'i devam ettirir
     */
    public void resume() {
        if (state == ReplayState.PAUSED) {
            state = ReplayState.PLAYING;
            stateManager.setPaused(false);
            stateManager.recalculateStartTime();
            plugin.getLogger().info("[REPLAY] Replay resumed");
        }
    }

    /**
     * Replay'i durdurur - GÜNCELLENEN METOD
     */
    public void stop() {
        if (state == ReplayState.STOPPED) {
            return;
        }

        plugin.getLogger().info("[REPLAY] Stopping replay - cleaning up NPCs and UI");

        state = ReplayState.STOPPED;
        actionHandler.stopPlayback();

        // 1. ÖNCE NPC'leri temizle
        npcManager.despawnAll();
        plugin.getLogger().info("[REPLAY] NPCs despawned");

        // 2. ActionPlayer cleanup (FishHooks, vehicles, bloklar)
        actionPlayer.cleanup();
        plugin.getLogger().info("[REPLAY] ActionPlayer cleanup completed");

        // 3. Her viewer için detaylı temizlik - SYNC olarak
        Set<Player> viewersCopy = new HashSet<>(viewers);
        for (Player viewer : viewersCopy) {
            if (viewer != null && viewer.isOnline()) {
                plugin.getLogger().info("[REPLAY] Cleaning up for viewer: " + viewer.getName());

                // ReplayManager üzerinden kontrol itemlerini temizle
                com.reportsystem.spigot.ReportSystemSpigot plugin =
                        (com.reportsystem.spigot.ReportSystemSpigot) this.plugin;
                plugin.getReplayManager().getControlManager().removeControlItems(viewer);

                // Mesaj gönder
                viewer.sendMessage(ChatColor.RED + "Replay durduruldu!");

                plugin.getLogger().info("[REPLAY] Cleanup completed for viewer: " + viewer.getName());
            }
        }

        // 4. Viewer'ları temizle
        viewers.clear();
        plugin.getLogger().info("[REPLAY] All viewers cleared");

        plugin.getLogger().info("[REPLAY] Replay stopped and cleaned up completely");
    }

    /**
     * Replay'i bitirir - GÜNCELLENEN METOD
     */
    public void finish() {
        plugin.getLogger().info("[REPLAY] Replay finished - cleaning up and teleporting viewers back");

        state = ReplayState.FINISHED;
        actionHandler.stopPlayback();

        com.reportsystem.spigot.ReportSystemSpigot spigotPlugin =
                (com.reportsystem.spigot.ReportSystemSpigot) plugin;

        // EKLENEN: Tüm viewer'lara bittiğini söyle ve kontrol itemlerini temizle
        Set<Player> viewersCopy = new HashSet<>(viewers);
        for (Player viewer : viewersCopy) {
            if (viewer != null && viewer.isOnline()) {
                // Send message
                spigotPlugin.getMessageManager().sendMessage(viewer, "replay.completed");

                String title = spigotPlugin.getMessageManager().getMessage("replay.title-completed");
                String subtitle = spigotPlugin.getMessageManager().getMessage("replay.subtitle-completed")
                        .replace("%player%", replay.getRecordedPlayer());
                viewer.sendTitle(
                        spigotPlugin.getMessageManager().colorize(title),
                        spigotPlugin.getMessageManager().colorize(subtitle),
                        10, 40, 10);

                // Kontrol itemlerini temizle
                spigotPlugin.getReplayManager().getControlManager().removeControlItems(viewer);

                // ÖNEMLİ: activeReplays'den viewer'ı kaldır
                spigotPlugin.getReplayManager().removeActiveReplay(viewer.getUniqueId());

                plugin.getLogger().info("[REPLAY] Replay finished notification sent to: " + viewer.getName());
            }
        }

        // Spawned entity'leri temizle ve blokları restore et
        actionPlayer.cleanup();
        actionPlayer.restoreBlocks();

        // 3 saniye sonra NPC'yi kaldır, tamamen temizle ve viewer'ları geri ışınla
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // NPC'leri temizle
            npcManager.despawnAll();

            // ÖNEMLİ: Her viewer'ı orijinal konumuna geri ışınla
            for (Player viewer : viewersCopy) {
                if (viewer != null && viewer.isOnline()) {
                    spigotPlugin.getReplayManager().restoreViewerLocation(viewer);
                }
            }

            // Son temizlik
            viewers.clear();
            state = ReplayState.STOPPED;

            plugin.getLogger().info("[REPLAY] Final cleanup and teleport completed");
        }, 60L); // 3 saniye bekle
    }

    /**
     * İzleyici ekler
     */
    public void addViewer(Player player) {
        if (!viewers.contains(player)) {
            viewers.add(player);
            plugin.getLogger().info("[REPLAY] Viewer added: " + player.getName());

            // Eğer NPC zaten spawn edilmişse, bu oyuncuya da göster
            if (state != ReplayState.STOPPED) {
                npcManager.showNPCToViewer(player);
            }
        }
    }

    /**
     * İzleyici çıkarır - GÜNCELLENEN METOD
     */
    public void removeViewer(Player player) {
        if (viewers.remove(player)) {
            plugin.getLogger().info("[REPLAY] Viewer removed: " + player.getName());

            // Bu oyuncu için kontrol itemlerini temizle
            com.reportsystem.spigot.ReportSystemSpigot plugin =
                    (com.reportsystem.spigot.ReportSystemSpigot) this.plugin;
            plugin.getReplayManager().getControlManager().removeControlItems(player);

            // ÖNEMLİ: activeReplays'den de kaldır
            plugin.getReplayManager().removeActiveReplay(player.getUniqueId());
        }

        // Hiç izleyici kalmadıysa replay'i durdur
        if (viewers.isEmpty()) {
            plugin.getLogger().info("[REPLAY] No viewers left, stopping replay");
            stop();
        }
    }

    /**
     * İlk location action'ını bulur
     */
    private LocationAction findFirstLocationAction() {
        for (ReplayAction action : actions) {
            if (action instanceof LocationAction) {
                return (LocationAction) action;
            }
        }
        return null;
    }

    /**
     * Oynatma hızını değiştirir
     */
    public void setPlaybackSpeed(double speed) {
        stateManager.setPlaybackSpeed(speed);
        plugin.getLogger().info("[REPLAY] Playback speed changed to: " + speed + "x");
    }

    /**
     * Progress bilgisi döndürür (0.0 - 1.0 arası)
     */
    public double getProgress() {
        return actionHandler.getProgress();
    }

    /**
     * Belirli bir yüzdeye atlar
     */
    public void seekToPercent(double percent) {
        actionHandler.seekToPercent(percent);
        plugin.getLogger().info("[REPLAY] Seeked to: " + (percent * 100) + "%");
    }

    /**
     * İleri sar (saniye cinsinden)
     */
    public void forward(int seconds) {
        actionHandler.forward(seconds);
    }

    /**
     * Geri sar (saniye cinsinden)
     */
    public void rewind(int seconds) {
        actionHandler.rewind(seconds);
    }

    // Getter'lar
    public JavaPlugin getPlugin() { return plugin; }
    public Replay getReplay() { return replay; }
    public List<ReplayAction> getActions() { return actions; }
    public Set<Player> getViewers() { return new HashSet<>(viewers); }
    public ReplayState getState() { return state; }
    public Location getLastLocation() { return lastLocation; }
    public void setLastLocation(Location location) { this.lastLocation = location; }

    // Modül getter'ları
    public ReplayActionPlayer getActionPlayer() { return actionPlayer; }
    public ReplayNPCManager getNpcManager() { return npcManager; }
    public ReplayActionHandler getActionHandler() { return actionHandler; }
    public ReplayStateManager getStateManager() { return stateManager; }

    // State kontrolü
    public boolean isPaused() { return stateManager.isPaused(); }
    public int getCurrentActionIndex() { return actionHandler.getCurrentActionIndex(); }
    public int getTotalActions() { return actions.size(); }
    public double getPlaybackSpeed() { return stateManager.getPlaybackSpeed(); }
    public long getElapsedTime() { return stateManager.getElapsedTime(); }
    public long getTotalTime() { return stateManager.getTotalTime(); }
}