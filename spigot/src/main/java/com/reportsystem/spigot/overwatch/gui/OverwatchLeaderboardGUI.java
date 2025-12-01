package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboard GUI - Shows top reviewers
 */
public class OverwatchLeaderboardGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player viewer;
    private final Inventory inventory;

    public OverwatchLeaderboardGUI(ReportSystemSpigot plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;

        String title = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.title");
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Fill with glass panes
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, grayGlass);
        }

        // Get top 10 reviewers
        List<OverwatchStats> topReviewers = plugin.getOverwatchManager().getTopReviewers(10);

        if (topReviewers.isEmpty()) {
            List<String> emptyLore = new ArrayList<>();
            String emptyMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.empty-lore");
            emptyLore.add(plugin.getMessageManager().colorize(emptyMsg));

            String emptyName = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.empty-name");
            inventory.setItem(22, createItem(Material.BARRIER, plugin.getMessageManager().colorize(emptyName), emptyLore));
        } else {
            // Display top reviewers
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21}; // First 10 slots for top 10

            for (int i = 0; i < Math.min(topReviewers.size(), 10); i++) {
                OverwatchStats stats = topReviewers.get(i);
                ItemStack item = createLeaderboardItem(i + 1, stats);
                inventory.setItem(slots[i], item);
            }
        }

        // Info Item (Slot 31)
        List<String> infoLore = new ArrayList<>();
        String info1 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.info-lore1");
        infoLore.add(plugin.getMessageManager().colorize(info1));

        String info2 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.info-lore2");
        infoLore.add(plugin.getMessageManager().colorize(info2));
        infoLore.add("");

        String info3 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.info-sorting");
        infoLore.add(plugin.getMessageManager().colorize(info3));

        String infoName = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.info-name");
        inventory.setItem(31, createItem(Material.BOOK, plugin.getMessageManager().colorize(infoName), infoLore));

        // Back Button (Slot 49)
        String backName = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.back");
        inventory.setItem(49, createItem(Material.ARROW, plugin.getMessageManager().colorize(backName), null));
    }

    private ItemStack createLeaderboardItem(int rank, OverwatchStats stats) {
        String rankPrefix;

        // Rank prefix for all players
        switch (rank) {
            case 1:
                String rank1 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.rank-1");
                rankPrefix = plugin.getMessageManager().colorize(rank1);
                break;
            case 2:
                String rank2 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.rank-2");
                rankPrefix = plugin.getMessageManager().colorize(rank2);
                break;
            case 3:
                String rank3 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.rank-3");
                rankPrefix = plugin.getMessageManager().colorize(rank3);
                break;
            default:
                String rankN = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.rank-n")
                        .replace("%rank%", String.valueOf(rank));
                rankPrefix = plugin.getMessageManager().colorize(rankN);
                break;
        }

        // Always use player head for all ranks
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(rankPrefix + stats.getReviewerName());

            List<String> lore = new ArrayList<>();
            String separator = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.separator");
            lore.add(plugin.getMessageManager().colorize(separator));

            String rankMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.rank")
                    .replace("%rank_color%", getRankColor(stats.getRank()))
                    .replace("%rank%", stats.getRank());
            lore.add(plugin.getMessageManager().colorize(rankMsg));

            String levelMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.level")
                    .replace("%level%", String.valueOf(stats.getLevel()))
                    .replace("%xp%", String.valueOf(stats.getXp()));
            lore.add(plugin.getMessageManager().colorize(levelMsg));
            lore.add("");

            String statsTitle = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.stats-title");
            lore.add(plugin.getMessageManager().colorize(statsTitle));

            String totalMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.total")
                    .replace("%total%", String.valueOf(stats.getTotalReviews()));
            lore.add(plugin.getMessageManager().colorize(totalMsg));

            String guiltyMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.guilty")
                    .replace("%guilty%", String.valueOf(stats.getGuiltyVerdicts()));
            lore.add(plugin.getMessageManager().colorize(guiltyMsg));

            String innocentMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.innocent")
                    .replace("%innocent%", String.valueOf(stats.getInnocentVerdicts()));
            lore.add(plugin.getMessageManager().colorize(innocentMsg));

            String skipMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.skip")
                    .replace("%skip%", String.valueOf(stats.getSkippedVerdicts()));
            lore.add(plugin.getMessageManager().colorize(skipMsg));
            lore.add("");

            String accuracyMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.accuracy")
                    .replace("%accuracy%", String.format("%.1f", stats.getAccuracy()));
            lore.add(plugin.getMessageManager().colorize(accuracyMsg));

            // Review time
            int hours = stats.getTotalReviewTime() / 3600;
            int minutes = (stats.getTotalReviewTime() % 3600) / 60;
            String timeMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.time")
                    .replace("%hours%", String.valueOf(hours))
                    .replace("%minutes%", String.valueOf(minutes));
            lore.add(plugin.getMessageManager().colorize(timeMsg));

            if (stats.getTotalReviews() > 0) {
                int avgSeconds = stats.getTotalReviewTime() / stats.getTotalReviews();
                String avgMsg = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.average")
                        .replace("%avg_seconds%", String.valueOf(avgSeconds));
                lore.add(plugin.getMessageManager().colorize(avgMsg));
            }

            lore.add(plugin.getMessageManager().colorize(separator));

            // Special badges for top 3
            if (rank == 1) {
                String badge1 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.badge-1");
                lore.add(plugin.getMessageManager().colorize(badge1));
            } else if (rank == 2) {
                String badge2 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.badge-2");
                lore.add(plugin.getMessageManager().colorize(badge2));
            } else if (rank == 3) {
                String badge3 = plugin.getMessageManager().getMessage("overwatch.gui.leaderboard.badge-3");
                lore.add(plugin.getMessageManager().colorize(badge3));
            }

            meta.setLore(lore);

            // For player heads, try to set the owner
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwner(stats.getReviewerName());
            }

            item.setItemMeta(meta);
        }

        return item;
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
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot == 49) { // Back
            viewer.closeInventory();
            new OverwatchMenuGUI(plugin, viewer).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
