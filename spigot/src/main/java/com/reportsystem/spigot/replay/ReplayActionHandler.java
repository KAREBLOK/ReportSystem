package com.reportsystem.spigot.replay;

import com.reportsystem.common.replay.actions.ReplayAction;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class ReplayActionHandler {

    private final JavaPlugin plugin;
    private final ReplayPlayer replayPlayer;
    private final List<ReplayAction> actions;

    private BukkitTask playbackTask;
    private int currentActionIndex = 0;
    private long startTime;

    public ReplayActionHandler(JavaPlugin plugin, ReplayPlayer replayPlayer, List<ReplayAction> actions) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
        this.actions = actions;
    }

    /**
     * Playback'i başlatır
     */
    public void startPlayback() {
        startTime = System.currentTimeMillis();
        currentActionIndex = 0;

        // Playback task'ı başlat - HER TICK ÇALIŞTIR
        playbackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processNextAction, 0L, 1L);
    }

    /**
     * Playback'i durdurur
     */
    public void stopPlayback() {
        if (playbackTask != null) {
            playbackTask.cancel();
            playbackTask = null;
        }
    }

    /**
     * Bir sonraki action'ı işler
     */
    private void processNextAction() {
        if (replayPlayer.isPaused() || replayPlayer.getState() != ReplayPlayer.ReplayState.PLAYING) {
            return;
        }

        if (currentActionIndex >= actions.size()) {
            replayPlayer.finish();
            return;
        }

        ReplayAction currentAction = actions.get(currentActionIndex);
        long elapsedTime = (long) ((System.currentTimeMillis() - startTime) * replayPlayer.getPlaybackSpeed());
        long actionTime = currentAction.getTimestamp() - actions.get(0).getTimestamp();

        if (elapsedTime >= actionTime) {
            // Action'ı oynat
            replayPlayer.getActionPlayer().playAction(currentAction);
            currentActionIndex++;
        }
    }

    /**
     * Progress bilgisi döndürür (0.0 - 1.0 arası)
     */
    public double getProgress() {
        if (actions.isEmpty()) return 0.0;
        return (double) currentActionIndex / actions.size();
    }

    /**
     * Belirli bir yüzdeye atlar
     */
    public void seekToPercent(double percent) {
        if (percent < 0.0) percent = 0.0;
        if (percent > 1.0) percent = 1.0;

        int targetIndex = (int) (actions.size() * percent);
        seekToIndex(targetIndex);
    }

    /**
     * Belirli bir action index'ine atlar
     */
    private void seekToIndex(int targetIndex) {
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= actions.size()) targetIndex = actions.size() - 1;

        currentActionIndex = targetIndex;

        // Zamanı yeniden hesapla
        if (currentActionIndex < actions.size()) {
            long targetTime = actions.get(currentActionIndex).getTimestamp() - actions.get(0).getTimestamp();
            startTime = System.currentTimeMillis() - (long)(targetTime / replayPlayer.getPlaybackSpeed());
        }
    }

    /**
     * İleri sar (saniye cinsinden)
     */
    public void forward(int seconds) {
        long forwardTime = seconds * 1000;
        long currentElapsed = (long) ((System.currentTimeMillis() - startTime) * replayPlayer.getPlaybackSpeed());
        long targetTime = currentElapsed + forwardTime;

        // Hedef zamanı bulacak action index'ini bul
        int targetIndex = currentActionIndex;
        for (int i = currentActionIndex; i < actions.size(); i++) {
            if (actions.get(i).getTimestamp() - actions.get(0).getTimestamp() >= targetTime) {
                targetIndex = i;
                break;
            }
        }

        seekToIndex(targetIndex);
    }

    /**
     * Geri sar (saniye cinsinden)
     */
    public void rewind(int seconds) {
        long rewindTime = seconds * 1000;
        long currentElapsed = (long) ((System.currentTimeMillis() - startTime) * replayPlayer.getPlaybackSpeed());
        long targetTime = Math.max(0, currentElapsed - rewindTime);

        // Hedef zamanı bulacak action index'ini bul
        int targetIndex = 0;
        for (int i = 0; i < currentActionIndex; i++) {
            if (actions.get(i).getTimestamp() - actions.get(0).getTimestamp() >= targetTime) {
                targetIndex = i;
                break;
            }
        }

        seekToIndex(targetIndex);
    }

    /**
     * Start time'ı yeniden hesaplar
     */
    public void recalculateStartTime() {
        if (currentActionIndex < actions.size()) {
            long targetTime = actions.get(currentActionIndex).getTimestamp() - actions.get(0).getTimestamp();
            startTime = System.currentTimeMillis() - (long)(targetTime / replayPlayer.getPlaybackSpeed());
        }
    }

    // Getter'lar
    public int getCurrentActionIndex() { return currentActionIndex; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
}