package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

public class AnimatedBanGUI extends GUI {

    private final Report report;
    private final String targetName;
    private final GUIConfig guiConfig;

    public AnimatedBanGUI(ReportSystemSpigot plugin, Player player, Report report) {
        super(plugin, player);
        this.report = report;
        this.targetName = report.getReportedPlayerName();
        this.guiConfig = new GUIConfig((ReportSystemSpigot) plugin, "animated-ban");
    }

    @Override
    public void build() {
        String title = guiConfig.getTitle("%target%", targetName);
        int size = guiConfig.getSize();
        inventory = Bukkit.createInventory(this, size, title);

        // Background
        if (guiConfig.isBackgroundEnabled()) {
            String bgMaterial = guiConfig.getConfig().getString("background.material", "BLACK_STAINED_GLASS_PANE");
            Material bgMat = Material.getMaterial(bgMaterial);
            if (bgMat != null) {
                fillBorder(bgMat);
            }
        }

        // Player info
        inventory.setItem(guiConfig.getItemSlot("player-info"), createPlayerInfoItem());

        // Duration selections
        addDurationItem("durations.30-minutes");
        addDurationItem("durations.1-hour");
        addDurationItem("durations.6-hours");
        addDurationItem("durations.1-day");
        addDurationItem("durations.7-days");
        addDurationItem("durations.30-days");
        addDurationItem("durations.permanent");
        addDurationItem("durations.custom");

        // Control buttons
        ItemStack cancelItem = guiConfig.getItem("controls.cancel");
        if (cancelItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.cancel"), cancelItem);
        }

        ItemStack backItem = guiConfig.getItem("controls.back");
        if (backItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.back"), backItem);
        }
    }

    private ItemStack createPlayerInfoItem() {
        String materialName = guiConfig.getConfig().getString("player-info.material", "PLAYER_HEAD");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PLAYER_HEAD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfigString("player-info.name", "&4⚡ %target%");
            name = name.replace("%target%", targetName);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = guiConfig.getConfigStringList("player-info.lore");
            List<String> processedLore = lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private void addDurationItem(String path) {
        String materialName = guiConfig.getConfig().getString(path + ".material", "PAPER");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfigString(path + ".name", "&7Duration");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = guiConfig.getConfigStringList(path + ".lore");
            List<String> processedLore = lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            // Store duration in persistent data
            String duration = guiConfig.getConfig().getString(path + ".duration", "1h");
            NamespacedKey key = new NamespacedKey((ReportSystemSpigot) plugin, "ban_duration");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, duration);

            item.setItemMeta(meta);
        }

        int slot = guiConfig.getItemSlot(path);
        inventory.setItem(slot, item);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Handled in GUIListener
    }

    @Override
    public void open() {
        build();
        player.openInventory(inventory);
    }

    public Report getReport() {
        return report;
    }

    public String getTargetName() {
        return targetName;
    }
}
