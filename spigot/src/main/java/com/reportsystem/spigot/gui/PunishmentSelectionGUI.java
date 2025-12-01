package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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

        plugin.getLogger().info("[PunishmentSelectionGUI] GUIConfig loaded for punishment-selection");
        plugin.getLogger().info("[PunishmentSelectionGUI] Config title: " + guiConfig.getTitle());
        plugin.getLogger().info("[PunishmentSelectionGUI] Config size: " + guiConfig.getSize());

        String title = guiConfig.getTitle("%player%", report.getReportedPlayerName());
        int size = guiConfig.getSize();
        this.inventory = Bukkit.createInventory(this, size, title);

        setupInventory();
    }

    private void setupInventory() {
        plugin.getLogger().info("[PunishmentSelectionGUI] Setting up inventory for " + report.getReportedPlayerName());

        // Background
        ItemStack bgItem = guiConfig.getBackgroundItem();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, bgItem);
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
            plugin.getLogger().info("[PunishmentSelectionGUI] Player info at slot " + slot);
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
            plugin.getLogger().info("[PunishmentSelectionGUI] Adding item " + path + " at slot " + slot + " (" + item.getType() + ")");
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
            viewer.sendMessage(plugin.getMessageManager().colorize("&cRapor kabul işlemi iptal edildi."));
            // Go back to report detail
            new ReportDetailGUI(viewer, report, plugin.getReplayDAO()).open();
        }
    }

    private void executePunishment(String type, long duration) {
        // Execute punishment
        String targetPlayer = report.getReportedPlayerName();
        String reason = report.getReason();
        String punisher = viewer.getName();

        boolean success = false;
        switch (type.toLowerCase()) {
            case "kick":
                Player target = org.bukkit.Bukkit.getPlayer(targetPlayer);
                if (target != null && target.isOnline()) {
                    String kickMessage = org.bukkit.ChatColor.RED + "" + org.bukkit.ChatColor.BOLD + "SUNUCUDAN ATILDINIZ!\n\n" +
                        org.bukkit.ChatColor.GRAY + "Sebep: " + org.bukkit.ChatColor.WHITE + reason + "\n" +
                        org.bukkit.ChatColor.GRAY + "Yetkili: " + org.bukkit.ChatColor.WHITE + punisher;
                    target.kickPlayer(kickMessage);
                    success = true;
                } else {
                    viewer.sendMessage(org.bukkit.ChatColor.RED + targetPlayer + " çevrimiçi değil!");
                    return;
                }
                break;
            case "warn":
                Player targetWarn = org.bukkit.Bukkit.getPlayer(targetPlayer);
                if (targetWarn != null && targetWarn.isOnline()) {
                    targetWarn.sendMessage("");
                    targetWarn.sendMessage(org.bukkit.ChatColor.GOLD + "" + org.bukkit.ChatColor.BOLD + "⚠ UYARI ⚠");
                    targetWarn.sendMessage(org.bukkit.ChatColor.YELLOW + "Yetkili tarafından uyarıldınız!");
                    targetWarn.sendMessage("");
                    targetWarn.sendMessage(org.bukkit.ChatColor.GRAY + "Sebep: " + org.bukkit.ChatColor.WHITE + reason);
                    targetWarn.sendMessage(org.bukkit.ChatColor.GRAY + "Yetkili: " + org.bukkit.ChatColor.WHITE + punisher);
                    targetWarn.sendMessage(org.bukkit.ChatColor.YELLOW + "Kuralları ihlal etmeye devam ederseniz ceza alabilirsiniz!");
                    targetWarn.sendMessage("");
                    targetWarn.playSound(targetWarn.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                    success = plugin.getPunishmentManager().getProvider().warn(targetPlayer, reason, punisher);
                } else {
                    viewer.sendMessage(org.bukkit.ChatColor.RED + targetPlayer + " çevrimiçi değil!");
                    return;
                }
                break;
        }

        if (success) {
            // Update report status to ACCEPTED
            report.setStatus(Report.Status.ACCEPTED.getValue());
            report.setResolvedBy(viewer.getName());
            report.setResolvedAt(System.currentTimeMillis());
            report.setPunished(true);
            report.setPunishmentType(type.toUpperCase());

            try {
                plugin.getReportService().updateReport(report);
                viewer.sendMessage(org.bukkit.ChatColor.GREEN + "✓ Ceza uygulandı ve rapor kabul edildi!");

                // Send Discord webhook notification
                if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                    plugin.getWebhookManager().sendReportClosedWithPunishmentNotification(
                        report, punisher, type.toUpperCase(), reason, type.equals("kick") ? "Instant" : "N/A"
                    );
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update report status after punishment: " + e.getMessage());
                viewer.sendMessage(org.bukkit.ChatColor.RED + "Ceza uygulandı ama rapor güncellenemedi!");
            }
        } else {
            viewer.sendMessage(org.bukkit.ChatColor.RED + "Ceza uygulanamadı!");
        }
    }

    private void acceptWithoutPunishment() {
        // Update report status to ACCEPTED
        report.setStatus(Report.Status.ACCEPTED.getValue());
        report.setResolvedBy(viewer.getName());
        report.setResolvedAt(System.currentTimeMillis());
        report.setPunished(false);

        try {
            plugin.getReportService().updateReport(report);
            viewer.sendMessage(plugin.getMessageManager().colorize("&aRapor #" + report.getId() + " ceza verilmeden kabul edildi."));

            // Send Discord webhook notification (no punishment)
            if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                plugin.getWebhookManager().sendReportClosedNotification(report, viewer.getName(), null);
                plugin.getLogger().info("[Webhook] Report #" + report.getId() + " accepted without punishment");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update report status: " + e.getMessage());
            viewer.sendMessage(plugin.getMessageManager().colorize("&cRapor güncellenemedi!"));
        }
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
