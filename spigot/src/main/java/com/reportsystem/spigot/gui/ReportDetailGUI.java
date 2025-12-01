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

    public ReportDetailGUI(Player viewer, Report report, ReplayDAO replayDAO) {
        this.plugin = (ReportSystemSpigot) Bukkit.getPluginManager().getPlugin("ReportSystem-Spigot");
        this.viewer = viewer;
        this.report = report;
        this.replayDAO = replayDAO;
        this.guiConfig = new GUIConfig(plugin, "report-detail");

        // Create inventory with title from messages
        String title = plugin.getMessageManager().getMessage("gui.report-detail.title");
        title = plugin.getMessageManager().colorize(title.replace("%id%", String.valueOf(report.getId())));
        int size = guiConfig.getSize();
        this.inventory = Bukkit.createInventory(this, size, title);

        // Check if replay exists
        try {
            hasReplay = replayDAO.getReplayByReportId(report.getId()).isPresent();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        setupInventory();
    }

    private void setupInventory() {
        // Background
        ItemStack glass = createGlassPane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
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
            // Get total reports for player
            int totalReports = plugin.getReportService().getReportCount(report.getReportedPlayerName());

            // Get status name and color
            String statusName = plugin.getMessageManager().getStatusName(report.getStatus());
            String statusColor = getStatusColorCode(report.getStatus());

            // Get labels from message file
            String labelReportId = plugin.getMessageManager().getMessage("gui.report-detail.labels.report-id");
            String labelReporter = plugin.getMessageManager().getMessage("gui.report-detail.labels.reporter");
            String labelReason = plugin.getMessageManager().getMessage("gui.report-detail.labels.reason");
            String labelDate = plugin.getMessageManager().getMessage("gui.report-detail.labels.date");
            String labelServer = plugin.getMessageManager().getMessage("gui.report-detail.labels.server");
            String labelTotalReports = plugin.getMessageManager().getMessage("gui.report-detail.labels.total-reports");
            String labelStatus = plugin.getMessageManager().getMessage("gui.report-detail.labels.status");
            String labelTimes = plugin.getMessageManager().getMessage("gui.report-detail.labels.times");
            String labelUnknown = plugin.getMessageManager().getMessage("gui.report-detail.labels.unknown");

            // Name from messages
            String name = plugin.getMessageManager().getMessage("gui.report-detail.player-head-name");
            name = name.replace("%reported%", report.getReportedPlayerName());
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Lore with all info
            meta.setLore(Arrays.asList(
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

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createReplayButton() {
        String basePath = hasReplay ? "replay.available" : "replay.unavailable";
        String msgPath = hasReplay ? "gui.report-detail.buttons.replay-available" : "gui.report-detail.buttons.replay-unavailable";

        String materialName = guiConfig.getConfig().getString(basePath + ".material", "ENDER_PEARL");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get name from messages
            String name = plugin.getMessageManager().getMessage(msgPath + ".name");
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Get lore from messages
            List<String> lore = plugin.getMessageManager().getMessageList(msgPath + ".lore");
            List<String> processedLore = lore.stream()
                    .map(line -> plugin.getMessageManager().colorize(line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAcceptButton() {
        return createButton("actions.accept", "gui.report-detail.buttons.accept", Material.LIME_DYE);
    }

    private ItemStack createRejectButton() {
        return createButton("actions.reject", "gui.report-detail.buttons.reject", Material.RED_DYE);
    }

    private ItemStack createBanButton() {
        return createButton("punishments.ban", "gui.report-detail.buttons.ban", Material.IRON_AXE);
    }

    private ItemStack createMuteButton() {
        return createButton("punishments.mute", "gui.report-detail.buttons.mute", Material.BARRIER);
    }

    private ItemStack createKickButton() {
        return createButton("punishments.kick", "gui.report-detail.buttons.kick", Material.LEATHER_BOOTS);
    }

    private ItemStack createWarnButton() {
        return createButton("punishments.warn", "gui.report-detail.buttons.warn", Material.PAPER);
    }

    private ItemStack createTeleportButton() {
        return createButton("punishments.teleport", "gui.report-detail.buttons.teleport", Material.ENDER_PEARL);
    }

    private ItemStack createBackButton() {
        return createButton("navigation.back", "gui.report-detail.buttons.back", Material.ARROW);
    }

    /**
     * Helper method to create button items
     * @param configPath Path in config for material (e.g., "actions.accept")
     * @param messagePath Path in messages for name/lore (e.g., "gui.report-detail.buttons.accept")
     * @param defaultMaterial Default material if not found in config
     * @return ItemStack for the button
     */
    private ItemStack createButton(String configPath, String messagePath, Material defaultMaterial) {
        // Get material from config
        String materialName = guiConfig.getConfig().getString(configPath + ".material");
        Material material = materialName != null ? Material.getMaterial(materialName) : defaultMaterial;
        if (material == null) material = defaultMaterial;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Get name from messages
            String name = plugin.getMessageManager().getMessage(messagePath + ".name");
            meta.setDisplayName(plugin.getMessageManager().colorize(name));

            // Get lore from messages
            List<String> lore = plugin.getMessageManager().getMessageList(messagePath + ".lore");
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
}
