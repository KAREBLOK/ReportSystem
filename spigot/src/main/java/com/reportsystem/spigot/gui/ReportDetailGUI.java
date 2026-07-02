package com.reportsystem.spigot.gui;

import com.reportsystem.common.database.ReplayDAO;
import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReportDetailGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player viewer;
    private final Report report;
    private final ReplayDAO replayDAO;
    private final Inventory inventory;
    private final GUIConfig guiConfig;
    private boolean hasReplay = false;
    private int totalReports = 0;

    public ReportDetailGUI(Player viewer, Report report, ReplayDAO replayDAO) {
        this.plugin = (ReportSystemSpigot) Bukkit.getPluginManager().getPlugin("ReportSystem-Spigot");
        this.viewer = viewer;
        this.report = report;
        this.replayDAO = replayDAO;
        this.guiConfig = new GUIConfig(plugin, "report-detail");

        // Create inventory with title from GUI config
        String title = guiConfig.getTitle("%id%", String.valueOf(report.getId()));
        int size = guiConfig.getSize();
        this.inventory = Bukkit.createInventory(this, size, title);

        // Check if replay exists
        try {
            hasReplay = replayDAO.getReplayByReportId(report.getId()).isPresent();
        } catch (SQLException e) {
            plugin.getLogger().warning("Replay kontrolü sırasında hata: " + e.getMessage());
        }

        // Cache total reports count
        this.totalReports = plugin.getReportService().getReportCount(report.getReportedPlayerName());

        setupInventory();
    }

    private void setupInventory() {
        // Background
        if (guiConfig.isBackgroundEnabled()) {
            ItemStack glass = createGlassPane();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, glass);
            }
        }

        // Player head (report info)
        inventory.setItem(guiConfig.getItemSlot("player-head"), createInfoItem());

        // Replay button
        inventory.setItem(guiConfig.getItemSlot("replay"), createReplayButton());

        // Action buttons (only if pending)
        if ("PENDING".equalsIgnoreCase(report.getStatus())) {
            inventory.setItem(guiConfig.getItemSlot("actions.accept"), createAcceptButton());
            inventory.setItem(guiConfig.getItemSlot("actions.reject"), createRejectButton());
            inventory.setItem(guiConfig.getItemSlot("punishments.teleport"), createTeleportButton());
        }

        // Back button
        inventory.setItem(guiConfig.getItemSlot("navigation.back"), createBackButton());
    }

    private ItemStack createGlassPane() {
        String materialName = guiConfig.getConfig().getString("background.material", "GRAY_STAINED_GLASS_PANE");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfig().getString("background.name", " ");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Use cached total reports for player

            // Get status name and color
            String statusName = plugin.getMessageManager().getStatusName(report.getStatus());
            String statusColor = getStatusColorCode(report.getStatus());

            // Get labels from GUI config
            String labelReportId = guiConfig.getConfig().getString("player-head.labels.report-id", "Rapor ID:");
            String labelReporter = guiConfig.getConfig().getString("player-head.labels.reporter", "Raporlayan:");
            String labelReason = guiConfig.getConfig().getString("player-head.labels.reason", "Sebep:");
            String labelDate = guiConfig.getConfig().getString("player-head.labels.date", "Tarih:");
            String labelServer = guiConfig.getConfig().getString("player-head.labels.server", "Sunucu:");
            String labelTotalReports = guiConfig.getConfig().getString("player-head.labels.total-reports", "Toplam Rapor:");
            String labelStatus = guiConfig.getConfig().getString("player-head.labels.status", "Durum:");
            String labelTimes = guiConfig.getConfig().getString("player-head.labels.times", "kez");
            String labelUnknown = guiConfig.getConfig().getString("player-head.labels.unknown", "Bilinmiyor");

            // Name from GUI config
            String name = guiConfig.getConfig().getString("player-head.name", "&c⚡ %reported%")
                .replace("%reported%", report.getReportedPlayerName());
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Trust level
            String trustLine = "";
            if (plugin.getTrustLevelManager() != null) {
                String labelTrust = guiConfig.getConfig().getString("player-head.labels.trust-level", "Hesap Durumu:");
                org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(report.getReportedPlayerName());
                String trustFormatted = plugin.getTrustLevelManager().getFormattedTrustLevel(target.getUniqueId(), report.getReportedPlayerName());
                trustLine = ChatColor.GRAY + labelTrust + " " + plugin.getMessageManager().colorize(trustFormatted);
            }

            // Lore with all info
            java.util.List<String> lore = new java.util.ArrayList<>(Arrays.asList(
                    "",
                    ChatColor.GRAY + labelReportId + " " + ChatColor.WHITE + "#" + report.getId(),
                    ChatColor.GRAY + labelReporter + " " + ChatColor.AQUA + report.getReporterName(),
                    ChatColor.GRAY + labelReason + " " + ChatColor.YELLOW + report.getReason(),
                    ChatColor.GRAY + labelDate + " " + ChatColor.WHITE + report.getFormattedTimestamp(),
                    ChatColor.GRAY + labelServer + " " + ChatColor.AQUA + (report.getServerName() != null ? report.getServerName() : labelUnknown),
                    "",
                    ChatColor.GRAY + labelTotalReports + " " + ChatColor.RED + totalReports + " " + labelTimes,
                    ChatColor.GRAY + labelStatus + " " + ChatColor.translateAlternateColorCodes('&', statusColor) + statusName
            ));
            if (!trustLine.isEmpty()) {
                lore.add(trustLine);
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReplayButton() {
        String basePath = hasReplay ? "replay.available" : "replay.unavailable";

        String materialName = guiConfig.getConfig().getString(basePath + ".material", "ENDER_PEARL");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get name from GUI config
            String name = guiConfig.getConfig().getString(basePath + ".name", "");
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Get lore from GUI config
            List<String> lore = guiConfig.getConfig().getStringList(basePath + ".lore");
            List<String> processedLore = lore.stream()
                    .map(line -> plugin.getMessageManager().colorize(line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAcceptButton() {
        return createButton("actions.accept", Material.LIME_DYE);
    }

    private ItemStack createRejectButton() {
        return createButton("actions.reject", Material.RED_DYE);
    }

    private ItemStack createBanButton() {
        return createButton("punishments.ban", Material.IRON_AXE);
    }

    private ItemStack createMuteButton() {
        return createButton("punishments.mute", Material.BARRIER);
    }

    private ItemStack createKickButton() {
        return createButton("punishments.kick", Material.LEATHER_BOOTS);
    }

    private ItemStack createWarnButton() {
        return createButton("punishments.warn", Material.PAPER);
    }

    private ItemStack createTeleportButton() {
        return createButton("punishments.teleport", Material.GOLD_NUGGET);
    }

    private ItemStack createBackButton() {
        return createButton("navigation.back", Material.ARROW);
    }

    /**
     * Helper method to create button items
     * @param configPath Path in GUI config for material/name/lore (e.g., "actions.accept")
     * @param defaultMaterial Default material if not found in config
     * @return ItemStack for the button
     */
    private ItemStack createButton(String configPath, Material defaultMaterial) {
        // Get material from config
        String materialName = guiConfig.getConfig().getString(configPath + ".material");
        Material material = materialName != null ? Material.getMaterial(materialName) : defaultMaterial;
        if (material == null) material = defaultMaterial;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get name from GUI config
            String name = guiConfig.getConfig().getString(configPath + ".name", "");
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Get lore from GUI config
            List<String> lore = guiConfig.getConfig().getStringList(configPath + ".lore");
            List<String> processedLore = lore.stream()
                    .map(line -> plugin.getMessageManager().colorize(line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private String getStatusColorCode(String status) {
        switch (status.toUpperCase()) {
            case "PENDING":
                return "&e";
            case "ACCEPTED":
                return "&a";
            case "REJECTED":
                return "&c";
            case "CLOSED":
                return "&7";
            default:
                return "&7";
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public Report getReport() {
        return report;
    }

    public Player getViewer() {
        return viewer;
    }

    public ReplayDAO getReplayDAO() {
        return replayDAO;
    }

    public boolean hasReplay() {
        return hasReplay;
    }

    public GUIConfig getGuiConfig() {
        return guiConfig;
    }
}
