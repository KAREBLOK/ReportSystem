package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.GUIConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboard GUI - DeluxeMenu inspired clean design
 * Layout (54 slots / 6 rows):
 *   Row 1: .........                (empty)
 *   Row 2: .BBBBBBB.               (slots 10-16 = black glass separator)
 *   Row 3: . P P P P P P P .       (slots 19-25 = top 7)
 *   Row 4: . . P P P . . . .       (slots 29-31 = remaining 3)
 *   Row 5: . . . . INFO . . . .    (slot 40 = info)
 *   Row 6: . . . . BACK . . . .    (slot 49 = back)
 */
public class OverwatchLeaderboardGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final FileConfiguration cfg;

    public OverwatchLeaderboardGUI(ReportSystemSpigot plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.cfg = new GUIConfig(plugin, "overwatch-leaderboard").getConfig();

        String title = cfg.getString("title", "&8&nLiderlik Tablosu");
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Black glass separator (row 2: slots 10-16)
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, blackGlass);
        }

        List<OverwatchStats> topReviewers = plugin.getOverwatchManager().getTopReviewers(10);

        if (topReviewers.isEmpty()) {
            List<String> emptyLore = new ArrayList<>();
            emptyLore.add("");
            emptyLore.add(plugin.getMessageManager().colorize(cfg.getString("empty-lore", "")));
            emptyLore.add("");

            inventory.setItem(22, createItem(Material.BARRIER,
                    plugin.getMessageManager().colorize(cfg.getString("empty-name", "")), emptyLore));
        } else {
            // Row 3: slots 19-25 (top 7), Row 4: slots 29-31 (remaining 3)
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31};

            for (int i = 0; i < Math.min(topReviewers.size(), 10); i++) {
                inventory.setItem(slots[i], createLeaderboardItem(i + 1, topReviewers.get(i)));
            }
        }

        // Info Item (Slot 40)
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(plugin.getMessageManager().colorize(cfg.getString("info-lore1", "")));
        infoLore.add(plugin.getMessageManager().colorize(cfg.getString("info-lore2", "")));
        infoLore.add("");
        infoLore.add(plugin.getMessageManager().colorize(cfg.getString("info-sorting", "")));
        infoLore.add("");

        inventory.setItem(40, createItem(Material.BOOK,
                plugin.getMessageManager().colorize(cfg.getString("info-name", "")), infoLore));

        // Back Button (Slot 49)
        inventory.setItem(49, createItem(Material.ARROW,
                plugin.getMessageManager().colorize(cfg.getString("back", "&8←")), null));
    }

    private ItemStack createLeaderboardItem(int rank, OverwatchStats stats) {
        String rankPrefix;
        switch (rank) {
            case 1: rankPrefix = c(cfg.getString("rank-1", "")); break;
            case 2: rankPrefix = c(cfg.getString("rank-2", "")); break;
            case 3: rankPrefix = c(cfg.getString("rank-3", "")); break;
            default: rankPrefix = c(cfg.getString("rank-n", "").replace("%rank%", String.valueOf(rank))); break;
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(rankPrefix + stats.getReviewerName());

            List<String> lore = new ArrayList<>();
            lore.add(c(cfg.getString("separator", "")));
            lore.add("");
            lore.add(c(cfg.getString("rank", "").replace("%rank_color%", getRankColor(stats.getRank())).replace("%rank%", stats.getRank())));
            lore.add(c(cfg.getString("level", "").replace("%level%", String.valueOf(stats.getLevel())).replace("%xp%", String.valueOf(stats.getXp()))));
            lore.add("");
            lore.add(c(cfg.getString("total", "").replace("%total%", String.valueOf(stats.getTotalReviews()))));
            lore.add(c(cfg.getString("guilty", "").replace("%guilty%", String.valueOf(stats.getGuiltyVerdicts()))));
            lore.add(c(cfg.getString("innocent", "").replace("%innocent%", String.valueOf(stats.getInnocentVerdicts()))));
            lore.add(c(cfg.getString("skip", "").replace("%skip%", String.valueOf(stats.getSkippedVerdicts()))));
            lore.add("");
            lore.add(c(cfg.getString("accuracy", "").replace("%accuracy%", String.format("%.1f", stats.getAccuracy()))));

            int hours = stats.getTotalReviewTime() / 3600;
            int minutes = (stats.getTotalReviewTime() % 3600) / 60;
            lore.add(c(cfg.getString("time", "").replace("%hours%", String.valueOf(hours)).replace("%minutes%", String.valueOf(minutes))));

            if (stats.getTotalReviews() > 0) {
                int avgSeconds = stats.getTotalReviewTime() / stats.getTotalReviews();
                lore.add(c(cfg.getString("average", "").replace("%avg_seconds%", String.valueOf(avgSeconds))));
            }

            lore.add("");
            lore.add(c(cfg.getString("separator", "")));

            // Special badges for top 3
            if (rank == 1) lore.add(c(cfg.getString("badge-1", "")));
            else if (rank == 2) lore.add(c(cfg.getString("badge-2", "")));
            else if (rank == 3) lore.add(c(cfg.getString("badge-3", "")));

            meta.setLore(lore);

            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwner(stats.getReviewerName());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private String c(String text) {
        return plugin.getMessageManager().colorize(text);
    }

    private String getRankColor(String rank) {
        switch (rank.toUpperCase()) {
            case "DIAMOND": return "§b";
            case "GOLD": return "§6";
            case "SILVER": return "§7";
            default: return "§c";
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot == 49) {
            viewer.closeInventory();
            new OverwatchMenuGUI(plugin, viewer).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
