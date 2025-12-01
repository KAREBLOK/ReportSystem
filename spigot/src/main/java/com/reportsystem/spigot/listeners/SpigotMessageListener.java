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
        // Yetkililere bildirim
        String notification = ChatColor.translateAlternateColorCodes('&',
                "&8[&6Rapor&8] &7(&e" + server + "&7) &f" + reporter + " &7➜ &c" + reported +
                        "\n&7Sebep: &f" + reason + " &8(#" + reportId + ")");

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("reportsystem.notify")) {
                staff.sendMessage(notification);

                // Ses efekti
                staff.playSound(staff.getLocation(),
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        plugin.getLogger().info("[REPORT] #" + reportId + " - " + reporter + " reported " +
                reported + " from " + server + ": " + reason);
    }

    @Override
    public void onReportStatusUpdate(int reportId, String newStatus) {
        String message = ChatColor.translateAlternateColorCodes('&',
                "&8[&6Rapor&8] &7Rapor #" + reportId + " durumu güncellendi: &e" + newStatus);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("reportsystem.notify")) {
                staff.sendMessage(message);
            }
        }
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