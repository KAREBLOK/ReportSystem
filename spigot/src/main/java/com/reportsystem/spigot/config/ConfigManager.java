package com.reportsystem.spigot.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    // General Settings
    public boolean isDebugEnabled() {
        return config.getBoolean("general.debug", false);
    }

    /** Desteklenen dil kodları (messages_<dil>.yml jar'da bulunanlar). */
    private static final java.util.Set<String> SUPPORTED_LANGUAGES = java.util.Set.of("en", "tr");
    private volatile String lastWarnedLanguage;

    /**
     * I18N-14 / K-h: ham dil değerini tek yerde normalize eder. trim + lower-case (Locale.ROOT),
     * "tr_TR"/"tr-TR" -> "tr", "en_US" -> "en". Desteklenmeyen / boş değer "en"e düşer; asla istisna atmaz.
     *
     * @return normalize edilmiş dil kodu; uyarı için bkz. {@link #isSupportedLanguage}
     */
    public static String normalizeLanguage(String raw) {
        String l = stripLanguage(raw);
        return SUPPORTED_LANGUAGES.contains(l) ? l : "en";
    }

    /** Ham değer (trim/case/bölge eki sonrası) desteklenen bir dile karşılık geliyor mu? */
    public static boolean isSupportedLanguage(String raw) {
        return SUPPORTED_LANGUAGES.contains(stripLanguage(raw));
    }

    /** trim + lower-case + bölge ekini at; null -> "". */
    private static String stripLanguage(String raw) {
        if (raw == null) {
            return "";
        }
        String l = raw.trim().toLowerCase(java.util.Locale.ROOT);
        int sep = l.indexOf('_');
        if (sep < 0) {
            sep = l.indexOf('-');
        }
        return sep > 0 ? l.substring(0, sep) : l;
    }

    public String getLanguage() {
        String raw = config.getString("general.language", "en");
        String normalized = normalizeLanguage(raw);
        if (!isSupportedLanguage(raw) && raw != null && !raw.isBlank()
                && !raw.equals(lastWarnedLanguage)) {
            lastWarnedLanguage = raw;
            plugin.getLogger().warning("Unsupported general.language '" + raw
                    + "' (supported: en, tr). Falling back to 'en'.");
        }
        return normalized;
    }

    public String getDateFormat() {
        return config.getString("general.date-format", "dd/MM/yyyy HH:mm:ss");
    }

    public boolean isUpdateCheckEnabled() {
        return config.getBoolean("general.check-updates", true);
    }

    // Database Settings
    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "reportsystem");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "password");
    }

    // MySQL connection pool (HikariCP) — sınırlar DatabaseConfig.withPool'da uygulanır
    public int getMySQLPoolMaximumSize() {
        return config.getInt("database.mysql.pool.maximum-pool-size", 10);
    }

    public int getMySQLPoolMinimumIdle() {
        return config.getInt("database.mysql.pool.minimum-idle", 2);
    }

    public long getMySQLPoolConnectionTimeout() {
        return config.getLong("database.mysql.pool.connection-timeout", 5000L);
    }

    public long getMySQLPoolIdleTimeout() {
        return config.getLong("database.mysql.pool.idle-timeout", 600000L);
    }

    public long getMySQLPoolMaxLifetime() {
        return config.getLong("database.mysql.pool.max-lifetime", 1800000L);
    }

    public String getMySQLSslMode() {
        return config.getString("database.mysql.ssl-mode", "DISABLED");
    }

    // BungeeCord Settings
    public boolean isBungeeCordEnabled() {
        return config.getBoolean("bungeecord.enabled", false);
    }

    public String getServerDisplayName() {
        return config.getString("bungeecord.display-name", "");
    }

    // Report Settings
    public int getReportCooldown() {
        return config.getInt("reports.cooldown", 60);
    }

    public int getGlobalRateLimit() {
        return config.getInt("reports.global-rate-limit", 5);
    }

    public int getMinReasonLength() {
        return config.getInt("reports.min-reason-length", 10);
    }

    public int getMaxReasonLength() {
        return config.getInt("reports.max-reason-length", 100);
    }

    public int getAutoCloseDays() {
        return config.getInt("reports.auto-close-days", 30);
    }

    public int getMaxReportsPerPlayer() {
        return config.getInt("reports.max-reports-per-player", 3);
    }

    public List<String> getReportCategories() {
        return config.getStringList("reports.categories");
    }

    public boolean isStaffNotificationEnabled() {
        return config.getBoolean("reports.notifications.notify-staff", true);
    }

    public boolean isNotificationSoundEnabled() {
        return config.getBoolean("reports.notifications.sound-enabled", true);
    }

    public String getNotificationSound() {
        return config.getString("reports.notifications.sound", "BLOCK_NOTE_BLOCK_PLING");
    }

    // Replay Settings
    public boolean isReplayEnabled() {
        return config.getBoolean("replay.enabled", true);
    }

    public boolean isAutoRecordEnabled() {
        return config.getBoolean("replay.auto-record", true);
    }

    public int getRecordingDuration() {
        return config.getInt("replay.recording-duration", 45);
    }

    public int getMaxRecordings() {
        return config.getInt("replay.max-recordings", 3);
    }

    public int getReplayAutoDeleteDays() {
        return config.getInt("replay.auto-delete-days", 7);
    }

    // Web Replay Settings
    public boolean isWebReplayEnabled() {
        return config.getBoolean("replay.web-replay.enabled", false);
    }

    public boolean isWebReplayAutoGenerate() {
        return config.getBoolean("replay.web-replay.auto-generate", false);
    }

    public String getWebReplayStoragePath() {
        String path = config.getString("replay.web-replay.storage-path", "");
        if (path.isEmpty() || path.equals("plugins/ReportSystem/web-replays")) {
            // Eklentinin kendi klasorunu kullan (plugins/ReportSystem-Spigot/web-replays)
            return new java.io.File(plugin.getDataFolder(), "web-replays").getAbsolutePath();
        }
        return path;
    }

    public String getWebReplayBaseUrl() {
        return config.getString("replay.web-replay.base-url", "https://your-cdn.example.com/replays");
    }

    public String getWebReplayViewerUrl() {
        return config.getString("replay.web-replay.viewer-url", "https://s.mcraft.fun");
    }

    public String getWebReplayApiUrl() {
        return config.getString("replay.web-replay.api-url", "");
    }

    public String getLicenseKey() {
        return config.getString("license.license-key", "");
    }

    // Punishment Settings
    public boolean isUseGlobalPunishments() {
        return config.getBoolean("punishments.use-global", true);
    }

    // Performance Settings
    public boolean isAsyncDatabase() {
        return config.getBoolean("performance.async-database", true);
    }

    public boolean isCacheEnabled() {
        return config.getBoolean("performance.cache.enabled", true);
    }

    public int getCacheExpiry() {
        return config.getInt("performance.cache.expiry", 10);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getGuiTitle(String guiName, String... replacements) {
        String title = config.getString("guis." + guiName + ".title", "&8GUI");
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                title = title.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    public int getGuiSize(String guiName) {
        return config.getInt("guis." + guiName + ".size", 54);
    }

    public FileConfiguration getGuiConfig(String guiName) {
        // H-49: dile göre guis/<dil>/<ad>.yml veya kök guis/<ad>.yml
        File guiFile = com.reportsystem.spigot.gui.GUIConfig.resolveFile(plugin, guiName);
        return YamlConfiguration.loadConfiguration(guiFile);
    }

    public String getItemName(String configPath, String itemKey, String... replacements) {
        String name = config.getString(configPath + "." + itemKey + ".name", "&7Item");
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                name = name.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', name);
    }

    public List<String> getItemLore(String configPath, String itemKey, String... replacements) {
        List<String> lore = config.getStringList(configPath + "." + itemKey + ".lore");
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    line = line.replace(replacements[i], replacements[i + 1]);
                }
            }
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return coloredLore;
    }

    // ============= Notification Toggles =============

    public boolean isTitleNotificationEnabled() {
        return config.getBoolean("reports.notifications.title-enabled", true);
    }

    public boolean isActionBarNotificationEnabled() {
        return config.getBoolean("reports.notifications.actionbar-enabled", false);
    }

    // ============= Reports =============

    public boolean isRequireOnline() {
        return config.getBoolean("reports.require-online", true);
    }

    // ============= Performance =============

    public int getCacheMaxSize() {
        return config.getInt("performance.cache.max-size", 100);
    }

    // ============= Replay Quality =============

    public int getReplayTPS() {
        return config.getInt("replay.quality.tps", 20);
    }

    public boolean isRecordNearbyPlayers() {
        return config.getBoolean("replay.quality.record-nearby-players", true);
    }

    public int getNearbyRadius() {
        return config.getInt("replay.quality.nearby-radius", 50);
    }

    public boolean isRecordInventory() {
        return config.getBoolean("replay.quality.record-inventory", true);
    }

    public boolean isRecordChat() {
        return config.getBoolean("replay.quality.record-chat", true);
    }

    public boolean isRecordBlocks() {
        return config.getBoolean("replay.quality.record-blocks", true);
    }

    // ============= Replay Max File Size =============

    public int getMaxFileSize() {
        return config.getInt("replay.max-file-size", 5);
    }

    // ============= Overwatch Settings =============

    public boolean isOverwatchEnabled() {
        return config.getBoolean("overwatch.enabled", true);
    }

    public boolean isAutoCompleteQueueEnabled() {
        return config.getBoolean("overwatch.auto-complete-queue", false);
    }

    public int getMinReviewers() {
        return config.getInt("overwatch.min-reviewers", 3);
    }

    public double getConsensusThreshold() {
        return config.getDouble("overwatch.consensus-threshold", 70.0);
    }

    public int getAutoBanDurationDays() {
        return config.getInt("overwatch.auto-ban-duration-days", 30);
    }

    public int getHologramUpdateInterval() {
        return config.getInt("overwatch.npc.hologram-update-interval", 30);
    }

    public boolean isAutoAddReports() {
        return config.getBoolean("overwatch.queue.auto-add-reports", false);
    }

    public int getDefaultPriority() {
        return config.getInt("overwatch.queue.default-priority", 0);
    }

    public int getMaxQueueSize() {
        return config.getInt("overwatch.queue.max-queue-size", 100);
    }

    public boolean isAllowSkip() {
        return config.getBoolean("overwatch.reviewer.allow-skip", true);
    }

    public int getReviewCooldown() {
        return config.getInt("overwatch.reviewer.review-cooldown", 10);
    }

    public int getGuiltyInnocentXP() {
        return config.getInt("overwatch.xp.guilty-innocent-xp", 15);
    }

    public int getSkipXP() {
        return config.getInt("overwatch.xp.skip-xp", 5);
    }

    public int getXPPerLevel() {
        return config.getInt("overwatch.xp.xp-per-level", 100);
    }

    public int getBronzeXP() {
        return config.getInt("overwatch.xp.bronze-xp", 0);
    }

    public int getSilverXP() {
        return config.getInt("overwatch.xp.silver-xp", 500);
    }

    public int getGoldXP() {
        return config.getInt("overwatch.xp.gold-xp", 1000);
    }

    public int getDiamondXP() {
        return config.getInt("overwatch.xp.diamond-xp", 2500);
    }

    // ============= Overwatch Rewards =============

    /**
     * Check if reward commands are enabled
     */
    public boolean isRewardCommandsEnabled() {
        return config.getBoolean("overwatch.rewards.enabled", false);
    }

    /**
     * Get commands to execute on review complete (any verdict)
     */
    public List<String> getReviewCompleteCommands() {
        return config.getStringList("overwatch.rewards.on-review-complete");
    }

    /**
     * Get commands to execute on GUILTY or INNOCENT verdict
     */
    public List<String> getGuiltyInnocentCommands() {
        return config.getStringList("overwatch.rewards.on-guilty-innocent");
    }

    /**
     * Get commands to execute on SKIP verdict
     */
    public List<String> getSkipCommands() {
        return config.getStringList("overwatch.rewards.on-skip");
    }

    /**
     * Get commands to execute on level up
     */
    public List<String> getLevelUpCommands() {
        return config.getStringList("overwatch.rewards.on-level-up");
    }

    /**
     * Get commands to execute on rank up (bronze, silver, gold, diamond)
     */
    public List<String> getRankUpCommands(String rank) {
        return config.getStringList("overwatch.rewards.on-rank-up." + rank.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Get the raw config object for accessing nested properties
     */
    public FileConfiguration getConfig() {
        return config;
    }

}