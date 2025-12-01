package com.reportsystem.spigot.replay;

import com.reportsystem.common.replay.actions.ReplayAction;

import java.util.List;

public class ReplayStateManager {

    private final ReplayPlayer replayPlayer;

    private boolean isPaused = false;
    private double playbackSpeed = 1.0;
    private long pausedTime = 0; // Duraklatıldığı zaman
    private long totalPausedDuration = 0; // Toplam duraklatma süresi

    public ReplayStateManager(ReplayPlayer replayPlayer) {
        this.replayPlayer = replayPlayer;
    }

    /**
     * Duraklatma durumunu ayarlar
     */
    public void setPaused(boolean paused) {
        if (paused && !isPaused) {
            // Duraklatma başlıyor
            pausedTime = System.currentTimeMillis();
        } else if (!paused && isPaused) {
            // Duraklatma bitiyor
            totalPausedDuration += System.currentTimeMillis() - pausedTime;
        }
        this.isPaused = paused;
    }

    /**
     * Oynatma hızını değiştirir
     */
    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = Math.max(0.25, Math.min(4.0, speed)); // 0.25x - 4x arası
    }

    /**
     * Start time'ı yeniden hesaplar (resume için)
     */
    public void recalculateStartTime() {
        replayPlayer.getActionHandler().recalculateStartTime();
    }

    /**
     * Elapsed time hesaplar - Duraklatma süresini çıkar
     */
    public long getElapsedTime() {
        if (replayPlayer.getState() == ReplayPlayer.ReplayState.STOPPED || replayPlayer.getActions().isEmpty()) {
            return 0;
        }

        long startTime = replayPlayer.getActionHandler().getStartTime();
        long currentTime = System.currentTimeMillis();

        // Eğer duraklatılmışsa, şu anki zamanı duraklatma zamanı olarak kullan
        if (isPaused) {
            currentTime = pausedTime;
        }

        // Toplam geçen süre - duraklatma süreleri
        long elapsed = (long) ((currentTime - startTime - totalPausedDuration) * playbackSpeed);

        // Negatif olamaz
        return Math.max(0, elapsed);
    }

    /**
     * Total time hesaplar
     */
    public long getTotalTime() {
        List<ReplayAction> actions = replayPlayer.getActions();
        if (actions.isEmpty()) return 0;
        return actions.get(actions.size() - 1).getTimestamp() - actions.get(0).getTimestamp();
    }

    /**
     * Duraklatma sürelerini sıfırlar
     */
    public void resetPausedTime() {
        pausedTime = 0;
        totalPausedDuration = 0;
    }

    // Getter'lar
    public boolean isPaused() { return isPaused; }
    public double getPlaybackSpeed() { return playbackSpeed; }
    public long getTotalPausedDuration() { return totalPausedDuration; }
}