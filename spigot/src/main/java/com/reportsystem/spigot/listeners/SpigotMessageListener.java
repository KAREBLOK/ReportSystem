package com.reportsystem.spigot.listeners;

import com.reportsystem.common.messaging.PluginMessageHandler;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;

public class SpigotMessageListener implements PluginMessageListener, PluginMessageHandler.MessageHandler {

    private final ReportSystemSpigot plugin;

    public SpigotMessageListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PluginMessageHandler.CHANNEL)) {
            return;
        }

        try {
            PluginMessageHandler.handleMessage(message, this);
        } catch (IOException e) {
            plugin.getLogger().severe("Mesaj işlenirken hata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onReportCreated(int reportId, String reporter, String reported, String reason, String server) {
        // Yetkililere toast bildirim (MessageManager üzerinden, messages dosyasından okunur)
        plugin.getMessageManager().sendStaffNotification(reportId, reporter, reported, reason, server);

        plugin.getLogger().info("[REPORT] #" + reportId + " - " + reporter + " reported " +
                reported + " from " + server + ": " + reason);
    }

    @Override
    public void onReportStatusUpdate(int reportId, String newStatus) {
        // Durum güncelleme toast bildirimi
        plugin.getMessageManager().sendStatusUpdateToast(reportId, newStatus);
    }

    @Override
    public void onReplayRequest(int reportId, String requester) {
        // Replay isteği geldiğinde
        plugin.getLogger().info("Replay requested for report #" + reportId + " by " + requester);
    }

    @Override
    public void onBroadcastMessage(String message, String permission) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission.isEmpty() || player.hasPermission(permission)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }
}