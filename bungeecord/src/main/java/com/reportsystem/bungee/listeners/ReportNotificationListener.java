package com.reportsystem.bungee.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.bungee.ReportSystemBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;

/**
 * Yeni rapor oluşturulduğunda tüm Spigot sunucularına bildirim gönderir
 * BungeeCord üzerinden cross-server bildirim sistemi
 */
public class ReportNotificationListener implements Listener {

    private final ReportSystemBungee plugin;

    public ReportNotificationListener(ReportSystemBungee plugin) {
        this.plugin = plugin;
    }

    /**
     * Tüm Spigot sunucularına rapor bildirimi gönder
     * Her sunucudaki yetkili oyunculara toast/chat mesajı göstermek için kullanılır
     */
    public void broadcastReportNotification(int reportId, String reporterName, String reportedName,
                                           String reason, String serverName) {
        // Tüm sunuculara plugin message gönder
        for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
            try {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("NewReport");
                out.writeInt(reportId);
                out.writeUTF(reporterName);
                out.writeUTF(reportedName);
                out.writeUTF(reason);
                out.writeUTF(serverName);

                server.sendData("reportsystem:notification", out.toByteArray());

                plugin.getLogger().info("[NOTIFICATION] Sent report #" + reportId +
                        " notification to server: " + server.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[NOTIFICATION] Failed to send notification to server: " +
                        server.getName() + " - " + e.getMessage());
            }
        }

        // BungeeCord'daki yetkililere de bildirim gönder
        String message = ChatColor.translateAlternateColorCodes('&',
                "&8[&6Rapor&8] &7(&e" + serverName + "&7) &f" + reporterName +
                        " &7➜ &c" + reportedName +
                        "\n&7Sebep: &f" + reason + " &8(#" + reportId + ")");

        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            if (player.hasPermission("reportsystem.notify")) {
                player.sendMessage(message);
            }
        }
    }
}
