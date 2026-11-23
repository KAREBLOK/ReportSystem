package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.ReportResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;

public class PunishmentSelectionGUI implements InventoryHolder {

    private final Player viewer;
    private final Report report;
    private final ReportSystemSpigot plugin;
    private final Inventory inventory;
    private final GUIConfig guiConfig;

    public PunishmentSelectionGUI(Player viewer, Report report, ReportSystemSpigot plugin) {
        this.viewer = viewer;
        this.report = report;
        this.plugin = plugin;
        this.guiConfig = new GUIConfig(plugin, "punishment-selection");

        plugin.debug("[PunishmentSelectionGUI] GUIConfig loaded for punishment-selection");
        plugin.debug("[PunishmentSelectionGUI] Config title: " + guiConfig.getTitle());
        plugin.debug("[PunishmentSelectionGUI] Config size: " + guiConfig.getSize());

        String title = guiConfig.getTitle("%player%", report.getReportedPlayerName());
        int size = guiConfig.getSize();
        this.inventory = Bukkit.createInventory(this, size, title);

        setupInventory();
    }

    private void setupInventory() {
        plugin.debug("[PunishmentSelectionGUI] Setting up inventory for " + report.getReportedPlayerName());

        // Background
        if (guiConfig.isBackgroundEnabled()) {
            ItemStack bgItem = guiConfig.getBackgroundItem();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, bgItem);
            }
        }

        // Player info
        ItemStack playerInfo = guiConfig.getItem("player-info",
                "%player%", report.getReportedPlayerName(),
                "%reason%", report.getReason()
        );
        if (playerInfo != null && playerInfo.getType() == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) playerInfo.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwner(report.getReportedPlayerName());
                playerInfo.setItemMeta(skullMeta);
            }
            int slot = guiConfig.getItemSlot("player-info");
            plugin.debug("[PunishmentSelectionGUI] Player info at slot " + slot);
            inventory.setItem(slot, playerInfo);
        } else {
            plugin.getLogger().warning("[PunishmentSelectionGUI] Player info item is null or not a PLAYER_HEAD!");
        }

        // Punishment options
        addItem("ban");
        addItem("mute");
        addItem("kick");
        addItem("warn");

        // Bottom actions
        addItem("no-punishment");
        addItem("back");
    }

    private void addItem(String path) {
        ItemStack item = guiConfig.getItem(path);
        if (item != null) {
            int slot = guiConfig.getItemSlot(path);
            plugin.debug("[PunishmentSelectionGUI] Adding item " + path + " at slot " + slot + " (" + item.getType() + ")");
            inventory.setItem(slot, item);
        } else {
            plugin.getLogger().warning("[PunishmentSelectionGUI] Item " + path + " is null!");
        }
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        // GÜVENLİK: ceza uygulama yetkisi olmayan hiç kimse bu GUI ile işlem yapamaz.
        // ("Geri" ve iptal dahil kapatılır; yetkisiz kişi buraya normalde erişememeli.)
        if (!viewer.hasPermission("reportsystem.punish")) {
            viewer.closeInventory();
            plugin.getMessageManager().sendNoPermission(viewer);
            return;
        }

        int slot = event.getSlot();

        if (slot == guiConfig.getItemSlot("ban")) {
            viewer.closeInventory();
            // Open animated ban duration selection
            new AnimatedBanGUI(plugin, viewer, report).open();
        } else if (slot == guiConfig.getItemSlot("mute")) {
            viewer.closeInventory();
            // Open mute duration selection
            PunishmentGUI muteGUI = new PunishmentGUI(plugin, viewer, report);
            muteGUI.setPunishmentType("mute");
            muteGUI.open();
        } else if (slot == guiConfig.getItemSlot("kick")) {
            viewer.closeInventory();
            // Execute kick immediately
            executePunishment("KICK", -1);
        } else if (slot == guiConfig.getItemSlot("warn")) {
            viewer.closeInventory();
            // Execute warn immediately
            executePunishment("WARN", -1);
        } else if (slot == guiConfig.getItemSlot("no-punishment")) {
            viewer.closeInventory();
            // Accept report without punishment
            acceptWithoutPunishment();
        } else if (slot == guiConfig.getItemSlot("back")) {
            viewer.closeInventory();
            plugin.getMessageManager().sendMessage(viewer, "reports.actions.accept-cancelled");
            // Go back to report detail
            ReportDetailGUI.openAsync(plugin, viewer, report);
        }
    }

    /** Talep sonucu ALREADY_HANDLED/ERROR ise yetkiliye bildirir; CLAIMED ise false döner. */
    private boolean handleClaimFailure(ReportResolution.ClaimResult result) {
        if (result == ReportResolution.ClaimResult.CLAIMED) {
            return false;
        }
        if (viewer.isOnline()) {
            plugin.getMessageManager().sendMessage(viewer, result == ReportResolution.ClaimResult.ALREADY_HANDLED
                    ? "reports.actions.already-handled"
                    : "reports.actions.database-error");
        }
        return true;
    }

    private void sendOfflineMessage(String typeLower, String targetPlayer) {
        if (!viewer.isOnline()) return;
        plugin.getMessageManager().sendMessage(viewer,
                typeLower.equals("warn") ? "punishments.warn.offline" : "punishments.kick.offline",
                "%player%", targetPlayer);
    }

    private void executePunishment(String type, long duration) {
        // Execute punishment
        String targetPlayer = report.getReportedPlayerName();
        String reason = report.getReason();
        String punisher = viewer.getName();
        String typeLower = type.toLowerCase(Locale.ROOT);
        String typeUpper = type.toUpperCase(Locale.ROOT);
        int reportId = report.getId();

        // Kick/uyarı çevrimiçi hedef ister — talepten önce kontrol (CRA-02: tam ad eşleşmesi)
        if (Bukkit.getPlayerExact(targetPlayer) == null) {
            sendOfflineMessage(typeLower, targetPlayer);
            return;
        }

        // ÇİFT-CEZA KORUMASI (DATA-03/DB-21): cezadan ÖNCE raporu atomik olarak talep et;
        // başka bir yetkili raporu çoktan işlediyse ceza uygulanmaz.
        ReportResolution.claim(plugin, reportId, Report.Status.ACCEPTED, result -> {
            if (handleClaimFailure(result)) return;

            // Talep sırasında hedef çıkmış olabilir
            Player target = Bukkit.getPlayerExact(targetPlayer);
            if (target == null || !target.isOnline()) {
                ReportResolution.revert(plugin, reportId, Report.Status.ACCEPTED);
                sendOfflineMessage(typeLower, targetPlayer);
                return;
            }

            boolean success = false;
            try {
                switch (typeLower) {
                    case "kick": {
                        String kickMessage = plugin.getMessageManager().colorize(
                            plugin.getMessageManager().getMessage("punishments.kick.player-message")
                                    .replace("%reason%", reason)
                                    .replace("%staff%", punisher));
                        target.kickPlayer(kickMessage);
                        success = true;
                        break;
                    }
                    case "warn": {
                        String warnMsg = plugin.getMessageManager().getMessage("punishments.warn.player-message")
                                .replace("%reason%", reason)
                                .replace("%staff%", punisher)
                                .replace("%count%", "");
                        for (String line : warnMsg.split("\n")) {
                            target.sendMessage(plugin.getMessageManager().colorize(line));
                        }
                        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                        success = plugin.getPunishmentManager().getProvider().warn(targetPlayer, reason, punisher);
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "[PunishmentSelectionGUI] Punishment error (" + typeUpper + " " + targetPlayer + ")", e);
                success = false;
            }

            if (!success) {
                // Ceza uygulanamadı → rapor tekrar PENDING (yetkili tekrar deneyebilir)
                ReportResolution.revert(plugin, reportId, Report.Status.ACCEPTED);
                if (viewer.isOnline()) {
                    plugin.getMessageManager().sendMessage(viewer, "punishments.failed");
                }
                return;
            }

            // Yalnız webhook nesnesi için (DB'ye bayat nesne yazılmaz)
            report.setStatus(Report.Status.ACCEPTED.getValue());
            report.setResolvedBy(punisher);
            report.setResolvedAt(System.currentTimeMillis());
            report.setPunished(true);
            report.setPunishmentType(typeUpper);

            // Ceza türü + Overwatch oylamasını sonlandır (SUÇLU) — async
            ReportResolution.finishAsync(plugin, reportId, typeUpper, true, punisher);

            if (viewer.isOnline()) {
                plugin.getMessageManager().sendMessage(viewer, "punishments.applied-with-report");
            }

            // Send Discord webhook notification
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedWithPunishmentNotification(
                    report, punisher, typeUpper, reason, typeLower.equals("kick") ? "Instant" : "N/A"
                );
            }
        });
    }

    private void acceptWithoutPunishment() {
        String staffName = viewer.getName();
        int reportId = report.getId();

        // ÇİFT-İŞLEM KORUMASI: yalnız hâlâ PENDING olan rapor kabul edilir (atomik talep)
        ReportResolution.claim(plugin, reportId, Report.Status.ACCEPTED, result -> {
            if (handleClaimFailure(result)) return;

            // Yalnız webhook nesnesi için
            report.setStatus(Report.Status.ACCEPTED.getValue());
            report.setResolvedBy(staffName);
            report.setResolvedAt(System.currentTimeMillis());
            report.setPunished(false);

            // Overwatch oylamasını yetkili kararına göre sonlandır (SUÇLU/KABUL) — async
            ReportResolution.finishAsync(plugin, reportId, null, true, staffName);

            if (viewer.isOnline()) {
                plugin.getMessageManager().sendMessage(viewer, "reports.actions.accepted-no-punishment", "%id%", String.valueOf(reportId));
            }

            // Send Discord webhook notification (no punishment)
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedNotification(report, staffName, null);
                plugin.debug("[Webhook] Report #" + reportId + " accepted without punishment");
            }
        });
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }

    public Report getReport() {
        return report;
    }
}
