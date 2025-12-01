package com.reportsystem.spigot.overwatch;

import com.reportsystem.common.database.OverwatchDAO;
import com.reportsystem.common.models.Report;
import com.reportsystem.common.models.overwatch.*;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OverwatchManager {

    private final ReportSystemSpigot plugin;
    private final OverwatchDAO overwatchDAO;

    public OverwatchManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.overwatchDAO = new OverwatchDAO(plugin.getDatabaseManager());
    }

    /**
     * Get next report to review for player
     */
    public Optional<Integer> getNextReportForReviewer(Player reviewer) {
        try {
            Optional<OverwatchQueueItem> queueItem = overwatchDAO.getNextQueueItem(reviewer.getUniqueId());

            if (queueItem.isPresent()) {
                int reportId = queueItem.get().getReportId();

                // Assign to reviewer
                overwatchDAO.assignReportToReviewer(reportId, reviewer.getUniqueId(), plugin.getServerName());

                plugin.getLogger().info("[OVERWATCH] Assigned report #" + reportId +
                        " to reviewer " + reviewer.getName());

                return Optional.of(reportId);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to get next report: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    /**
     * Submit a review verdict
     */
    public void submitVerdict(Player reviewer, int reportId, String verdict, String category,
                             String subcategory, int reviewDuration) {
        try {
            // Create review
            OverwatchReview review = new OverwatchReview(
                0, // ID auto-generated
                reportId,
                reviewer.getUniqueId().toString(),
                reviewer.getName(),
                verdict,
                category,
                subcategory,
                100, // confidence
                null, // notes
                System.currentTimeMillis(),
                reviewDuration,
                plugin.getServerName()
            );

            // Save review
            overwatchDAO.submitReview(review);

            // Update reviewer stats
            updateReviewerStats(reviewer, verdict, reviewDuration);

            // Check if we have enough reviews for consensus
            processConsensus(reportId);

            plugin.getLogger().info("[OVERWATCH] Verdict submitted by " + reviewer.getName() +
                    " for report #" + reportId + ": " + verdict);

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to submit verdict: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update reviewer statistics
     */
    private void updateReviewerStats(Player reviewer, String verdict, int reviewDuration) {
        try {
            Optional<OverwatchStats> statsOpt = overwatchDAO.getStats(reviewer.getUniqueId());

            // Store previous stats for comparison (for level-up/rank-up detection)
            int previousLevel = statsOpt.map(OverwatchStats::getLevel).orElse(0);
            String previousRank = statsOpt.map(OverwatchStats::getRank).orElse("BRONZE");
            int previousXp = statsOpt.map(OverwatchStats::getXp).orElse(0);

            OverwatchStats stats;
            if (statsOpt.isPresent()) {
                stats = statsOpt.get();
                stats.setReviewerName(reviewer.getName());
                stats.addReview(verdict, reviewDuration);
            } else {
                stats = OverwatchStats.createNew(reviewer.getUniqueId().toString(), reviewer.getName());
                stats.addReview(verdict, reviewDuration);
            }

            overwatchDAO.createOrUpdateStats(stats);

            // Get XP gained
            int xpGained = verdict.equals("SKIP") ?
                plugin.getConfigManager().getConfig().getInt("overwatch.xp.skip-xp", 5) :
                plugin.getConfigManager().getConfig().getInt("overwatch.xp.guilty-innocent-xp", 15);

            // Notify player
            String rankColor = getRankColor(stats.getRank());
            reviewer.sendMessage("§a[Overwatch] §7İnceleme tamamlandı! §e+" +
                    xpGained + " XP §8| " +
                    rankColor + stats.getRank() + " §7Seviye " + stats.getLevel());

            // Execute reward commands if enabled
            if (plugin.getConfigManager().isRewardCommandsEnabled()) {
                executeRewardCommands(reviewer, verdict, stats, previousLevel, previousRank, xpGained);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to update stats: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Execute reward commands for completing a review
     */
    private void executeRewardCommands(Player reviewer, String verdict, OverwatchStats stats,
                                      int previousLevel, String previousRank, int xpGained) {
        String playerName = reviewer.getName();
        int currentLevel = stats.getLevel();
        String currentRank = stats.getRank();

        // Always execute: on-review-complete commands
        List<String> completeCommands = plugin.getConfigManager().getReviewCompleteCommands();
        for (String command : completeCommands) {
            String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
            executeConsoleCommand(processedCommand);
        }

        // Execute verdict-specific commands
        if ("GUILTY".equalsIgnoreCase(verdict) || "INNOCENT".equalsIgnoreCase(verdict)) {
            List<String> guiltyInnocentCommands = plugin.getConfigManager().getGuiltyInnocentCommands();
            for (String command : guiltyInnocentCommands) {
                String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
                executeConsoleCommand(processedCommand);
            }
        } else if ("SKIP".equalsIgnoreCase(verdict)) {
            List<String> skipCommands = plugin.getConfigManager().getSkipCommands();
            for (String command : skipCommands) {
                String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
                executeConsoleCommand(processedCommand);
            }
        }

        // Check for level-up
        if (currentLevel > previousLevel) {
            List<String> levelUpCommands = plugin.getConfigManager().getLevelUpCommands();
            for (String command : levelUpCommands) {
                String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
                executeConsoleCommand(processedCommand);
            }
            plugin.getLogger().info("[OVERWATCH] Player " + playerName + " leveled up to " + currentLevel);
        }

        // Check for rank-up
        if (!currentRank.equalsIgnoreCase(previousRank)) {
            List<String> rankUpCommands = plugin.getConfigManager().getRankUpCommands(currentRank);
            for (String command : rankUpCommands) {
                String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
                executeConsoleCommand(processedCommand);
            }
            plugin.getLogger().info("[OVERWATCH] Player " + playerName + " ranked up to " + currentRank);
        }
    }

    /**
     * Replace placeholders in reward commands
     */
    private String replacePlaceholders(String command, String playerName, String verdict, int xp, int level, String rank) {
        return command
                .replace("%player%", playerName)
                .replace("%verdict%", verdict)
                .replace("%xp%", String.valueOf(xp))
                .replace("%level%", String.valueOf(level))
                .replace("%rank%", rank);
    }

    /**
     * Execute command as console
     */
    private void executeConsoleCommand(String command) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            });
            plugin.getLogger().info("[OVERWATCH] Executed reward command: " + command);
        } catch (Exception e) {
            plugin.getLogger().warning("[OVERWATCH] Failed to execute command: " + command + " - " + e.getMessage());
        }
    }

    /**
     * Process consensus and execute action if needed
     */
    private void processConsensus(int reportId) {
        try {
            List<OverwatchReview> reviews = overwatchDAO.getReviewsForReport(reportId);

            // Minimum reviewer requirement
            int minReviewers = plugin.getConfig().getInt("overwatch.min-reviewers", 3);
            if (reviews.size() < minReviewers) {
                plugin.getLogger().info("[OVERWATCH] Report #" + reportId +
                        " needs " + (minReviewers - reviews.size()) + " more reviews");
                return;
            }

            // Calculate consensus
            long guiltyCount = reviews.stream()
                    .filter(r -> "GUILTY".equalsIgnoreCase(r.getVerdict()))
                    .count();

            double consensusPercentage = (guiltyCount / (double) reviews.size()) * 100.0;

            plugin.getLogger().info("[OVERWATCH] Report #" + reportId +
                    " consensus: " + String.format("%.1f", consensusPercentage) + "% guilty (" +
                    guiltyCount + "/" + reviews.size() + ")");

            // Mark as completed - NO AUTO-PUNISHMENT
            // Admins will see voting results in /reports command
            overwatchDAO.completeQueueItem(reportId);

            // Log voting results (no automatic action taken)
            OverwatchAction action = new OverwatchAction(
                reportId,
                "VOTING_COMPLETED",
                "Voting completed: " + guiltyCount + " guilty, " +
                (reviews.size() - guiltyCount) + " innocent/skip (" +
                String.format("%.1f", consensusPercentage) + "% guilty)",
                consensusPercentage,
                reviews.size(),
                (int) guiltyCount,
                System.currentTimeMillis(),
                "SYSTEM"
            );

            overwatchDAO.logAction(action);

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to process consensus: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // AUTO-PUNISHMENT REMOVED
    // Admins will see voting results in /reports command instead

    /**
     * Add report to overwatch queue
     */
    public void addReportToQueue(int reportId, int priority) {
        try {
            overwatchDAO.addToQueue(reportId, priority);
            plugin.getLogger().info("[OVERWATCH] Added report #" + reportId + " to queue (priority: " + priority + ")");
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to add report to queue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove report from Overwatch queue (when status changes to non-PENDING)
     */
    public void removeReportFromQueue(int reportId) {
        try {
            overwatchDAO.removeFromQueue(reportId);
            plugin.getLogger().info("[OVERWATCH] Removed report #" + reportId + " from queue");
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to remove report from queue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Unassign a report and return it to PENDING status
     * Used when reviewer closes verdict GUI without submitting
     */
    public void unassignReport(int reportId) {
        try {
            overwatchDAO.unassignReport(reportId);
            plugin.getLogger().info("[OVERWATCH] Unassigned report #" + reportId + " - returned to PENDING queue");
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to unassign report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update queue status
     */
    public void updateQueueStatus(int reportId, String status) {
        try {
            overwatchDAO.updateQueueStatus(reportId, status);
            plugin.getLogger().info("[OVERWATCH] Updated queue status for report #" + reportId + " to " + status);
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to update queue status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get reviewer statistics
     */
    public Optional<OverwatchStats> getReviewerStats(UUID uuid) {
        try {
            return overwatchDAO.getStats(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to get stats: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get top reviewers
     */
    public List<OverwatchStats> getTopReviewers(int limit) {
        try {
            return overwatchDAO.getTopReviewers(limit);
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to get top reviewers: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get pending queue count
     */
    public int getPendingQueueCount() {
        try {
            return overwatchDAO.getPendingQueueCount();
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Get weekly reviewed count
     */
    public int getWeeklyReviewedCount() {
        try {
            return overwatchDAO.getWeeklyReviewedCount();
        } catch (SQLException e) {
            return 0;
        }
    }

    /**
     * Get total punished count
     */
    public int getTotalPunishedCount() {
        try {
            return overwatchDAO.getTotalPunishedCount();
        } catch (SQLException e) {
            return 0;
        }
    }

    public OverwatchDAO getDAO() {
        return overwatchDAO;
    }

    private String getRankColor(String rank) {
        switch (rank.toUpperCase()) {
            case "DIAMOND": return "§b";
            case "GOLD": return "§6";
            case "SILVER": return "§7";
            default: return "§c";
        }
    }

    /**
     * Get voting statistics for a report
     * Returns: "13 guilty, 7 innocent" or null if no votes
     */
    public String getVotingStats(int reportId) {
        try {
            List<OverwatchReview> reviews = overwatchDAO.getReviewsForReport(reportId);

            if (reviews.isEmpty()) {
                return null;
            }

            long guiltyCount = reviews.stream()
                    .filter(r -> "GUILTY".equalsIgnoreCase(r.getVerdict()))
                    .count();

            long innocentCount = reviews.stream()
                    .filter(r -> "INNOCENT".equalsIgnoreCase(r.getVerdict()))
                    .count();

            long skipCount = reviews.stream()
                    .filter(r -> "SKIP".equalsIgnoreCase(r.getVerdict()))
                    .count();

            // Get localized format
            String format;
            if (skipCount > 0) {
                format = plugin.getMessageManager().getMessage("overwatch.verdict.voting-stats.format-with-skip");
            } else {
                format = plugin.getMessageManager().getMessage("overwatch.verdict.voting-stats.format");
            }

            // Replace placeholders
            return format
                    .replace("%guilty%", String.valueOf(guiltyCount))
                    .replace("%innocent%", String.valueOf(innocentCount))
                    .replace("%skip%", String.valueOf(skipCount));

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH] Failed to get voting stats: " + e.getMessage());
            return null;
        }
    }
}
