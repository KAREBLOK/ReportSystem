package com.reportsystem.spigot.overwatch;

import com.reportsystem.common.database.OverwatchDAO;
import com.reportsystem.common.models.Report;
import com.reportsystem.common.models.overwatch.*;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OverwatchManager {

    private final ReportSystemSpigot plugin;
    private final OverwatchDAO overwatchDAO;
    /** İstatistik oku-değiştir-yaz bölgeleri için tek kilit (yalnız async thread'ler tutar) [THR-20]. */
    private final Object statsLock = new Object();

    public OverwatchManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.overwatchDAO = new OverwatchDAO(plugin.getDatabaseManager());
    }

    /**
     * Get next report to review for player
     */
    public Optional<Integer> getNextReportForReviewer(Player reviewer) {
        try {
            Optional<OverwatchQueueItem> queueItem = overwatchDAO.getNextQueueItem(reviewer.getUniqueId(), reviewer.getName());

            if (queueItem.isPresent()) {
                int reportId = queueItem.get().getReportId();

                // Assign to reviewer (returns false if report was completed or removed)
                boolean assigned = overwatchDAO.assignReportToReviewer(reportId, reviewer.getUniqueId(), plugin.getServerName());

                if (!assigned) {
                    plugin.debug("[OVERWATCH] Report #" + reportId +
                            " could not be assigned (already completed or removed)");
                    return Optional.empty();
                }

                plugin.debug("[OVERWATCH] Assigned report #" + reportId +
                        " to reviewer " + reviewer.getName());

                return Optional.of(reportId);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to get next report", e);
        }

        return Optional.empty();
    }

    /** submitVerdict sonucu. */
    public enum VerdictResult { ACCEPTED, REJECTED_INVOLVED, ERROR }

    /**
     * Submit a review verdict.
     * UYARI: Bloklayan JDBC içerir; ASYNC thread'den çağrılmalı. Bukkit çağrıları
     * (mesaj) içeride ana thread'e taşınır. Sonucu çağıran işler.
     */
    public VerdictResult submitVerdict(Player reviewer, int reportId, String verdict, String category,
                             String subcategory, int reviewDuration) {
        try {
            // Kendi raporunu (raporlayan/raporlanan) inceleme koruması
            if (overwatchDAO.isReviewerInvolved(reportId, reviewer.getUniqueId(), reviewer.getName())) {
                plugin.getLogger().warning("[OVERWATCH] " + reviewer.getName() +
                        " tried to review own report #" + reportId + " - verdict rejected");
                return VerdictResult.REJECTED_INVOLVED;
            }

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

            plugin.debug("[OVERWATCH] Verdict submitted by " + reviewer.getName() +
                    " for report #" + reportId + ": " + verdict);
            return VerdictResult.ACCEPTED;

        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to submit verdict", e);
            return VerdictResult.ERROR;
        }
    }

    /**
     * Birden çok rapor için oylama özetini tek seferde çeker (ASYNC thread'den çağır).
     * Oy yoksa haritada anahtar bulunmaz.
     */
    public java.util.Map<Integer, String> getVotingStatsForReports(List<Integer> reportIds) {
        java.util.Map<Integer, String> result = new java.util.HashMap<>();
        for (Integer id : reportIds) {
            if (id == null || result.containsKey(id)) continue;
            String stats = getVotingStats(id);
            if (stats != null) result.put(id, stats);
        }
        return result;
    }

    /**
     * Update reviewer statistics
     */
    private void updateReviewerStats(Player reviewer, String verdict, int reviewDuration) {
        try {
            final int previousLevel;
            final String previousRank;
            final OverwatchStats stats;

            // Yalnız DB oku-değiştir-yaz bölgesi kilitli; mesaj / ödül komutu kilit DIŞINDA [THR-20]
            synchronized (statsLock) {
                Optional<OverwatchStats> statsOpt = overwatchDAO.getStats(reviewer.getUniqueId());

                // Store previous stats for comparison (for level-up/rank-up detection)
                previousLevel = statsOpt.map(OverwatchStats::getLevel).orElse(0);
                previousRank = statsOpt.map(OverwatchStats::getRank).orElse("BRONZE");

                if (statsOpt.isPresent()) {
                    stats = statsOpt.get();
                    stats.setReviewerName(reviewer.getName());
                    stats.addReview(verdict, reviewDuration);
                } else {
                    stats = OverwatchStats.createNew(reviewer.getUniqueId().toString(), reviewer.getName());
                    stats.addReview(verdict, reviewDuration);
                }

                overwatchDAO.createOrUpdateStats(stats);
            }

            // Get XP gained
            int xpGained = verdict.equals("SKIP") ?
                plugin.getConfigManager().getConfig().getInt("overwatch.xp.skip-xp", 5) :
                plugin.getConfigManager().getConfig().getInt("overwatch.xp.guilty-innocent-xp", 15);

            // Notify player
            String rankColor = getRankColor(stats.getRank());
            final String completedMsg = plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.review.completed")
                            .replace("%xp%", String.valueOf(xpGained))
                            .replace("%rank%", rankColor + stats.getRank())
                            .replace("%level%", String.valueOf(stats.getLevel())));
            // Bu metot async thread'den çağrılır: oyuncu mesajı ana thread'de gönderilir
            if (plugin.isEnabled()) { // kapanışta runTask IllegalPluginAccessException atar [THR-17]
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (reviewer.isOnline()) reviewer.sendMessage(completedMsg);
                });
            }

            // Execute reward commands if enabled
            if (plugin.getConfigManager().isRewardCommandsEnabled()) {
                executeRewardCommands(reviewer, verdict, stats, previousLevel, previousRank, xpGained);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to update stats", e);
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
            plugin.debug("[OVERWATCH] Player " + playerName + " leveled up to " + currentLevel);
        }

        // Check for rank-up
        if (!currentRank.equalsIgnoreCase(previousRank)) {
            List<String> rankUpCommands = plugin.getConfigManager().getRankUpCommands(currentRank);
            for (String command : rankUpCommands) {
                String processedCommand = replacePlaceholders(command, playerName, verdict, xpGained, currentLevel, currentRank);
                executeConsoleCommand(processedCommand);
            }
            plugin.debug("[OVERWATCH] Player " + playerName + " ranked up to " + currentRank);
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
            plugin.debug("[OVERWATCH] Executed reward command: " + command);
        } catch (Exception e) {
            plugin.getLogger().warning("[OVERWATCH] Failed to execute command: " + command + " - " + e.getMessage());
        }
    }

    /**
     * Process consensus with weighted voting and update reviewer accuracies
     */
    private void processConsensus(int reportId) {
        try {
            List<OverwatchReview> reviews = overwatchDAO.getReviewsForReport(reportId);

            boolean autoComplete = plugin.getConfigManager().isAutoCompleteQueueEnabled();
            if (!autoComplete) {
                // Açık Topluluk Oylaması Modu (Varsayılan):
                // Rapor kuyrukta COMPLETED yapılmaz; unassignReport ile PENDING durumunda tutulur.
                // Böylece 4., 5., 10. herkes izleyip oy kullanabilir. Rapor ancak yetkili
                // /reports üzerinden onaylayana/reddedene kadar kuyrukta aktif kalır.
                overwatchDAO.unassignReport(reportId);
                plugin.debug("[OVERWATCH] Report #" + reportId +
                        " kept in open queue for more reviews (current reviews: " + reviews.size() + ")");
                return;
            }

            // Minimum reviewer requirement (Sadece auto-complete-queue: true ise çalışır)
            int minReviewers = plugin.getConfigManager().getMinReviewers();
            if (reviews.size() < minReviewers) {
                plugin.debug("[OVERWATCH] Report #" + reportId +
                        " needs " + (minReviewers - reviews.size()) + " more reviews");
                overwatchDAO.unassignReport(reportId);
                return;
            }

            // Calculate WEIGHTED consensus
            double guiltyWeight = 0;
            double totalWeight = 0;

            for (OverwatchReview review : reviews) {
                if ("SKIP".equalsIgnoreCase(review.getVerdict())) continue;

                double weight = getReviewerWeight(review.getReviewerUUID());
                totalWeight += weight;
                if ("GUILTY".equalsIgnoreCase(review.getVerdict())) {
                    guiltyWeight += weight;
                }
            }

            double consensusPercentage = totalWeight > 0 ? (guiltyWeight / totalWeight) * 100.0 : 0;
            boolean consensusGuilty = consensusPercentage >= plugin.getConfig().getDouble("overwatch.consensus-threshold", 70.0);

            // Raw counts for logging
            long rawGuiltyCount = reviews.stream()
                    .filter(r -> "GUILTY".equalsIgnoreCase(r.getVerdict())).count();

            plugin.debug("[OVERWATCH] Report #" + reportId +
                    " weighted consensus: " + String.format("%.1f", consensusPercentage) + "% guilty (" +
                    rawGuiltyCount + "/" + reviews.size() + " votes, weighted: " +
                    String.format("%.1f", guiltyWeight) + "/" + String.format("%.1f", totalWeight) + ")");

            // Mark as completed — koşullu: eşzamanlı iki karar aynı uzlaşmayı iki kez
            // saymasın (logAction + updateReviewerAccuracies yalnız kazanan thread'de) [DATA-26]
            if (overwatchDAO.completeQueueItem(reportId) == 0) {
                plugin.debug("[OVERWATCH] Report #" + reportId +
                        " consensus already completed (or not in queue) - skipping duplicate processing");
                return;
            }

            // Log voting results
            OverwatchAction action = new OverwatchAction(
                reportId,
                "VOTING_COMPLETED",
                "Voting completed: " + rawGuiltyCount + " guilty, " +
                (reviews.size() - rawGuiltyCount) + " innocent/skip (" +
                String.format("%.1f", consensusPercentage) + "% weighted guilty)",
                consensusPercentage,
                reviews.size(),
                (int) rawGuiltyCount,
                System.currentTimeMillis(),
                "SYSTEM"
            );
            overwatchDAO.logAction(action);

            // Update reviewer accuracies based on consensus result
            updateReviewerAccuracies(reviews, consensusGuilty);

        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to process consensus", e);
        }
    }

    /**
     * Get reviewer's vote weight from their stats
     */
    private double getReviewerWeight(String reviewerUuid) {
        try {
            Optional<OverwatchStats> stats = overwatchDAO.getStatsByUuid(reviewerUuid);
            return stats.map(OverwatchStats::getVoteWeight).orElse(1.0);
        } catch (SQLException e) {
            return 1.0;
        }
    }

    /**
     * Consensus sonrası her reviewer'ın doğruluk oranını güncelle
     * Doğru oy veren → correctVerdicts++, yanlış veren → değişmez
     */
    private void updateReviewerAccuracies(List<OverwatchReview> reviews, boolean consensusGuilty) {
        String correctVerdict = consensusGuilty ? "GUILTY" : "INNOCENT";

        for (OverwatchReview review : reviews) {
            if ("SKIP".equalsIgnoreCase(review.getVerdict())) continue;

            try {
                // Oku-değiştir-yaz atomik olsun: eşzamanlı karar/istatistik güncellemesiyle ezişme olmasın [THR-20]
                synchronized (statsLock) {
                    Optional<OverwatchStats> statsOpt = overwatchDAO.getStatsByUuid(review.getReviewerUUID());
                    if (statsOpt.isEmpty()) continue;

                    OverwatchStats stats = statsOpt.get();

                    if (correctVerdict.equalsIgnoreCase(review.getVerdict())) {
                        stats.addCorrectVerdict();
                    } else {
                        stats.recalculateAccuracy();
                    }

                    overwatchDAO.createOrUpdateStats(stats);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[OVERWATCH] Failed to update accuracy for " +
                        review.getReviewerName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Yetkili kararı ile Overwatch oylamasını sonlandır ve doğruluk oranlarını güncelle.
     * /reports menüsünden yetkili raporu Onayladığında (guiltyVerdict = true) veya
     * Reddettiğinde (guiltyVerdict = false) çağrılır.
     * <p>ASYNC thread'den çağrılmalı (DB erişimi + istatistik kilidi).
     */
    public void resolveReportVoting(int reportId, boolean guiltyVerdict) {
        try {
            List<OverwatchReview> reviews = overwatchDAO.getReviewsForReport(reportId);
            if (!reviews.isEmpty()) {
                // Yetkili kararına göre doğruluk oranlarını güncelle
                updateReviewerAccuracies(reviews, guiltyVerdict);

                long rawGuiltyCount = reviews.stream()
                        .filter(r -> "GUILTY".equalsIgnoreCase(r.getVerdict())).count();
                double consensusPercentage = reviews.size() > 0 ? ((double) rawGuiltyCount / reviews.size()) * 100.0 : 0.0;

                OverwatchAction action = new OverwatchAction(
                    reportId,
                    guiltyVerdict ? "STAFF_ACCEPTED" : "STAFF_REJECTED",
                    "Resolved by staff (" + (guiltyVerdict ? "Guilty" : "Innocent") + "): " +
                    rawGuiltyCount + " guilty, " + (reviews.size() - rawGuiltyCount) + " innocent/skip",
                    consensusPercentage,
                    reviews.size(),
                    (int) rawGuiltyCount,
                    System.currentTimeMillis(),
                    "STAFF"
                );
                overwatchDAO.logAction(action);

                plugin.debug("[OVERWATCH] Report #" + reportId + " voting resolved by staff: " +
                        (guiltyVerdict ? "GUILTY" : "INNOCENT") + " across " + reviews.size() + " community reviews");
            }

            // Raporu Overwatch kuyruğundan kaldır
            removeReportFromQueue(reportId);

        } catch (SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to resolve voting for report #" + reportId, e);
        }
    }

    /**
     * Kuyruk yazımlarını ana thread'den çıkarır: ana thread'den (ve eklenti açıkken) çağrıldıysa
     * async görev olarak çalıştırır, aksi halde (zaten async thread ya da kapanış sırasında)
     * senkron çalıştırır — kapanışta yazım kaybolmaz, çağıranlar değişmez [THR-01][THR-17].
     * Not: async yazımlar arasında sıra garantisi yoktur.
     */
    private void runDb(Runnable r) {
        if (Bukkit.isPrimaryThread() && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
        } else {
            r.run();
        }
    }

    /**
     * Add report to overwatch queue (ana thread'den çağrılırsa async yazılır)
     */
    public void addReportToQueue(int reportId, int priority) {
        runDb(() -> {
            try {
                overwatchDAO.addToQueue(reportId, priority);
                plugin.debug("[OVERWATCH] Added report #" + reportId + " to queue (priority: " + priority + ")");
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to add report to queue", e);
            }
        });
    }

    /**
     * Remove report from Overwatch queue (when status changes to non-PENDING)
     * (ana thread'den çağrılırsa async yazılır)
     */
    public void removeReportFromQueue(int reportId) {
        runDb(() -> {
            try {
                overwatchDAO.removeFromQueue(reportId);
                plugin.debug("[OVERWATCH] Removed report #" + reportId + " from queue");
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to remove report from queue", e);
            }
        });
    }

    /**
     * Unassign a report and return it to PENDING status
     * Used when reviewer closes verdict GUI without submitting
     * (ana thread'den çağrılırsa async yazılır)
     */
    public void unassignReport(int reportId) {
        runDb(() -> {
            try {
                overwatchDAO.unassignReport(reportId);
                plugin.debug("[OVERWATCH] Unassigned report #" + reportId + " - returned to PENDING queue");
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to unassign report", e);
            }
        });
    }

    /**
     * Update queue status (ana thread'den çağrılırsa async yazılır)
     */
    public void updateQueueStatus(int reportId, String status) {
        runDb(() -> {
            try {
                overwatchDAO.updateQueueStatus(reportId, status);
                plugin.debug("[OVERWATCH] Updated queue status for report #" + reportId + " to " + status);
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to update queue status", e);
            }
        });
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

    /**
     * Get reviewer stats by UUID string (for consensus processing)
     */
    public Optional<OverwatchStats> getReviewerStatsByUuid(String uuid) {
        try {
            return overwatchDAO.getStatsByUuid(uuid);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public OverwatchDAO getDAO() {
        return overwatchDAO;
    }

    private String getRankColor(String rank) {
        switch (rank.toUpperCase(java.util.Locale.ROOT)) {
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
