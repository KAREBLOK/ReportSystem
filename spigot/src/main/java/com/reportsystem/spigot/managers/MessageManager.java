package com.reportsystem.spigot.managers;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.config.ConfigManager;
import com.reportsystem.spigot.utils.AdvancementNotification;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        // JAR'daki varsayılan değerleri yükle - kullanıcı dosyasında eksik key varsa fallback olarak kullanılır
        InputStream defaultStream = plugin.getResource("messages_" + language + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            messagesConfig.setDefaults(defaults);
        }

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
            return messageCache.get(path);
        }

        String message = messagesConfig.getString(path, "Missing message: " + path);

        // Replace all prefix placeholders
        message = replacePrefixes(message);

        messageCache.put(path, message);
        return message;
    }

    /**
     * Tüm prefix placeholder'larını gerçek değerleriyle değiştirir
     */
    private String replacePrefixes(String message) {
        return message
                .replace("%prefix_general%", messagesConfig.getString("prefix.general", "&8[&6Report&8]"))
                .replace("%prefix_success%", messagesConfig.getString("prefix.success", "&8[&a✓&8]"))
                .replace("%prefix_error%", messagesConfig.getString("prefix.error", "&8[&c✗&8]"))
                .replace("%prefix_info%", messagesConfig.getString("prefix.info", "&8[&b!&8]"))
                .replace("%prefix_warning%", messagesConfig.getString("prefix.warning", "&8[&e⚠&8]"))
                .replace("%overwatch_prefix%", messagesConfig.getString("overwatch.prefix", "&8[&6⚖&8]"));
    }

    public void sendMessage(CommandSender sender, String messagePath, String... replacements) {
        String message = getMessage(messagePath);

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
        sendToastToPlayer(staff, "new-report", reporter, reported, reason, reportId, configManager.getServerDisplayName());

        // Title bildirimi
        if (plugin.getConfig().getBoolean("reports.notifications.title-enabled", true)) {
            String title = colorize(getMessage("reports.notify.title"));
            String subtitle = colorize(getMessage("reports.notify.subtitle")
                    .replace("%reporter%", reporter)
                    .replace("%reported%", reported));
            staff.sendTitle(title, subtitle, 10, 70, 20);
        }

        // ActionBar bildirimi
        if (plugin.getConfig().getBoolean("reports.notifications.actionbar-enabled", false)) {
            String actionbar = colorize(getMessage("reports.notify.actionbar")
                    .replace("%reported%", reported)
                    .replace("%id%", String.valueOf(reportId)));
            staff.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacy(actionbar));
        }

        // Ses efekti (config'den)
        playNotificationSound(staff);
    }

    /**
     * Tüm yetkililere rapor bildirimi gönder
     * BungeeCord'dan gelen bildirimler için kullanılır
     */
    public void sendStaffNotification(int reportId, String reporter, String reported, String reason, String serverName) {
        boolean titleEnabled = plugin.getConfig().getBoolean("reports.notifications.title-enabled", true);
        boolean actionbarEnabled = plugin.getConfig().getBoolean("reports.notifications.actionbar-enabled", false);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("reportsystem.staff")) {
                sendToastToPlayer(onlinePlayer, "new-report", reporter, reported, reason, reportId, serverName);

                // Title bildirimi
                if (titleEnabled) {
                    String title = colorize(getMessage("reports.notify.title"));
                    String subtitle = colorize(getMessage("reports.notify.subtitle")
                            .replace("%reporter%", reporter)
                            .replace("%reported%", reported));
                    onlinePlayer.sendTitle(title, subtitle, 10, 70, 20);
                }

                // ActionBar bildirimi
                if (actionbarEnabled) {
                    String actionbar = colorize(getMessage("reports.notify.actionbar")
                            .replace("%reported%", reported)
                            .replace("%id%", String.valueOf(reportId)));
                    onlinePlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacy(actionbar));
                }

                playNotificationSound(onlinePlayer);
            }
        }
    }

    /**
     * Rapor durum güncellemesi toast bildirimi gönder
     */
    public void sendStatusUpdateToast(int reportId, String newStatus) {
        AdvancementNotification notif = plugin.getAdvancementNotification();
        if (notif == null) return;

        String translatedStatus = getStatusName(newStatus);
        String title = getMessage("reports.notify.toast.status-update.title")
                .replace("%id%", String.valueOf(reportId))
                .replace("%status%", translatedStatus);

        Material icon = getToastIcon("status-update");
        AdvancementNotification.FrameType frame = getToastFrame("status-update");

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("reportsystem.notify")) {
                notif.sendToast(onlinePlayer, icon, colorize(title), "", frame);
                playNotificationSound(onlinePlayer);
            }
        }
    }

    /**
     * Tek bir oyuncuya toast bildirimi gönder (messages dosyasından ayarları okur)
     */
    private void sendToastToPlayer(Player player, String toastType, String reporter, String reported, String reason, int reportId, String serverName) {
        AdvancementNotification notif = plugin.getAdvancementNotification();
        if (notif == null) return;

        String title = getMessage("reports.notify.toast." + toastType + ".title")
                .replace("%reporter%", reporter)
                .replace("%reported%", reported)
                .replace("%reason%", reason)
                .replace("%id%", String.valueOf(reportId))
                .replace("%server%", serverName);

        Material icon = getToastIcon(toastType);
        AdvancementNotification.FrameType frame = getToastFrame(toastType);

        notif.sendToast(player, icon, colorize(title), "", frame);
    }

    /**
     * Messages dosyasından toast ikonunu oku
     */
    private Material getToastIcon(String toastType) {
        String iconName = messagesConfig.getString("reports.notify.toast." + toastType + ".icon", "PAPER");
        try {
            return Material.valueOf(iconName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    /**
     * Messages dosyasından toast frame türünü oku
     */
    private AdvancementNotification.FrameType getToastFrame(String toastType) {
        String frameName = messagesConfig.getString("reports.notify.toast." + toastType + ".frame", "CHALLENGE");
        try {
            return AdvancementNotification.FrameType.valueOf(frameName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AdvancementNotification.FrameType.CHALLENGE;
        }
    }

    /**
     * Config'den bildirim sesini çal
     */
    private void playNotificationSound(Player player) {
        if (!configManager.isNotificationSoundEnabled()) return;

        try {
            String soundName = plugin.getConfig().getString("reports.notifications.sound", "BLOCK_NOTE_BLOCK_PLING");
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    /**
     * Raporcu'ya raporunun kabul edildiğini bildirir (Hypixel tarzı)
     * Sadece ACCEPTED raporlar için çağrılır - REJECTED için bildirim gönderilmez
     */
    public void sendReporterFeedback(Player reporter, int reportId, String reportedPlayer) {
        if (!plugin.getConfig().getBoolean("reports.reporter-feedback.enabled", true)) return;

        sendMessage(reporter, "reports.reporter-feedback.accepted",
                "%id%", String.valueOf(reportId),
                "%player%", reportedPlayer);

        // Ses efekti
        if (plugin.getConfig().getBoolean("reports.reporter-feedback.sound-enabled", true)) {
            try {
                String soundName = plugin.getConfig().getString("reports.reporter-feedback.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                reporter.playSound(reporter.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                reporter.playSound(reporter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
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
            return "Unknown";
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