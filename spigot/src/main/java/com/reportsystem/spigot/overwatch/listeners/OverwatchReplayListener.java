package com.reportsystem.spigot.overwatch.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.OverwatchVerdictGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for Overwatch Replay Events
 * Opens verdict GUI when replay ends
 */
public class OverwatchReplayListener implements Listener {

    private final ReportSystemSpigot plugin;

    // Track which players are reviewing (for Overwatch)
    // UUID -> (reportId, startTime)
    private final Map<UUID, OverwatchReviewData> activeReviews = new HashMap<>();

    public OverwatchReplayListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;

        // Schedule task to check for replay completion
        startReplayCheckTask();
    }

    /**
     * Start a review session for a player
     */
    public void startReview(Player player, int reportId) {
        activeReviews.put(player.getUniqueId(), new OverwatchReviewData(reportId, System.currentTimeMillis()));

        plugin.getLogger().info("[OVERWATCH-REPLAY] Started review session for " + player.getName() +
                " (Report #" + reportId + ")");
    }

    /**
     * Check if player is currently reviewing
     */
    public boolean isReviewing(UUID playerUUID) {
        return activeReviews.containsKey(playerUUID);
    }

    /**
     * Get report ID being reviewed by player
     */
    public Integer getReviewingReportId(UUID playerUUID) {
        OverwatchReviewData data = activeReviews.get(playerUUID);
        return data != null ? data.reportId : null;
    }

    /**
     * Task to check for replay completion every second
     */
    private void startReplayCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check all active reviews
            for (UUID playerUUID : new HashMap<>(activeReviews).keySet()) {
                Player player = Bukkit.getPlayer(playerUUID);

                if (player == null || !player.isOnline()) {
                    // Player left - remove review
                    activeReviews.remove(playerUUID);
                    continue;
                }

                // Check if replay is still active
                boolean isWatching = plugin.getReplayManager().isWatchingReplay(player);

                if (!isWatching) {
                    // Replay ended - open verdict GUI
                    OverwatchReviewData reviewData = activeReviews.remove(playerUUID);

                    if (reviewData != null) {
                        openVerdictGUI(player, reviewData.reportId, reviewData.startTime);
                    }
                }
            }
        }, 20L, 20L); // Check every second
    }

    /**
     * Open verdict GUI for player
     */
    private void openVerdictGUI(Player player, int reportId, long reviewStartTime) {
        // Wait 1 second before opening GUI (let player settle)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String msg = plugin.getMessageManager().getMessage("overwatch.review.replay-completed");
                player.sendMessage(plugin.getMessageManager().colorize(msg));
                new OverwatchVerdictGUI(plugin, player, reportId, reviewStartTime).open();
            }
        }, 20L);
    }

    /**
     * Handle player quit - cancel review
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        if (activeReviews.containsKey(playerUUID)) {
            plugin.getLogger().info("[OVERWATCH-REPLAY] Player " + event.getPlayer().getName() +
                    " quit during review - review cancelled");
            activeReviews.remove(playerUUID);
        }
    }

    /**
     * Inner class to store review data
     */
    private static class OverwatchReviewData {
        private final int reportId;
        private final long startTime;

        public OverwatchReviewData(int reportId, long startTime) {
            this.reportId = reportId;
            this.startTime = startTime;
        }
    }
}
