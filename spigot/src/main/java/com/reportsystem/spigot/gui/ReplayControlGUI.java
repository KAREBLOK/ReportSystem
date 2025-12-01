package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.replay.ReplayPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ReplayControlGUI extends GUI {
    private final ReplayPlayer replayPlayer;
    private final GUIConfig guiConfig;

    public ReplayControlGUI(ReportSystemSpigot plugin, Player player, ReplayPlayer replayPlayer) {
        super(plugin, player);
        this.replayPlayer = replayPlayer;
        this.guiConfig = new GUIConfig((ReportSystemSpigot) plugin, "replay-control");
    }

    @Override
    public void open() {
        build();
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    @Override
    public void build() {
        String title = guiConfig.getTitle();
        int size = guiConfig.getSize();
        inventory = Bukkit.createInventory(this, size, title);

        // Background
        String bgMaterial = guiConfig.getConfig().getString("background.material", "GRAY_STAINED_GLASS_PANE");
        Material bgMat = Material.getMaterial(bgMaterial);
        if (bgMat != null) {
            ItemStack bg = new ItemStack(bgMat);
            ItemMeta bgMeta = bg.getItemMeta();
            if (bgMeta != null) {
                String bgName = guiConfig.getConfig().getString("background.name", " ");
                bgMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', bgName));
                bg.setItemMeta(bgMeta);
            }
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, bg);
            }
        }

        // Play/Pause button
        String ppPath = replayPlayer.isPaused() ? "controls.play-pause.play" : "controls.play-pause.pause";
        ItemStack playPauseItem = createControlItem(ppPath);
        if (playPauseItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.play-pause"), playPauseItem);
        }

        // Stop button
        ItemStack stopItem = guiConfig.getItem("controls.stop");
        if (stopItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.stop"), stopItem);
        }

        // Speed button
        ItemStack speedItem = createSpeedItem();
        if (speedItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.speed"), speedItem);
        }

        // Info button
        ItemStack infoItem = createInfoItem();
        if (infoItem != null) {
            inventory.setItem(guiConfig.getItemSlot("controls.info"), infoItem);
        }

        // Time controls
        ItemStack rewindItem = guiConfig.getItem("time-controls.rewind-10s");
        if (rewindItem != null) {
            inventory.setItem(guiConfig.getItemSlot("time-controls.rewind-10s"), rewindItem);
        }

        ItemStack forwardItem = guiConfig.getItem("time-controls.forward-10s");
        if (forwardItem != null) {
            inventory.setItem(guiConfig.getItemSlot("time-controls.forward-10s"), forwardItem);
        }

        ItemStack teleportItem = guiConfig.getItem("time-controls.teleport");
        if (teleportItem != null) {
            inventory.setItem(guiConfig.getItemSlot("time-controls.teleport"), teleportItem);
        }
    }

    private ItemStack createControlItem(String path) {
        String materialName = guiConfig.getConfig().getString(path + ".material", "PAPER");
        Material material = Material.getMaterial(materialName);
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfig().getString(path + ".name", "");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = guiConfig.getConfig().getStringList(path + ".lore");
            List<String> processedLore = lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(processedLore);

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSpeedItem() {
        String materialName = guiConfig.getConfig().getString("controls.speed.material", "FEATHER");
        Material material = Material.getMaterial(materialName);
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String speedValue = String.valueOf(replayPlayer.getPlaybackSpeed());
            String name = guiConfig.getConfig().getString("controls.speed.name", "&bHız: %speed%x");
            name = name.replace("%speed%", speedValue);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = guiConfig.getConfig().getStringList("controls.speed.lore");
            if (!lore.isEmpty()) {
                List<String> processedLore = lore.stream()
                        .map(line -> line.replace("%speed%", speedValue))
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        String materialName = guiConfig.getConfig().getString("controls.info.material", "BOOK");
        Material material = Material.getMaterial(materialName);
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = guiConfig.getConfig().getString("controls.info.name", "&6Replay Info");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = guiConfig.getConfig().getStringList("controls.info.lore");
            if (!lore.isEmpty()) {
                String playerName = replayPlayer.getReplay().getRecordedPlayer();
                String duration = formatDuration(replayPlayer.getTotalTime());
                String progress = String.format("%.1f", replayPlayer.getProgress());
                String current = String.valueOf(replayPlayer.getCurrentActionIndex());
                String total = String.valueOf(replayPlayer.getTotalActions());

                List<String> processedLore = lore.stream()
                        .map(line -> line
                                .replace("%player%", playerName)
                                .replace("%duration%", duration)
                                .replace("%progress%", progress)
                                .replace("%current%", current)
                                .replace("%total%", total)
                        )
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public ReplayPlayer getReplayPlayer() {
        return replayPlayer;
    }
}
