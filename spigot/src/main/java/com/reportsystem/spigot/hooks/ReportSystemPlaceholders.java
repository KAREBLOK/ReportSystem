package com.reportsystem.spigot.hooks;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.trust.TrustLevelManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PlaceholderAPI Expansion for ReportSystem
 *
 * Placeholders:
 *   %reportsystem_reports%          - Oyuncuya açılan toplam rapor sayısı
 *   %reportsystem_trust_level%      - Hesap durumu seviyesi (Mükemmel, İyi, vb.)
 *   %reportsystem_trust_points%     - İhlal puanı
 *   %reportsystem_overwatch_rank%   - Overwatch rütbesi
 *   %reportsystem_overwatch_level%  - Overwatch seviyesi
 *   %reportsystem_overwatch_xp%     - Overwatch XP
 *   %reportsystem_overwatch_reviews%- Toplam inceleme sayısı
 *   %reportsystem_overwatch_accuracy% - Doğruluk oranı
 */
public class ReportSystemPlaceholders extends PlaceholderExpansion {

    private final ReportSystemSpigot plugin;

    public ReportSystemPlaceholders(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "reportsystem";
    }

    @Override
    public @NotNull String getAuthor() {
        return "KAREBLOK.TC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "reports":
                return String.valueOf(plugin.getReportService().getReportCount(player.getName()));

            case "trust_level":
                if (plugin.getTrustLevelManager() == null) return "N/A";
                TrustLevelManager.TrustLevel level = plugin.getTrustLevelManager()
                        .getTrustLevel(player.getUniqueId(), player.getName());
                return plugin.getTrustLevelManager().getLocalizedLevelName(level);

            case "trust_points":
                if (plugin.getTrustLevelManager() == null) return "0";
                return String.format("%.1f", plugin.getTrustLevelManager()
                        .getViolationPoints(player.getUniqueId(), player.getName()));

            case "overwatch_rank":
                return getOverwatchStat(player, "rank");

            case "overwatch_level":
                return getOverwatchStat(player, "level");

            case "overwatch_xp":
                return getOverwatchStat(player, "xp");

            case "overwatch_reviews":
                return getOverwatchStat(player, "reviews");

            case "overwatch_accuracy":
                return getOverwatchStat(player, "accuracy");

            default:
                return null;
        }
    }

    private String getOverwatchStat(OfflinePlayer player, String stat) {
        if (plugin.getOverwatchManager() == null) return "N/A";

        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager()
                .getReviewerStats(player.getUniqueId());

        if (!statsOpt.isPresent()) return "0";

        OverwatchStats stats = statsOpt.get();
        switch (stat) {
            case "rank": return stats.getRank();
            case "level": return String.valueOf(stats.getLevel());
            case "xp": return String.valueOf(stats.getXp());
            case "reviews": return String.valueOf(stats.getTotalReviews());
            case "accuracy": return stats.isAccuracyVisible()
                    ? String.format("%.1f%%", stats.getAccuracy()) : "N/A";
            default: return "0";
        }
    }
}
