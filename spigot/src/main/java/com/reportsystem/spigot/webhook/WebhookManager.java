package com.reportsystem.spigot.webhook;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.Color;
import java.time.Instant;

/**
 * Manager for Discord webhook notifications
 * Supports multi-language messages based on config language setting
 */
public class WebhookManager {

    private final ReportSystemSpigot plugin;
    private final String webhookUrl;
    private final boolean enabled;
    private final String language;
    private final boolean sendNewReport;
    private final boolean sendReportClosed;
    private final boolean sendPunishment;
    private final boolean includeServerName;
    private final boolean addButtons;
    private final String webPanelUrl;

    // Color scheme
    private static final Color COLOR_NEW_REPORT = new Color(255, 85, 85);      // Red
    private static final Color COLOR_ACCEPTED = new Color(85, 255, 85);        // Green
    private static final Color COLOR_REJECTED = new Color(255, 170, 85);       // Orange
    private static final Color COLOR_CLOSED = new Color(85, 170, 255);         // Blue
    private static final Color COLOR_PUNISHMENT_BAN = new Color(139, 0, 0);    // Dark Red
    private static final Color COLOR_PUNISHMENT_KICK = new Color(255, 165, 0); // Orange
    private static final Color COLOR_PUNISHMENT_MUTE = new Color(255, 215, 0); // Gold

    public WebhookManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.enabled = config.getBoolean("discord-webhook.enabled", false);
        this.webhookUrl = config.getString("discord-webhook.url", "");
        this.language = config.getString("language", "en").toLowerCase();
        this.sendNewReport = config.getBoolean("discord-webhook.events.new-report", true);
        this.sendReportClosed = config.getBoolean("discord-webhook.events.report-closed", true);
        this.sendPunishment = config.getBoolean("discord-webhook.events.punishment", true);
        this.includeServerName = config.getBoolean("discord-webhook.include-server-name", true);
        this.addButtons = config.getBoolean("discord-webhook.add-buttons", true);
        this.webPanelUrl = config.getString("discord-webhook.web-panel-url", "");

