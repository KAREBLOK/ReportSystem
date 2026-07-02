package com.reportsystem.velocity.listeners;

import com.reportsystem.velocity.ReportSystemVelocity;
import com.reportsystem.velocity.config.VelocityConfig;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public class ReportNotificationListener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ReportSystemVelocity plugin;

    public ReportNotificationListener(ReportSystemVelocity plugin) {
        this.plugin = plugin;
    }

    private VelocityConfig config() {
        return plugin.getConfig();
    }

    /**
     * Tum Spigot sunucularina rapor bildirimi gonder
     */
    public void broadcastReportNotification(int reportId, String reporterName, String reportedName,
                                           String reason, String serverName) {
        // Tum sunuculara plugin message gonder
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            try {
                ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(msgBytes);
                out.writeUTF("NewReport");
                out.writeInt(reportId);
                out.writeUTF(reporterName);
                out.writeUTF(reportedName);
                out.writeUTF(reason);
                out.writeUTF(serverName);

                server.sendPluginMessage(ReportSystemVelocity.NOTIFICATION_CHANNEL, msgBytes.toByteArray());

                plugin.getLogger().info("[NOTIFICATION] Sent report #{} notification to server: {}",
                        reportId, server.getServerInfo().getName());
            } catch (Exception e) {
                plugin.getLogger().warn("[NOTIFICATION] Failed to send notification to server: {} - {}",
                        server.getServerInfo().getName(), e.getMessage());
            }
        }

        // Velocity'deki yetkililere de bildirim gonder
        String notifyFormat = config().getMessage("report-notify.format",
                "&8[&6Rapor&8] &7(%server%) &f%reporter% &7\u279C &c%reported%\n&7Sebep: &f%reason% &8(#%id%)",
                "%server%", serverName, "%reporter%", reporterName, "%reported%", reportedName,
                "%reason%", reason, "%id%", String.valueOf(reportId));

        Component message = LEGACY.deserialize(notifyFormat);

        for (Player player : plugin.getServer().getAllPlayers()) {
            if (player.hasPermission("reportsystem.notify")) {
                player.sendMessage(message);
            }
        }
    }
}
