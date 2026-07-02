package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

public class PunishmentGUI extends GUI {
    private final Report report;
    private final String targetName;
    private String punishmentType = "mute";
    private final GUIConfig guiConfig;

    public PunishmentGUI(ReportSystemSpigot plugin, Player player, Report report) {
        super(plugin, player);
        this.report = report;
        this.targetName = report.getReportedPlayerName();
        this.guiConfig = new GUIConfig((ReportSystemSpigot) plugin, "punishment");
    }

    public PunishmentGUI(ReportSystemSpigot plugin, Player player, String targetName) {
        super(plugin, player);
        this.report = null;
        this.targetName = targetName;
        this.guiConfig = new GUIConfig((ReportSystemSpigot) plugin, "punishment");
    }

    @Override
    public void open() {
        build();
        player.openInventory(getInventory());
    }

    @Override
    public void build() {
        String title = guiConfig.getTitle("%target%", targetName);
        int size = guiConfig.getSize();
        inventory = Bukkit.createInventory(this, size, title);

        setupTimedPunishment();
    }

    private void setupTimedPunishment() {
        // Background
        if (guiConfig.isBackgroundEnabled()) {
            String bgMaterial = guiConfig.getConfig().getString("background.material", "RED_STAINED_GLASS_PANE");
            Material bgMat = Material.getMaterial(bgMaterial);
            if (bgMat != null) {
                fillBorder(bgMat);
            }
        }

        // Player info
        inventory.setItem(guiConfig.getItemSlot("player-info"), createPlayerInfoItem());

        // Time selections
        addTimeItem("times.30-minutes");
        addTimeItem("times.1-hour");
        addTimeItem("times.3-hours");
        addTimeItem("times.1-day");
        addTimeItem("times.7-days");
        addTimeItem("times.30-days");
        addTimeItem("times.permanent");

        // Control buttons
        ItemStack cancelItem = guiConfig.getItem("controls.cancel");
        if (cancelItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.cancel"), cancelItem);
        }

        if (report != null) {
            ItemStack backItem = guiConfig.getItem("controls.back");
            if (backItem != null) {
                inventory.setItem(guiConfig.getItemSlot("controls.back"), backItem);
            }
        }
    }

    private ItemStack createPlayerInfoItem() {
        String materialName = guiConfig.getConfig().getString("player-info.material", "PLAYER_HEAD");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PLAYER_HEAD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfigString("player-info.name", "&c⚡ %target%");
            name = name.replace("%target%", targetName);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            if (report != null) {
                List<String> lore = guiConfig.getConfigStringList("player-info.lore");
                List<String> processedLore = lore.stream()
                        .map(line -> line.replace("%reason%", report.getReason()))
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private void addTimeItem(String path) {
        String materialName = guiConfig.getConfig().getString(path + ".material", "PAPER");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfigString(path + ".name", "&7Time");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            String duration = guiConfig.getConfig().getString(path + ".duration", "1h");

            // Use PersistentDataContainer instead of deprecated setLocalizedName
            NamespacedKey durationKey = new NamespacedKey((ReportSystemSpigot) plugin, "punishment_duration");
            meta.getPersistentDataContainer().set(durationKey, PersistentDataType.STRING, duration);

            List<String> lore = guiConfig.getConfigStringList(path + ".lore");
            if (!lore.isEmpty()) {
                List<String> processedLore = lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }

            item.setItemMeta(meta);
        }

        int slot = guiConfig.getItemSlot(path);
        inventory.setItem(slot, item);
    }

    public Report getReport() { return report; }
    public String getTargetName() { return targetName; }
    public String getPunishmentType() { return punishmentType; }
    public void setPunishmentType(String type) { this.punishmentType = type; }
}
