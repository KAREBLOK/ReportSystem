package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * BungeeCord'dan gelen rapor bildirimlerini dinler
 */
public class ReportNotificationPluginMessageListener implements PluginMessageListener {

    private final ReportSystemSpigot plugin;

    public ReportNotificationPluginMessageListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("reportsystem:notification")) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = in.readUTF();

            if (subChannel.equals("NewReport")) {
                int reportId = in.readInt();
                String reporterName = in.readUTF();
                String reportedName = in.readUTF();
                String reason = in.readUTF();
                String serverName = in.readUTF();

                // Yetkililere bildirim gönder
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageManager().sendStaffNotification(reportId, reporterName, reportedName, reason, serverName);
                });
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Plugin mesajı okunamadı: " + e.getMessage());
        }
    }
}
