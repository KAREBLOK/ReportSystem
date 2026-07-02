package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.*;
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

        // Kayıt sırasında var olan blokları göster (replay başlamadan önce)
        actionPlayer.showInitialBlocks();

        // Oynatmayı başlat
        state = ReplayState.PLAYING;
        actionHandler.startPlayback();

        ReportSystemSpigot.getInstance().debug("[REPLAY] Replay started for " + replay.getRecordedPlayer());
    }

    /**
     * Replay'i duraklatır
     */
    public void pause() {
        if (state == ReplayState.PLAYING) {
            state = ReplayState.PAUSED;
            actionHandler.onPause();
            stateManager.setPaused(true);
            ReportSystemSpigot.getInstance().debug("[REPLAY] Replay paused");
        }
    }

    /**
     * Duraklatılmış replay'i devam ettirir
     */
    public void resume() {
        if (state == ReplayState.PAUSED) {
            state = ReplayState.PLAYING;
            actionHandler.onResume();
            stateManager.setPaused(false);
            ReportSystemSpigot.getInstance().debug("[REPLAY] Replay resumed");
        }
    }

    /**
     * Replay'i durdurur - GÜNCELLENEN METOD
     */
    public void stop() {
        if (state == ReplayState.STOPPED) {
            return;
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY] Stopping replay - cleaning up NPCs and UI");

        state = ReplayState.STOPPED;
        actionHandler.stopPlayback();

        // 1. Viewer listesinin kopyasını AL (cleanup'tan ÖNCE)
        Set<Player> viewersCopy = new HashSet<>(viewers);

        // 2. Blokları restore et (viewers temizlenmeden ÖNCE)
        actionPlayer.restoreBlocks(viewersCopy);

        // 3. NPC'leri temizle
        npcManager.despawnAll();
        ReportSystemSpigot.getInstance().debug("[REPLAY] NPCs despawned");

        // 4. ActionPlayer cleanup (FishHooks, vehicles vb. - blok restore hariç)
        actionPlayer.cleanup();
        ReportSystemSpigot.getInstance().debug("[REPLAY] ActionPlayer cleanup completed");

        // 5. Her viewer için detaylı temizlik
        for (Player viewer : viewersCopy) {
            if (viewer != null && viewer.isOnline()) {
                com.reportsystem.spigot.ReportSystemSpigot plugin =
                        (com.reportsystem.spigot.ReportSystemSpigot) this.plugin;
                plugin.getReplayManager().getControlManager().removeControlItems(viewer);
                plugin.getReplayManager().restoreViewerLocation(viewer);
                viewer.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("replay.stopped")));
            }
        }

        // 6. Viewer'ları temizle (en son)
        viewers.clear();
        ReportSystemSpigot.getInstance().debug("[REPLAY] Replay stopped and cleaned up completely");
    }

    /**
     * Replay'i bitirir - GÜNCELLENEN METOD
     */
    public void finish() {
        ReportSystemSpigot.getInstance().debug("[REPLAY] Replay finished - cleaning up and teleporting viewers back");

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

                // Overwatch review'daysa sadece UI temizle, state'i geri yukleme
                boolean isOverwatchViewer = spigotPlugin.getOverwatchReplayListener() != null
                        && spigotPlugin.getOverwatchReplayListener().isReviewing(viewer.getUniqueId());

                if (isOverwatchViewer) {
                    spigotPlugin.getReplayManager().getControlManager().stopReplayUI(viewer);
                } else {
                    spigotPlugin.getReplayManager().getControlManager().removeControlItems(viewer);
                }

                // Düşme hasarını sıfırla - replay bitişinde havadan düşmeyi önler
                viewer.setFallDistance(0f);

                // NOT: activeReplays'den kaldırma işlemi teleport SONRASINA taşındı (aşağıda)

                ReportSystemSpigot.getInstance().debug("[REPLAY] Replay finished notification sent to: " + viewer.getName());
            }
        }

        // Blokları restore et (viewer listesi temizlenmeden ÖNCE)
        actionPlayer.restoreBlocks(viewersCopy);

        // Spawned entity'leri temizle
        actionPlayer.cleanup();

        // 3 saniye sonra NPC'yi kaldır, tamamen temizle ve viewer'ları geri ışınla
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // NPC'leri temizle
            npcManager.despawnAll();

            // Her viewer'i orijinal konumuna geri isinla
            // ANCAK Overwatch review'daysa isinlama - verdict GUI acik kalsin
            for (Player viewer : viewersCopy) {
                if (viewer != null && viewer.isOnline()) {
                    // Teleport öncesi düşme hasarını tekrar sıfırla
                    viewer.setFallDistance(0f);

                    boolean isOverwatchReview = spigotPlugin.getOverwatchReplayListener() != null
                            && spigotPlugin.getOverwatchReplayListener().isReviewing(viewer.getUniqueId());

                    if (!isOverwatchReview) {
                        spigotPlugin.getReplayManager().restoreViewerLocation(viewer);
                    }

                    // Teleport tamamlandıktan sonra activeReplays'den kaldır
                    // restoreViewerLocation içinde 10 tick daha bekliyor, toplam 70 tick sonra kaldır
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (viewer != null && viewer.isOnline()) {
                            viewer.setFallDistance(0f);
                        }
                        spigotPlugin.getReplayManager().removeActiveReplay(viewer.getUniqueId());
                    }, 15L);
                } else {
                    // Offline oyuncuyu da activeReplays'den temizle
                    if (viewer != null) {
                        spigotPlugin.getReplayManager().removeActiveReplay(viewer.getUniqueId());
                    }
                }
            }

            // Son temizlik
            viewers.clear();
            state = ReplayState.STOPPED;

            ReportSystemSpigot.getInstance().debug("[REPLAY] Final cleanup and teleport completed");
        }, 60L); // 3 saniye bekle
    }

    /**
     * İzleyici ekler
     */
    public void addViewer(Player player) {
        if (!viewers.contains(player)) {
            viewers.add(player);
            ReportSystemSpigot.getInstance().debug("[REPLAY] Viewer added: " + player.getName());

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
            ReportSystemSpigot.getInstance().debug("[REPLAY] Viewer removed: " + player.getName());

            // Bu oyuncu için kontrol itemlerini temizle
            com.reportsystem.spigot.ReportSystemSpigot plugin =
                    (com.reportsystem.spigot.ReportSystemSpigot) this.plugin;
            plugin.getReplayManager().getControlManager().removeControlItems(player);

            // Viewer'ı tekrar göster ve orijinal konumuna geri gönder
            plugin.getReplayManager().restoreViewerLocation(player);

            // ÖNEMLİ: activeReplays'den de kaldır
            plugin.getReplayManager().removeActiveReplay(player.getUniqueId());
        }

        // Hiç izleyici kalmadıysa replay'i durdur
        if (viewers.isEmpty()) {
            ReportSystemSpigot.getInstance().debug("[REPLAY] No viewers left, stopping replay");
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
        // 1. Hız değişmeden ÖNCE mevcut elapsed time'ı kaydet
        actionHandler.onSpeedChange();
        // 2. Hızı değiştir
        stateManager.setPlaybackSpeed(speed);
        // 3. Yeni hızla startTime'ı yeniden hesapla (elapsed time korunsun)
        actionHandler.afterSpeedChange();
        ReportSystemSpigot.getInstance().debug("[REPLAY] Playback speed changed to: " + speed + "x");
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
        // Blokları restore et
        actionPlayer.restoreBlocks(getViewers());
        // Seek yap
        actionHandler.seekToPercent(percent);
        // Blok durumunu hedef zamana kadar güncelle
        actionPlayer.showInitialBlocks();
        actionPlayer.replayBlockStateUpTo(actionHandler.getCurrentActionIndex());
        ReportSystemSpigot.getInstance().debug("[REPLAY] Seeked to: " + (percent * 100) + "%");
    }

    /**
     * İleri sar (saniye cinsinden)
     */
    public void forward(int seconds) {
        // 1. Mevcut blok değişikliklerini restore et
        actionPlayer.restoreBlocks(getViewers());
        // 2. İleri sar (index + startTime + NPC konumu güncellenir)
        actionHandler.forward(seconds);
        // 3. Başlangıç bloklarını göster
        actionPlayer.showInitialBlocks();
        // 4. Hedef zamana kadarki blok değişikliklerini uygula
        actionPlayer.replayBlockStateUpTo(actionHandler.getCurrentActionIndex());
    }

    /**
     * Geri sar (saniye cinsinden)
     * Geri sarma sırasında değiştirilen blokları restore eder ve başlangıç bloklarını tekrar gösterir
     */
    public void rewind(int seconds) {
        // 1. Geri sarmadan ÖNCE değiştirilen blokları restore et
        actionPlayer.restoreBlocks(getViewers());
        // 2. Geri sar (index + startTime + NPC konumu güncellenir)
        actionHandler.rewind(seconds);
        // 3. Başlangıç bloklarını tekrar göster (kayıt sırasında var olan bloklar)
        actionPlayer.showInitialBlocks();
        // 4. Hedef zamana kadarki blok değişikliklerini uygula (kırılmış bloklar kırık, konmuş bloklar konmuş)
        actionPlayer.replayBlockStateUpTo(actionHandler.getCurrentActionIndex());
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