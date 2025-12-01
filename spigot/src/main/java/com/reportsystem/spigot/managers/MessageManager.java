package com.reportsystem.spigot.managers;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final ReportSystemSpigot plugin;
    private final ConfigManager configManager;
    private FileConfiguration messagesConfig;
    private final Map<String, String> messageCache = new HashMap<>();

    public MessageManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        loadMessages();
    }

    private void loadMessages() {
        String language = configManager.getLanguage();
        File messagesFile = new File(plugin.getDataFolder(), "messages_" + language + ".yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages_" + language + ".yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Cache prefixes
        messageCache.put("prefix.general", messagesConfig.getString("prefix.general", "&8[&6Report&8]"));
        messageCache.put("prefix.success", messagesConfig.getString("prefix.success", "&8[&a✓&8]"));
        messageCache.put("prefix.error", messagesConfig.getString("prefix.error", "&8[&c✗&8]"));
        messageCache.put("prefix.info", messagesConfig.getString("prefix.info", "&8[&b!&8]"));
        messageCache.put("prefix.warning", messagesConfig.getString("prefix.warning", "&8[&e⚠&8]"));
        messageCache.put("overwatch.prefix", messagesConfig.getString("overwatch.prefix", "&8[&6⚖&8]"));
    }

    public String getMessage(String path) {
        if (messageCache.containsKey(path)) {
            String message = messageCache.get(path);
            // Replace overwatch prefix
            return message.replace("%overwatch_prefix%", messageCache.getOrDefault("overwatch.prefix", "&8[&6⚖&8]"));
        }

        String message = messagesConfig.getString(path, "Missing message: " + path);

        // Replace overwatch prefix
        message = message.replace("%overwatch_prefix%", messageCache.getOrDefault("overwatch.prefix", "&8[&6⚖&8]"));

        messageCache.put(path, message);
        return message;
    }

    public void sendMessage(CommandSender sender, String messagePath, String... replacements) {
        String message = getMessage(messagePath);

        // Replace prefixes
        message = message.replace("%prefix_general%", getMessage("prefix.general"))
                .replace("%prefix_success%", getMessage("prefix.success"))
                .replace("%prefix_error%", getMessage("prefix.error"))
                .replace("%prefix_info%", getMessage("prefix.info"))
                .replace("%prefix_warning%", getMessage("prefix.warning"))
                .replace("%overwatch_prefix%", getMessage("overwatch.prefix"));

        // Apply replacements
        if (replacements.length > 0 && replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }

        sender.sendMessage(colorize(message));
    }

    public void sendRawMessage(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    public void sendNoPermission(CommandSender sender) {
        sendMessage(sender, "general.no-permission");
    }

    public void sendRateLimitError(Player player, int limit) {
        sendMessage(player, "general.rate-limit",
                "%limit%", String.valueOf(limit));
    }

    public void sendReportSuccess(Player player, String reportedPlayer, int reportId) {
        sendMessage(player, "reports.success",
                "%player%", reportedPlayer,
                "%id%", String.valueOf(reportId));
    }

    public void sendReportLimitReached(Player player, String reportedPlayer, int limit) {
        sendMessage(player, "reports.limit-reached",
                "%player%", reportedPlayer,
                "%limit%", String.valueOf(limit));
    }

    public void sendCannotReportImmune(Player player) {
        sendMessage(player, "reports.cannot-report-immune");
    }

    public void sendReportNotification(Player staff, String reporter, String reported, String reason, int reportId) {
        String message = getMessage("reports.notify.staff")
                .replace("%reporter%", reporter)
                .replace("%reported%", reported)
                .replace("%reason%", reason)
                .replace("%id%", String.valueOf(reportId))
                .replace("%server%", configManager.getServerDisplayName());

        // Replace prefixes
        message = message.replace("%prefix_general%", getMessage("prefix.general"))
                .replace("%overwatch_prefix%", getMessage("overwatch.prefix"));

        staff.sendMessage(colorize(message));
    }

    /**
     * Tüm yetkililere rapor bildirimi gönder
     * BungeeCord'dan gelen bildirimler için kullanılır
     */
    public void sendStaffNotification(int reportId, String reporter, String reported, String reason, String serverName) {
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("reportsystem.staff")) {
                String message = getMessage("reports.notify.staff")
                        .replace("%reporter%", reporter)
                        .replace("%reported%", reported)
                        .replace("%reason%", reason)
                        .replace("%id%", String.valueOf(reportId))
                        .replace("%server%", serverName);

                // Replace prefixes
                message = message.replace("%prefix_general%", getMessage("prefix.general"))
                        .replace("%overwatch_prefix%", getMessage("overwatch.prefix"));

                onlinePlayer.sendMessage(colorize(message));
            }
        }
    }

    public void reload() {
        messageCache.clear();
        loadMessages();
    }

    public void sendRecordingStarted(Player player) {
        sendMessage(player, "replay.recording.started");
    }

    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Rapor durumunun dil dosyasından çevirisini al
     */
    public String getStatusName(String status) {
        if (status == null) {
            return status;
        }

        String path = "reports.status.name." + status.toLowerCase();
        String translatedName = messagesConfig.getString(path);

        // Eğer çeviri bulunamazsa, orijinal status'ü döndür
        return translatedName != null ? translatedName : status;
    }

    /**
     * Mesaj dosyasından liste al
     */
    public java.util.List<String> getMessageList(String path) {
        return messagesConfig.getStringList(path);
    }
}