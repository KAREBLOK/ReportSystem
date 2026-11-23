package com.reportsystem.spigot.listeners;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class ReportListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReportListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Yetkili bildirimi: bekleyen raporlar
        if (player.hasPermission("reportsystem.notify")) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    int pendingReports = plugin.getReportService().getPendingReportsCount();

                    if (pendingReports > 0) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage("");
                            plugin.getMessageManager().sendMessage(player, "reports.notify.staff-join-title");
                            plugin.getMessageManager().sendMessage(player, "reports.notify.staff-join-message",
                                    "%count%", String.valueOf(pendingReports));
                            plugin.getMessageManager().sendMessage(player, "reports.notify.staff-join-action");
                            player.sendMessage("");
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to check pending reports: " + e.getMessage());
                }
            }, 20L);
        }

        // Raporcu geri bildirimi: çevrimdışıyken kabul edilen raporlar (Hypixel tarzı)
        if (plugin.getConfig().getBoolean("reports.reporter-feedback.enabled", true)) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    List<Report> unnotified = plugin.getReportService()
                            .getUnnotifiedReportsForReporter(player.getUniqueId().toString());

                    if (!unnotified.isEmpty()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            for (Report report : unnotified) {
                                plugin.getMessageManager().sendReporterFeedback(
                                        player, report.getId(), report.getReportedPlayerName());
                                plugin.getReportService().markReporterNotified(report.getId());
                            }
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to check reporter feedback: " + e.getMessage());
                }
            }, 40L);
        }

        // BungeeCord pending replay
        if (plugin.getConfigManager().isBungeeCordEnabled()) {
            Integer pendingReplayId = plugin.getPendingReplay(player.getUniqueId());
            if (pendingReplayId != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    com.reportsystem.spigot.replay.ReplayManager replayManager = plugin.getReplayManager();
                    if (replayManager == null) {
                        return;
                    }
                    java.util.UUID uuid = player.getUniqueId();
                    // Cikis yaptiysa donus kaydini ReplayManager'in quit temizligi siler
                    if (!player.isOnline()) {
                        return;
                    }
                    // Savunma derinligi: bekleyen replay yalnizca yetkiliye acilir (H-20: donus kaydi kalmasin)
                    if (!player.hasPermission("reportsystem.view.all")) {
                        replayManager.forgetOriginServer(uuid);
                        return;
                    }
                    // M-6: Overwatch incelemesindeki oyuncu normal replay baslatamaz (GUIListener.watchReplay
                    // ve ReportPluginMessageListener.handleStartReplay ile ayni kontrol)
                    com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener owl = plugin.getOverwatchReplayListener();
                    if (owl != null && owl.isReviewing(uuid)) {
                        plugin.getMessageManager().sendMessage(player, "replay.already-watching");
                        replayManager.forgetOriginServer(uuid);
                        return;
                    }
                    replayManager.startReplay(pendingReplayId, player).thenAccept(started -> {
                        // K-3: replay baslamadiysa donus kaydi kalmasin; aktif replay'in kaydina dokunma
                        if (!Boolean.TRUE.equals(started) && !replayManager.isWatchingReplay(uuid)) {
                            replayManager.forgetOriginServer(uuid);
                        }
                    });
                }, 40L);
            }
        }
    }
}