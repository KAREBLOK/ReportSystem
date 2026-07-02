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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Statistics GUI - DeluxeMenu inspired clean design
 * Layout (54 slots / 6 rows):
 *   Row 1: .... HEAD ....              (slot 4 = player head + rank)
 *   Row 2: .BBBBBBB.                   (slots 10-16 = black glass separator)
 *   Row 3: . TOTAL GUILTY INNO SKIP ACC .  (slots 20-24 = verdict stats)
 *   Row 4: . TIME DATES XP RANKS .     (slots 29-33 = detail stats)
 *   Row 5: .........                    (empty)
 *   Row 6: .... BACK ....              (slot 49 = back)
 */
public class OverwatchStatsGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player viewer;
    private final UUID targetUUID;
    private final Inventory inventory;
    private final FileConfiguration cfg;

    public OverwatchStatsGUI(ReportSystemSpigot plugin, Player viewer, UUID targetUUID) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUUID = targetUUID;
        this.cfg = new GUIConfig(plugin, "overwatch-stats").getConfig();

        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(targetUUID);
        String playerName = statsOpt.map(OverwatchStats::getReviewerName).orElse("Unknown");

        String title = cfg.getString("title", "&8&n%player%").replace("%player%", playerName);
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Black glass separator (row 2: slots 10-16)
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, blackGlass);
        }

        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(targetUUID);

        if (!statsOpt.isPresent()) {
            List<String> noStatsLore = new ArrayList<>();
            noStatsLore.add("");
            noStatsLore.add(plugin.getMessageManager().colorize(cfg.getString("no-stats-lore1", "")));
            noStatsLore.add(plugin.getMessageManager().colorize(cfg.getString("no-stats-lore2", "")));
            noStatsLore.add("");

            inventory.setItem(22, createItem(Material.BARRIER,
                    plugin.getMessageManager().colorize(cfg.getString("no-stats-name", "")), noStatsLore));
            inventory.setItem(49, createItem(Material.ARROW,
                    plugin.getMessageManager().colorize(cfg.getString("back", "&8←")), null));
            return;
        }

        OverwatchStats stats = statsOpt.get();

        // Player Head / Rank (Slot 4)
        List<String> rankLore = new ArrayList<>();
        rankLore.add("");
        rankLore.add(c(cfg.getString("rank-lore", "")
                .replace("%rank_color%", getRankColor(stats.getRank()))
                .replace("%rank%", stats.getRank())));
        rankLore.add(c(cfg.getString("level-lore", "").replace("%level%", String.valueOf(stats.getLevel()))));
        rankLore.add(c(cfg.getString("xp-lore", "")
                .replace("%xp%", String.valueOf(stats.getXp()))
                .replace("%next_xp%", String.valueOf(getNextLevelXP(stats.getLevel())))));
        rankLore.add("");
        rankLore.add(getProgressBar(stats.getXp(), getNextLevelXP(stats.getLevel())));
        rankLore.add("");

        inventory.setItem(4, createItem(Material.PLAYER_HEAD,
                c(cfg.getString("player-name", "").replace("%player%", stats.getReviewerName())), rankLore));

        // Total Reviews (Slot 20)
        List<String> totalLore = new ArrayList<>();
        totalLore.add("");
        totalLore.add(c(cfg.getString("total-desc1", "")));
        totalLore.add(c(cfg.getString("total-desc2", "")));
        totalLore.add("");
        totalLore.add(c(cfg.getString("total-count", "").replace("%count%", String.valueOf(stats.getTotalReviews()))));
        totalLore.add("");
        inventory.setItem(20, createItem(Material.PAPER, c(cfg.getString("total-name", "")), totalLore));

        // Guilty Verdicts (Slot 21)
        List<String> guiltyLore = new ArrayList<>();
        guiltyLore.add("");
        guiltyLore.add(c(cfg.getString("guilty-desc1", "")));
        guiltyLore.add(c(cfg.getString("guilty-desc2", "")));
        guiltyLore.add("");
        guiltyLore.add(c(cfg.getString("guilty-count", "").replace("%count%", String.valueOf(stats.getGuiltyVerdicts()))));
        double guiltyPercent = stats.getTotalReviews() > 0 ? (stats.getGuiltyVerdicts() * 100.0 / stats.getTotalReviews()) : 0;
        guiltyLore.add(c(cfg.getString("guilty-percent", "").replace("%percent%", String.format("%.1f", guiltyPercent))));
        guiltyLore.add("");
        inventory.setItem(21, createItem(Material.RED_CONCRETE, c(cfg.getString("guilty-name", "")), guiltyLore));

        // Innocent Verdicts (Slot 22)
        List<String> innocentLore = new ArrayList<>();
        innocentLore.add("");
        innocentLore.add(c(cfg.getString("innocent-desc1", "")));
        innocentLore.add(c(cfg.getString("innocent-desc2", "")));
        innocentLore.add("");
        innocentLore.add(c(cfg.getString("innocent-count", "").replace("%count%", String.valueOf(stats.getInnocentVerdicts()))));
        double innocentPercent = stats.getTotalReviews() > 0 ? (stats.getInnocentVerdicts() * 100.0 / stats.getTotalReviews()) : 0;
        innocentLore.add(c(cfg.getString("innocent-percent", "").replace("%percent%", String.format("%.1f", innocentPercent))));
        innocentLore.add("");
        inventory.setItem(22, createItem(Material.GREEN_CONCRETE, c(cfg.getString("innocent-name", "")), innocentLore));

        // Skipped (Slot 23)
        List<String> skipLore = new ArrayList<>();
        skipLore.add("");
        skipLore.add(c(cfg.getString("skip-desc", "")));
        skipLore.add("");
        skipLore.add(c(cfg.getString("skip-count", "").replace("%count%", String.valueOf(stats.getSkippedVerdicts()))));
        double skipPercent = stats.getTotalReviews() > 0 ? (stats.getSkippedVerdicts() * 100.0 / stats.getTotalReviews()) : 0;
        skipLore.add(c(cfg.getString("skip-percent", "").replace("%percent%", String.format("%.1f", skipPercent))));
        skipLore.add("");
        inventory.setItem(23, createItem(Material.YELLOW_CONCRETE, c(cfg.getString("skip-name", "")), skipLore));

        // Accuracy (Slot 24)
        List<String> accuracyLore = new ArrayList<>();
        accuracyLore.add("");
        accuracyLore.add(c(cfg.getString("accuracy-desc1", "")));
        accuracyLore.add(c(cfg.getString("accuracy-desc2", "")));
        accuracyLore.add("");

        if (stats.isAccuracyVisible()) {
            accuracyLore.add(c(cfg.getString("accuracy-value", "").replace("%accuracy%", String.format("%.1f", stats.getAccuracy()))));
            accuracyLore.add(c(cfg.getString("accuracy-weight", "&7Vote Weight: &f%weight%x")
                    .replace("%weight%", String.format("%.1f", stats.getVoteWeight()))));
        } else {
            int remaining = 50 - (stats.getTotalReviews() - stats.getSkippedVerdicts());
            accuracyLore.add(c(cfg.getString("accuracy-hidden", "&8Visible after %remaining% more reviews")
                    .replace("%remaining%", String.valueOf(Math.max(0, remaining)))));
        }
        accuracyLore.add("");
        inventory.setItem(24, createItem(Material.GOLDEN_APPLE, c(cfg.getString("accuracy-name", "")), accuracyLore));

        // Review Time (Slot 29)
        List<String> timeLore = new ArrayList<>();
        timeLore.add("");
        timeLore.add(c(cfg.getString("time-desc", "")));
        timeLore.add("");
        int hours = stats.getTotalReviewTime() / 3600;
        int minutes = (stats.getTotalReviewTime() % 3600) / 60;
        int seconds = stats.getTotalReviewTime() % 60;
        timeLore.add(c(cfg.getString("time-total", "").replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes)).replace("%seconds%", String.valueOf(seconds))));
        if (stats.getTotalReviews() > 0) {
            int avgSeconds = stats.getTotalReviewTime() / stats.getTotalReviews();
            timeLore.add(c(cfg.getString("time-average", "").replace("%avg_seconds%", String.valueOf(avgSeconds))));
        }
        timeLore.add("");
        inventory.setItem(29, createItem(Material.CLOCK, c(cfg.getString("time-name", "")), timeLore));

        // First/Last Review (Slot 31)
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        List<String> dateLore = new ArrayList<>();
        dateLore.add("");
        dateLore.add(c(cfg.getString("dates-first", "")));
        dateLore.add(c(cfg.getString("dates-first-value", "").replace("%date%", sdf.format(new Date(stats.getFirstReviewAt())))));
        dateLore.add("");
        dateLore.add(c(cfg.getString("dates-last", "")));
        dateLore.add(c(cfg.getString("dates-last-value", "").replace("%date%", sdf.format(new Date(stats.getLastReviewAt())))));
        dateLore.add("");
        inventory.setItem(31, createItem(Material.PAPER, c(cfg.getString("dates-name", "")), dateLore));

        // XP Breakdown (Slot 33)
        List<String> xpLore = new ArrayList<>();
        xpLore.add("");
        xpLore.add(c(cfg.getString("xp-desc", "")));
        xpLore.add("");
        xpLore.add(c(cfg.getString("xp-guilty", "")));
        xpLore.add(c(cfg.getString("xp-skip", "")));
        xpLore.add("");
        xpLore.add(c(cfg.getString("xp-next", "")));
        int needed = getNextLevelXP(stats.getLevel()) - stats.getXp();
        xpLore.add(c(cfg.getString("xp-needed", "").replace("%xp%", String.valueOf(needed))));
        xpLore.add("");
        inventory.setItem(33, createItem(Material.EXPERIENCE_BOTTLE, c(cfg.getString("xp-name", "")), xpLore));

        // Back Button (Slot 49)
        inventory.setItem(49, createItem(Material.ARROW, c(cfg.getString("back", "&8←")), null));
    }

    private String c(String text) {
        return plugin.getMessageManager().colorize(text);
    }

    private int getNextLevelXP(int level) {
        return (level + 1) * 100;
    }

    private String getProgressBar(int current, int max) {
        int bars = 20;
        int filled = (int) ((current / (double) max) * bars);

        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "§a§l|" : "§8§l|");
        }
        sb.append("§7] §e").append(String.format("%.1f", (current / (double) max) * 100)).append("%");
        return sb.toString();
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
