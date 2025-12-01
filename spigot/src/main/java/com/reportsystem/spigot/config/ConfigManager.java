package com.reportsystem.spigot.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import org.bukkit.ChatColor;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messagesConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadMessages();
    }

    private void loadMessages() {
        String language = config.getString("general.language", "en");
        File messagesFile = new File(plugin.getDataFolder(), "messages_" + language + ".yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages_" + language + ".yml", false);
        }

        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    // General Settings
    public boolean isDebugEnabled() {
        return config.getBoolean("general.debug", false);
    }

    public String getLanguage() {
        return config.getString("general.language", "en");
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
        return config.getInt("replay.max-recordings", 10);
    }

    public int getReplayAutoDeleteDays() {
        return config.getInt("replay.auto-delete-days", 7);
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

    // Messages
    public String getPrefix() {
        return messagesConfig.getString("prefix.general", "&8[&6Report&8]");
    }

    public String getMessage(String path) {
        return messagesConfig.getString(path, "Message not found: " + path);
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadMessages();
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
        File guiFile = new File(plugin.getDataFolder(), "guis/" + guiName + ".yml");
        if (!guiFile.exists()) {
            plugin.saveResource("guis/" + guiName + ".yml", false);
        }
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

    public boolean isStaffNotifyEnabled() {
        return config.getBoolean("reports.notifications.notify-staff", true);
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
        return config.getStringList("overwatch.rewards.on-rank-up." + rank.toLowerCase());
    }

    /**
     * Get the raw config object for accessing nested properties
     */
    public FileConfiguration getConfig() {
        return config;
    }

}