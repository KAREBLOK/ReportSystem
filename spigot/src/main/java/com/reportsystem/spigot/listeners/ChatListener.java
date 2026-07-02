package com.reportsystem.spigot.listeners;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.commands.ReportCommand;
import com.reportsystem.spigot.gui.ReportCreateGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatListener implements Listener {

    private final ReportSystemSpigot plugin;
    private final Set<UUID> waitingForInput = new HashSet<>();

    public ChatListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if player is waiting for custom report reason
        ReportSystemSpigot.PendingReport pendingReport = plugin.getPendingReport(uuid);
        if (pendingReport != null) {
            event.setCancelled(true);
            String inputMessage = event.getMessage();

            plugin.removePendingReport(uuid);

            if (inputMessage.equalsIgnoreCase("iptal") || inputMessage.equalsIgnoreCase("cancel")) {
                plugin.getMessageManager().sendMessage(player, "reports.chat.cancelled");
                return;
            }

            if (pendingReport.isOnline()) {
                Player target = Bukkit.getPlayer(UUID.fromString(pendingReport.getTarget()));
                if (target != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        createReport(player, target.getName(), inputMessage);
                    });
                }
            } else {
                String targetName = pendingReport.getTarget();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    createReport(player, targetName, inputMessage);
                });
            }
            return;
        }

        // Check if player is waiting for cross-server target input
        String crossServerTarget = plugin.getPendingCrossServerTarget(uuid);
        if (crossServerTarget != null) {
            event.setCancelled(true);
            String inputMessage = event.getMessage();

            plugin.removePendingCrossServerTarget(uuid);

            if (inputMessage.equalsIgnoreCase("iptal") || inputMessage.equalsIgnoreCase("cancel")) {
                plugin.getMessageManager().sendMessage(player, "reports.chat.cancelled");
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                new ReportCreateGUI(plugin, player, inputMessage).open();
            });
            return;
        }

        // Check for any other waiting input states
        if (waitingForInput.contains(uuid)) {
            event.setCancelled(true);
            // Handle other input states if needed
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.removePendingReport(uuid);
        plugin.removePendingCrossServerTarget(uuid);
        waitingForInput.remove(uuid);
    }

    private void createReport(Player reporter, String targetName, String reason) {
        // Validate reason length
        if (reason.length() < plugin.getConfigManager().getMinReasonLength()) {
            plugin.getMessageManager().sendMessage(reporter, "reports.reason-too-short",
                    "%min%", String.valueOf(plugin.getConfigManager().getMinReasonLength()));
            return;
        }

        if (reason.length() > plugin.getConfigManager().getMaxReasonLength()) {
            plugin.getMessageManager().sendMessage(reporter, "reports.reason-too-long",
                    "%max%", String.valueOf(plugin.getConfigManager().getMaxReasonLength()));
            return;
        }

        // Get target player if online
        Player targetPlayer = Bukkit.getPlayer(targetName);
        String targetUuid = "";

        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId().toString();
        }

        plugin.getLogger()
                .info("[ReportCreate-Chat] " + reporter.getName() + " -> " + targetName + " | Sebep: " + reason);

        // Create report through service
        plugin.getReportService().createReport(
                reporter.getName(),
                reporter.getUniqueId().toString(),
                targetName,
                targetUuid,
                reason,
                plugin.getServerName(),
                plugin.getConfigManager().getMaxReportsPerPlayer()).thenAccept(reportId -> {
                    if (reportId != null && reportId > 0) {
                        plugin.getLogger().info("[ReportCreate-Chat] Rapor başarıyla oluşturuldu - ID: " + reportId);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Success message
                            plugin.getMessageManager().sendReportSuccess(reporter, targetName, reportId);

                            // Otomatik olarak Overwatch kuyruğuna ekle (sadece PENDING raporlar için)
                            if (plugin.getOverwatchManager() != null) {
                                plugin.getOverwatchManager().addReportToQueue(reportId, 5); // Öncelik 5 (normal)
                            }

                            // Notify staff
                            if (plugin.getReportCommand() != null) {
                                plugin.getReportCommand().notifyStaff(reporter.getName(), targetName, reason, reportId);
                            }

                            // Update recording if target is online
                            if (targetPlayer != null
                                    && plugin.getRecordingManager().isRecording(targetPlayer.getUniqueId())) {
                                ReportCommand.updateRecordingWithReportId(targetPlayer.getUniqueId(), reportId);
                            }

                            // Send Discord webhook notification
                            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                                // Fetch the created report to send to webhook (async to avoid blocking)
                                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                                    Report report = plugin.getReportService().getReportById(reportId);
                                    if (report != null) {
                                        plugin.getLogger().info(
                                                "[Webhook] Sending new report notification for report #" + reportId);
                                        plugin.getWebhookManager().sendNewReportNotification(report);
                                    } else {
                                        plugin.getLogger().warning(
                                                "[Webhook] Could not fetch report #" + reportId + " from database!");
                                    }
                                });
                            } else {
                                plugin.getLogger().info("[Webhook] Webhook disabled or manager not initialized");
                            }
                        });
                    } else if (reportId != null && reportId == -2) {
                        // Limit exceeded
                        plugin.getLogger().warning("[ReportCreate-Chat] Report limit exceeded: " + reporter.getName()
                                + " -> " + targetName);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendReportLimitReached(reporter, targetName,
                                    plugin.getConfigManager().getMaxReportsPerPlayer());
                        });
                    } else {
                        // General error (-1 or null)
                        plugin.getLogger().severe("[ReportCreate-Chat] Report creation failed! ReportID: " + reportId);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getMessageManager().sendMessage(reporter, "reports.create-failed");
                            plugin.getMessageManager().sendMessage(reporter, "reports.contact-staff");
                        });
                    }
                }).exceptionally(throwable -> {
                    // Async error handling
                    plugin.getLogger().severe("[ReportCreate-Chat] Async error: " + throwable.getMessage());
                    throwable.printStackTrace();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getMessageManager().sendMessage(reporter, "reports.unexpected-error");
                    });
                    return null;
                });
    }

    public void waitForCustomReason(Player player, String targetName) {
        waitingForInput.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        plugin.getMessageManager().sendMessage(player, "reports.chat.enter-reason");
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }

    public void waitForSearch(Player player) {
        waitingForInput.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        plugin.getMessageManager().sendMessage(player, "reports.chat.enter-search");
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }

    public void waitForPunishmentReason(Player player, String targetName, String punishType) {
        waitingForInput.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        plugin.getMessageManager().sendMessage(player, "reports.chat.enter-punishment-reason",
                "%type%", punishType);
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }

    public void waitForAssignment(Player player, com.reportsystem.common.models.Report report) {
        waitingForInput.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        plugin.getMessageManager().sendMessage(player, "reports.chat.enter-assignment");
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }

    public void waitForNote(Player player, com.reportsystem.common.models.Report report) {
        waitingForInput.add(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        plugin.getMessageManager().sendMessage(player, "reports.chat.enter-note");
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }
}