        if (enabled && (webhookUrl == null || webhookUrl.isEmpty())) {
            plugin.getLogger().warning("[Webhook] Discord webhook is enabled but URL is not set!");
        }
    }

    /**
     * Send new report notification
     */
    public void sendNewReportNotification(Report report) {
        if (!enabled || !sendNewReport || webhookUrl.isEmpty()) {
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(plugin, webhookUrl)
                .setUsername(getMessage("webhook.username"))
                .setAvatarUrl(plugin.getConfig().getString("discord-webhook.avatar-url",
                        "https://i.imgur.com/4M34hi2.png"));

        // Create embed
        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(getMessage("webhook.new-report.title"))
                .setDescription(getMessage("webhook.new-report.description"))
                .setColor(COLOR_NEW_REPORT)
                .addField(
                        getMessage("webhook.field.report-id"),
                        "#" + report.getId(),
                        true
                )
                .addField(
                        getMessage("webhook.field.reporter"),
                        report.getReporterName() != null ? report.getReporterName() :
                        (report.getReporter() != null ? report.getReporter() : "Unknown"),
                        true
                )
                .addField(
                        getMessage("webhook.field.reported-player"),
                        report.getReportedPlayerName() != null ? report.getReportedPlayerName() :
                        (report.getReportedPlayer() != null ? report.getReportedPlayer() : "Unknown"),
                        true
                )
                .addField(
                        getMessage("webhook.field.reason"),
                        report.getReason(),
                        false
                )
                .addField(
                        getMessage("webhook.field.status"),
                        getStatusEmoji(report.getStatus()) + " " + getStatusName(report.getStatus()),
                        true
                );

        // Add total reports count for this player
        String reportedPlayer = report.getReportedPlayerName() != null ? report.getReportedPlayerName() : report.getReportedPlayer();
        if (reportedPlayer != null) {
            int totalReports = plugin.getReportService().getReportCount(reportedPlayer);
            embed.addField(
                    getMessage("webhook.field.total-reports"),
                    String.valueOf(totalReports),
                    true
            );
        }

        // Add server name if enabled
        if (includeServerName && report.getServerName() != null) {
            embed.addField(
                    getMessage("webhook.field.server"),
                    report.getServerName(),
                    true
            );
        }

        // Add Overwatch votes if available
        if (plugin.getOverwatchManager() != null) {
            String votesText = formatOverwatchVotes(report.getId());
            if (votesText != null && !votesText.isEmpty()) {
                embed.addField(
                        getMessage("webhook.field.overwatch-votes"),
                        votesText,
                        false
                );
            }
        }

        // Add timestamp
        embed.setTimestamp(Instant.now());

        // Add footer
        embed.setFooter(getMessage("webhook.footer"), null);

        // Add player head thumbnail
        String playerName = report.getReportedPlayerName() != null ? report.getReportedPlayerName() : report.getReportedPlayer();
        if (playerName != null) {
            String playerHead = "https://mc-heads.net/avatar/" + playerName + "/64";
            embed.setThumbnail(playerHead);
        }

        webhook.addEmbed(embed);

        // Add buttons if enabled
        if (addButtons) {
            DiscordWebhook.ActionRow actionRow = new DiscordWebhook.ActionRow();

            // View report button (if web panel URL is set)
            if (!webPanelUrl.isEmpty()) {
                String viewUrl = webPanelUrl + "/report/" + report.getId();
                actionRow.addButton(new DiscordWebhook.Button(
                        DiscordWebhook.ButtonStyle.LINK,
                        getMessage("webhook.button.view-report"),
                        viewUrl,
                        "📋"
                ));
            }

            // Documentation button
            actionRow.addButton(new DiscordWebhook.Button(
                    DiscordWebhook.ButtonStyle.LINK,
                    getMessage("webhook.button.help"),
                    "https://github.com/your-repo/wiki",
                    "📚"
            ));

            // Always add action row if it has buttons
            webhook.addActionRow(actionRow);
        }

        webhook.execute();
    }

    /**
     * Send report closed notification
     */
    public void sendReportClosedNotification(Report report, String closedBy, String response) {
        if (!enabled || !sendReportClosed || webhookUrl.isEmpty()) {
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(plugin, webhookUrl)
                .setUsername(getMessage("webhook.username"))
                .setAvatarUrl(plugin.getConfig().getString("discord-webhook.avatar-url",
                        "https://i.imgur.com/4M34hi2.png"));

        // Determine color based on status
        Color color = COLOR_CLOSED;
        if (report.getStatus().equalsIgnoreCase("ACCEPTED")) {
            color = COLOR_ACCEPTED;
        } else if (report.getStatus().equalsIgnoreCase("REJECTED")) {
            color = COLOR_REJECTED;
        }

        // Create embed
        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(getMessage("webhook.report-closed.title"))
                .setDescription(getMessage("webhook.report-closed.description"))
                .setColor(color)
                .addField(
                        getMessage("webhook.field.report-id"),
                        "#" + report.getId(),
                        true
                )
                .addField(
                        getMessage("webhook.field.reported-player"),
                        report.getReportedPlayerName() != null ? report.getReportedPlayerName() :
                        (report.getReportedPlayer() != null ? report.getReportedPlayer() : "Unknown"),
                        true
                )
                .addField(
                        getMessage("webhook.field.closed-by"),
                        closedBy,
                        true
                )
                .addField(
                        getMessage("webhook.field.status"),
                        getStatusEmoji(report.getStatus()) + " " + getStatusName(report.getStatus()),
                        true
                );

        // Add response if available
        if (response != null && !response.isEmpty()) {
            embed.addField(
                    getMessage("webhook.field.response"),
                    response,
                    false
            );
        }

        // Add server name if enabled
        if (includeServerName && report.getServerName() != null) {
            embed.addField(
                    getMessage("webhook.field.server"),
                    report.getServerName(),
                    true
            );
        }

        // Add timestamp
        embed.setTimestamp(Instant.now());

        // Add footer
        embed.setFooter(getMessage("webhook.footer"), null);

        // Add player head thumbnail
        String playerName = report.getReportedPlayerName() != null ? report.getReportedPlayerName() : report.getReportedPlayer();
        if (playerName != null) {
            String playerHead = "https://mc-heads.net/avatar/" + playerName + "/64";
            embed.setThumbnail(playerHead);
        }

        webhook.addEmbed(embed);

        // Add buttons if enabled
        if (addButtons) {
            DiscordWebhook.ActionRow actionRow = new DiscordWebhook.ActionRow();

            // View details button (if web panel URL is set)
            if (!webPanelUrl.isEmpty()) {
                String viewUrl = webPanelUrl + "/report/" + report.getId();
                actionRow.addButton(new DiscordWebhook.Button(
                        DiscordWebhook.ButtonStyle.LINK,
                        getMessage("webhook.button.view-details"),
                        viewUrl,
                        "🔍"
                ));
            }

            // Help button
            actionRow.addButton(new DiscordWebhook.Button(
                    DiscordWebhook.ButtonStyle.LINK,
                    getMessage("webhook.button.help"),
                    "https://github.com/your-repo/wiki",
                    "📚"
            ));

            webhook.addActionRow(actionRow);
        }

        webhook.execute();
    }

    /**
     * Send report closed WITH punishment notification (combined message)
     */
    public void sendReportClosedWithPunishmentNotification(Report report, String closedBy,
                                                           String punishmentType, String reason, String duration) {
        if (!enabled || !sendReportClosed || webhookUrl.isEmpty()) {
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(plugin, webhookUrl)
                .setUsername(getMessage("webhook.username"))
                .setAvatarUrl(plugin.getConfig().getString("discord-webhook.avatar-url",
                        "https://i.imgur.com/4M34hi2.png"));

        // Determine color and emoji based on punishment type
        Color color = COLOR_PUNISHMENT_BAN;
        String emoji = "🔨";
        if (punishmentType.equalsIgnoreCase("kick")) {
            color = COLOR_PUNISHMENT_KICK;
            emoji = "👢";
        } else if (punishmentType.equalsIgnoreCase("mute")) {
            color = COLOR_PUNISHMENT_MUTE;
            emoji = "🔇";
        }

        String playerName = report.getReportedPlayerName() != null ? report.getReportedPlayerName() : report.getReportedPlayer();

        // Create embed
        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(emoji + " " + getMessage("webhook.report-punished.title"))
                .setDescription(getMessage("webhook.report-punished.description"))
                .setColor(color)
                .addField(
                        getMessage("webhook.field.report-id"),
                        "#" + report.getId(),
                        true
                )
                .addField(
                        getMessage("webhook.field.reported-player"),
                        playerName != null ? playerName : "Unknown",
                        true
                )
                .addField(
                        getMessage("webhook.field.closed-by"),
                        closedBy,
                        true
                )
                .addField(
                        getMessage("webhook.field.punishment-type"),
                        getPunishmentTypeName(punishmentType),
                        true
                )
                .addField(
                        getMessage("webhook.field.reason"),
                        reason,
                        false
                );

        // Add duration if available
        if (duration != null && !duration.isEmpty() && !duration.equalsIgnoreCase("permanent")) {
            embed.addField(
                    getMessage("webhook.field.duration"),
                    duration,
                    true
            );
        } else if (punishmentType.equalsIgnoreCase("ban")) {
            embed.addField(
                    getMessage("webhook.field.duration"),
                    getMessage("webhook.permanent"),
                    true
            );
        }

        // Add server name if enabled
        if (includeServerName && report.getServerName() != null) {
            embed.addField(
                    getMessage("webhook.field.server"),
                    report.getServerName(),
                    true
            );
        }

        // Add timestamp
        embed.setTimestamp(Instant.now());

        // Add footer
        embed.setFooter(getMessage("webhook.footer"), null);

        // Add player head thumbnail
        if (playerName != null) {
            String playerHead = "https://mc-heads.net/avatar/" + playerName + "/64";
            embed.setThumbnail(playerHead);
        }

        webhook.addEmbed(embed);

        // Add buttons if enabled
        if (addButtons) {
            DiscordWebhook.ActionRow actionRow = new DiscordWebhook.ActionRow();

            // View details button (if web panel URL is set)
            if (!webPanelUrl.isEmpty()) {
                String viewUrl = webPanelUrl + "/report/" + report.getId();
                actionRow.addButton(new DiscordWebhook.Button(
                        DiscordWebhook.ButtonStyle.LINK,
                        getMessage("webhook.button.view-details"),
                        viewUrl,
                        "🔍"
                ));
            }

            // Help button
            actionRow.addButton(new DiscordWebhook.Button(
                    DiscordWebhook.ButtonStyle.LINK,
                    getMessage("webhook.button.help"),
                    "https://github.com/your-repo/wiki",
                    "📚"
            ));

            webhook.addActionRow(actionRow);
        }

        webhook.execute();
    }

    /**
     * Send punishment notification
     */
    public void sendPunishmentNotification(String playerName, String punishmentType, String reason,
                                          String punishedBy, String duration) {
        if (!enabled || !sendPunishment || webhookUrl.isEmpty()) {
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(plugin, webhookUrl)
                .setUsername(getMessage("webhook.username"))
                .setAvatarUrl(plugin.getConfig().getString("discord-webhook.avatar-url",
                        "https://i.imgur.com/4M34hi2.png"));

        // Determine color based on punishment type
        Color color = COLOR_PUNISHMENT_BAN;
        String emoji = "🔨";
        if (punishmentType.equalsIgnoreCase("kick")) {
            color = COLOR_PUNISHMENT_KICK;
            emoji = "👢";
        } else if (punishmentType.equalsIgnoreCase("mute")) {
            color = COLOR_PUNISHMENT_MUTE;
            emoji = "🔇";
        } else if (punishmentType.equalsIgnoreCase("ban")) {
            emoji = "🔨";
        }

        // Create embed
        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(emoji + " " + getMessage("webhook.punishment.title"))
                .setDescription(getMessage("webhook.punishment.description"))
                .setColor(color)
                .addField(
                        getMessage("webhook.field.player"),
                        playerName,
                        true
                )
                .addField(
                        getMessage("webhook.field.punishment-type"),
                        getPunishmentTypeName(punishmentType),
                        true
                )
                .addField(
                        getMessage("webhook.field.punished-by"),
                        punishedBy,
                        true
                )
                .addField(
                        getMessage("webhook.field.reason"),
                        reason,
                        false
                );

        // Add duration if available
        if (duration != null && !duration.isEmpty() && !duration.equalsIgnoreCase("permanent")) {
            embed.addField(
                    getMessage("webhook.field.duration"),
                    duration,
                    true
            );
        } else if (punishmentType.equalsIgnoreCase("ban")) {
            embed.addField(
                    getMessage("webhook.field.duration"),
                    getMessage("webhook.permanent"),
                    true
            );
        }

        // Add timestamp
        embed.setTimestamp(Instant.now());

        // Add footer
        embed.setFooter(getMessage("webhook.footer"), null);

        // Add player head thumbnail
        String playerHead = "https://mc-heads.net/avatar/" + playerName + "/64";
        embed.setThumbnail(playerHead);

        webhook.addEmbed(embed);

        webhook.execute();
    }

    /**
     * Send custom notification
     */
    public void sendCustomNotification(String title, String description, Color color) {
        if (!enabled || webhookUrl.isEmpty()) {
            return;
        }

        DiscordWebhook webhook = new DiscordWebhook(plugin, webhookUrl)
                .setUsername(getMessage("webhook.username"))
                .setAvatarUrl(plugin.getConfig().getString("discord-webhook.avatar-url",
                        "https://i.imgur.com/4M34hi2.png"));

        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now())
                .setFooter(getMessage("webhook.footer"), null);

        webhook.addEmbed(embed);
        webhook.execute();
    }

    /**
     * Get message from language file
     */
    private String getMessage(String key) {
        String message = plugin.getMessageManager().getMessage(key);
        if (message == null || message.isEmpty() || message.startsWith("Missing message:")) {
            // Fallback to key if message not found
            return key;
        }
        // Remove Minecraft color codes (&a, &c, etc.)
        message = message.replaceAll("&[0-9a-fk-or]", "");
        return message;
    }

    /**
     * Get emoji for status
     */
    private String getStatusEmoji(String status) {
        switch (status.toUpperCase()) {
            case "PENDING":
                return "⏳";
            case "ACCEPTED":
                return "✅";
            case "REJECTED":
                return "❌";
            case "IN_PROGRESS":
                return "🔄";
            case "CLOSED":
                return "🔒";
            default:
                return "📋";
        }
    }

    /**
     * Get translated status name based on language
     */
    private String getStatusName(String status) {
        String key = "reports.status.name." + status.toLowerCase();
        String translated = plugin.getMessageManager().getMessage(key);

        // If translation not found, return the original status
        if (translated == null || translated.isEmpty() || translated.startsWith("Missing message:")) {
            return status;
        }

        // Remove Minecraft color codes
        translated = translated.replaceAll("&[0-9a-fk-or]", "");
        return translated;
    }

    /**
     * Get translated punishment type name based on language
     */
    private String getPunishmentTypeName(String punishmentType) {
        String key = "webhook.punishment-type." + punishmentType.toLowerCase();
        String translated = plugin.getMessageManager().getMessage(key);

        // If translation not found, return the original type in uppercase
        if (translated == null || translated.isEmpty() || translated.startsWith("Missing message:")) {
            return punishmentType.toUpperCase();
        }

        // Remove Minecraft color codes
        translated = translated.replaceAll("&[0-9a-fk-or]", "");
        return translated;
    }

    /**
     * Format Overwatch votes for display
     * Example: "7 suçlu, 3 masum, 2 atlandı"
     */
    private String formatOverwatchVotes(int reportId) {
        try {
            // Get voting stats from Overwatch manager (already formatted with translations)
            if (plugin.getOverwatchManager() != null) {
                return plugin.getOverwatchManager().getVotingStats(reportId);
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[Webhook] Error formatting Overwatch votes: " + e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled && !webhookUrl.isEmpty();
    }

    public String getLanguage() {
        return language;
    }
}
