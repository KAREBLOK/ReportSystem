package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ReportListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReportListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("reportsystem.notify")) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    // DÜZELTİLDİ: Rapor listesi yerine sayısını al
                    int pendingReports = plugin.getReportService().getPendingReportsCount();

                    if (pendingReports > 0) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage("");
                            player.sendMessage("§6§l⚠ BEKLEYEN RAPORLAR");
                            player.sendMessage("§eSunucuda §c" + pendingReports + " §eadet bekleyen rapor var!");
                            player.sendMessage("§eKontrol etmek için: §b/reports");
                            player.sendMessage("");
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to check pending reports: " + e.getMessage());
                }
            }, 20L);
        }

        if (plugin.getConfigManager().isBungeeCordEnabled()) {
            Integer pendingReplayId = plugin.getPendingReplay(player.getUniqueId());
            if (pendingReplayId != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getReplayManager().startReplay(pendingReplayId, player);
                }, 40L);
            }
        }
    }
}