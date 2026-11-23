package com.reportsystem.spigot.hooks;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.common.service.ReportService;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.OverwatchManager;
import com.reportsystem.spigot.trust.TrustLevelManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
 *
 * H-41: PAPI istekleri genelde ana thread'den (scoreboard/tab her tick) gelir. onRequest
 * ASLA veritabanına gitmez: değer önbellekten döner, önbellek 30 sn'den eskiyse (veya yoksa)
 * oyuncu+veri kaynağı başına tek bir async tazeleme planlanır. İlk istekte varsayılan döner.
 */
public class ReportSystemPlaceholders extends PlaceholderExpansion {

    /** Bu süreden yeni girdi taze sayılır, tazeleme planlanmaz. */
    private static final long FRESH_MS = 30_000L;
    /** Bu süreden eski girdiler (ör. çıkmış oyuncular) tazeleme sırasında silinir. */
    private static final long PURGE_MS = 10 * 60_000L;

    // Veri kaynağı grupları — aynı gruptaki placeholder'lar tek DB sonucunu paylaşır
    private static final String GROUP_REPORTS = "reports";
    private static final String GROUP_TRUST = "trust";
    private static final String GROUP_OVERWATCH = "overwatch";

    private final ReportSystemSpigot plugin;
    /** Anahtar: uuid + ":" + grup. Değer: Integer (reports), Double (trust), Optional&lt;OverwatchStats&gt;. */
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    /** Async tazelemesi sürmekte olan anahtarlar (anahtar başına tek görev). */
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    private record Entry(Object value, long at) {}

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

        switch (params.toLowerCase(Locale.ROOT)) {
            case "reports": {
                if (plugin.getReportService() == null) return "0";
                Object value = cachedValue(player, GROUP_REPORTS);
                return value instanceof Integer count ? String.valueOf(count) : "0";
            }

            case "trust_level": {
                TrustLevelManager trust = plugin.getTrustLevelManager();
                if (trust == null) return "N/A";
                Object value = cachedValue(player, GROUP_TRUST);
                if (!(value instanceof Double points)) return "N/A";
                return trust.getLocalizedLevelName(trust.getTrustLevelFromPoints(points));
            }

            case "trust_points": {
                if (plugin.getTrustLevelManager() == null) return "0";
                Object value = cachedValue(player, GROUP_TRUST);
                if (!(value instanceof Double points)) return "0";
                return String.format(Locale.ROOT, "%.1f", points);
            }

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

        Object value = cachedValue(player, GROUP_OVERWATCH);
        if (!(value instanceof Optional<?> statsOpt) || statsOpt.isEmpty()
                || !(statsOpt.get() instanceof OverwatchStats stats)) {
            return "accuracy".equals(stat) ? "N/A" : "0";
        }

        switch (stat) {
            case "rank": return stats.getRank();
            case "level": return String.valueOf(stats.getLevel());
            case "xp": return String.valueOf(stats.getXp());
            case "reviews": return String.valueOf(stats.getTotalReviews());
            case "accuracy": return stats.isAccuracyVisible()
                    ? String.format(Locale.ROOT, "%.1f%%", stats.getAccuracy()) : "N/A";
            default: return "0";
        }
    }

    /**
     * Önbellekteki değeri döndürür (yoksa null). Girdi yoksa veya 30 sn'den eskiyse ve bu anahtar
     * için zaten bir tazeleme sürmüyorsa async tazeleme planlar. Veritabanına DOKUNMAZ.
     */
    private Object cachedValue(OfflinePlayer player, String group) {
        UUID uuid = player.getUniqueId();
        String key = uuid + ":" + group;
        Entry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if ((entry == null || now - entry.at() >= FRESH_MS) && refreshing.add(key)) {
            scheduleRefresh(key, group, uuid, player.getName());
        }
        return entry == null ? null : entry.value();
    }

    private void scheduleRefresh(String key, String group, UUID uuid, String playerName) {
        if (!plugin.isEnabled()) {
            refreshing.remove(key);
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Object value = load(group, uuid, playerName);
                    long at = System.currentTimeMillis();
                    if (value != null) {
                        cache.put(key, new Entry(value, at));
                    }
                    cache.entrySet().removeIf(e -> at - e.getValue().at() > PURGE_MS);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[PAPI] Placeholder refresh failed (" + group + ")", t);
                    // Hata durumunda eski değeri 30 sn daha kullan (DB düşükken her istekte yeniden deneme)
                    Entry old = cache.get(key);
                    if (old != null) {
                        cache.put(key, new Entry(old.value(), System.currentTimeMillis()));
                    }
                } finally {
                    refreshing.remove(key);
                }
            });
        } catch (RuntimeException e) {
            // Eklenti kapanırken planlama reddedilebilir (IllegalPluginAccessException)
            refreshing.remove(key);
        }
    }

    /** YALNIZ async görev içinden çağrılır — veritabanı erişimi burada. */
    private Object load(String group, UUID uuid, String playerName) {
        switch (group) {
            case GROUP_REPORTS: {
                ReportService service = plugin.getReportService();
                if (service == null) return null;
                if (playerName == null) return 0;
                return service.getReportCount(playerName);
            }
            case GROUP_TRUST: {
                TrustLevelManager trust = plugin.getTrustLevelManager();
                if (trust == null) return null;
                if (playerName == null) return 0.0d;
                return trust.getViolationPoints(uuid, playerName);
            }
            case GROUP_OVERWATCH: {
                OverwatchManager overwatch = plugin.getOverwatchManager();
                if (overwatch == null) return null;
                Optional<OverwatchStats> stats = overwatch.getReviewerStats(uuid);
                return stats == null ? Optional.empty() : stats;
            }
            default:
                return null;
        }
    }
}
