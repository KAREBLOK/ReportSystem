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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Statistics GUI - Shows reviewer statistics
 */
public class OverwatchStatsGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player viewer;
    private final UUID targetUUID;
    private final Inventory inventory;

    public OverwatchStatsGUI(ReportSystemSpigot plugin, Player viewer, UUID targetUUID) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUUID = targetUUID;

        // Get stats to show player name
        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(targetUUID);
        String playerName = statsOpt.map(OverwatchStats::getReviewerName).orElse("Unknown");

        String title = plugin.getMessageManager().getMessage("overwatch.gui.stats.title")
                .replace("%player%", playerName);
        this.inventory = Bukkit.createInventory(this, 45, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Fill with glass panes
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, grayGlass);
        }

        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(targetUUID);

        if (!statsOpt.isPresent()) {
            // No stats found
            List<String> noStatsLore = new ArrayList<>();
            String noStats1 = plugin.getMessageManager().getMessage("overwatch.gui.stats.no-stats-lore1");
            noStatsLore.add(plugin.getMessageManager().colorize(noStats1));

            String noStats2 = plugin.getMessageManager().getMessage("overwatch.gui.stats.no-stats-lore2");
            noStatsLore.add(plugin.getMessageManager().colorize(noStats2));

            String noStatsName = plugin.getMessageManager().getMessage("overwatch.gui.stats.no-stats-name");
            inventory.setItem(22, createItem(Material.BARRIER, plugin.getMessageManager().colorize(noStatsName), noStatsLore));

            // Back button
            String backName = plugin.getMessageManager().getMessage("overwatch.gui.stats.back");
            inventory.setItem(40, createItem(Material.ARROW, plugin.getMessageManager().colorize(backName), null));
            return;
        }

        OverwatchStats stats = statsOpt.get();

        // Player Head / Rank (Slot 4)
        List<String> rankLore = new ArrayList<>();
        String rankMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.rank-lore")
                .replace("%rank_color%", getRankColor(stats.getRank()))
                .replace("%rank%", stats.getRank());
        rankLore.add(plugin.getMessageManager().colorize(rankMsg));

        String levelMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.level-lore")
                .replace("%level%", String.valueOf(stats.getLevel()));
        rankLore.add(plugin.getMessageManager().colorize(levelMsg));

        String xpMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-lore")
                .replace("%xp%", String.valueOf(stats.getXp()))
                .replace("%next_xp%", String.valueOf(getNextLevelXP(stats.getLevel())));
        rankLore.add(plugin.getMessageManager().colorize(xpMsg));
        rankLore.add("");
        rankLore.add(getProgressBar(stats.getXp(), getNextLevelXP(stats.getLevel())));

        String playerName = plugin.getMessageManager().getMessage("overwatch.gui.stats.player-name")
                .replace("%player%", stats.getReviewerName());
        inventory.setItem(4, createItem(Material.PLAYER_HEAD, plugin.getMessageManager().colorize(playerName), rankLore));

        // Total Reviews (Slot 20)
        List<String> totalLore = new ArrayList<>();
        String totalDesc1 = plugin.getMessageManager().getMessage("overwatch.gui.stats.total-desc1");
        totalLore.add(plugin.getMessageManager().colorize(totalDesc1));

        String totalDesc2 = plugin.getMessageManager().getMessage("overwatch.gui.stats.total-desc2");
        totalLore.add(plugin.getMessageManager().colorize(totalDesc2));
        totalLore.add("");

        String totalCount = plugin.getMessageManager().getMessage("overwatch.gui.stats.total-count")
                .replace("%count%", String.valueOf(stats.getTotalReviews()));
        totalLore.add(plugin.getMessageManager().colorize(totalCount));

        String totalName = plugin.getMessageManager().getMessage("overwatch.gui.stats.total-name");
        inventory.setItem(20, createItem(Material.PAPER, plugin.getMessageManager().colorize(totalName), totalLore));

        // Guilty Verdicts (Slot 21)
        List<String> guiltyLore = new ArrayList<>();
        String guiltyDesc1 = plugin.getMessageManager().getMessage("overwatch.gui.stats.guilty-desc1");
        guiltyLore.add(plugin.getMessageManager().colorize(guiltyDesc1));

        String guiltyDesc2 = plugin.getMessageManager().getMessage("overwatch.gui.stats.guilty-desc2");
        guiltyLore.add(plugin.getMessageManager().colorize(guiltyDesc2));
        guiltyLore.add("");

        String guiltyCount = plugin.getMessageManager().getMessage("overwatch.gui.stats.guilty-count")
                .replace("%count%", String.valueOf(stats.getGuiltyVerdicts()));
        guiltyLore.add(plugin.getMessageManager().colorize(guiltyCount));

        double guiltyPercent = stats.getTotalReviews() > 0
            ? (stats.getGuiltyVerdicts() * 100.0 / stats.getTotalReviews())
            : 0;
        String guiltyPercMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.guilty-percent")
                .replace("%percent%", String.format("%.1f", guiltyPercent));
        guiltyLore.add(plugin.getMessageManager().colorize(guiltyPercMsg));

        String guiltyName = plugin.getMessageManager().getMessage("overwatch.gui.stats.guilty-name");
        inventory.setItem(21, createItem(Material.RED_CONCRETE, plugin.getMessageManager().colorize(guiltyName), guiltyLore));

        // Innocent Verdicts (Slot 22)
        List<String> innocentLore = new ArrayList<>();
        String innocentDesc1 = plugin.getMessageManager().getMessage("overwatch.gui.stats.innocent-desc1");
        innocentLore.add(plugin.getMessageManager().colorize(innocentDesc1));

        String innocentDesc2 = plugin.getMessageManager().getMessage("overwatch.gui.stats.innocent-desc2");
        innocentLore.add(plugin.getMessageManager().colorize(innocentDesc2));
        innocentLore.add("");

        String innocentCount = plugin.getMessageManager().getMessage("overwatch.gui.stats.innocent-count")
                .replace("%count%", String.valueOf(stats.getInnocentVerdicts()));
        innocentLore.add(plugin.getMessageManager().colorize(innocentCount));

        double innocentPercent = stats.getTotalReviews() > 0
            ? (stats.getInnocentVerdicts() * 100.0 / stats.getTotalReviews())
            : 0;
        String innocentPercMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.innocent-percent")
                .replace("%percent%", String.format("%.1f", innocentPercent));
        innocentLore.add(plugin.getMessageManager().colorize(innocentPercMsg));

        String innocentName = plugin.getMessageManager().getMessage("overwatch.gui.stats.innocent-name");
        inventory.setItem(22, createItem(Material.GREEN_CONCRETE, plugin.getMessageManager().colorize(innocentName), innocentLore));

        // Skipped (Slot 23)
        List<String> skipLore = new ArrayList<>();
        String skipDesc = plugin.getMessageManager().getMessage("overwatch.gui.stats.skip-desc");
        skipLore.add(plugin.getMessageManager().colorize(skipDesc));
        skipLore.add("");

        String skipCount = plugin.getMessageManager().getMessage("overwatch.gui.stats.skip-count")
                .replace("%count%", String.valueOf(stats.getSkippedVerdicts()));
        skipLore.add(plugin.getMessageManager().colorize(skipCount));

        double skipPercent = stats.getTotalReviews() > 0
            ? (stats.getSkippedVerdicts() * 100.0 / stats.getTotalReviews())
            : 0;
        String skipPercMsg = plugin.getMessageManager().getMessage("overwatch.gui.stats.skip-percent")
                .replace("%percent%", String.format("%.1f", skipPercent));
        skipLore.add(plugin.getMessageManager().colorize(skipPercMsg));

        String skipName = plugin.getMessageManager().getMessage("overwatch.gui.stats.skip-name");
        inventory.setItem(23, createItem(Material.YELLOW_CONCRETE, plugin.getMessageManager().colorize(skipName), skipLore));

        // Accuracy (Slot 24)
        List<String> accuracyLore = new ArrayList<>();
        String accDesc1 = plugin.getMessageManager().getMessage("overwatch.gui.stats.accuracy-desc1");
        accuracyLore.add(plugin.getMessageManager().colorize(accDesc1));

        String accDesc2 = plugin.getMessageManager().getMessage("overwatch.gui.stats.accuracy-desc2");
        accuracyLore.add(plugin.getMessageManager().colorize(accDesc2));
        accuracyLore.add("");

        String accValue = plugin.getMessageManager().getMessage("overwatch.gui.stats.accuracy-value")
                .replace("%accuracy%", String.format("%.1f", stats.getAccuracy()));
        accuracyLore.add(plugin.getMessageManager().colorize(accValue));

        String accName = plugin.getMessageManager().getMessage("overwatch.gui.stats.accuracy-name");
        inventory.setItem(24, createItem(Material.GOLDEN_APPLE, plugin.getMessageManager().colorize(accName), accuracyLore));

        // Review Time (Slot 30)
        List<String> timeLore = new ArrayList<>();
        String timeDesc = plugin.getMessageManager().getMessage("overwatch.gui.stats.time-desc");
        timeLore.add(plugin.getMessageManager().colorize(timeDesc));
        timeLore.add("");

        int hours = stats.getTotalReviewTime() / 3600;
        int minutes = (stats.getTotalReviewTime() % 3600) / 60;
        int seconds = stats.getTotalReviewTime() % 60;
        String timeTotal = plugin.getMessageManager().getMessage("overwatch.gui.stats.time-total")
                .replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%seconds%", String.valueOf(seconds));
        timeLore.add(plugin.getMessageManager().colorize(timeTotal));

        if (stats.getTotalReviews() > 0) {
            int avgSeconds = stats.getTotalReviewTime() / stats.getTotalReviews();
            timeLore.add("");
            String timeAvg = plugin.getMessageManager().getMessage("overwatch.gui.stats.time-average")
                    .replace("%avg_seconds%", String.valueOf(avgSeconds));
            timeLore.add(plugin.getMessageManager().colorize(timeAvg));
        }

        String timeName = plugin.getMessageManager().getMessage("overwatch.gui.stats.time-name");
        inventory.setItem(30, createItem(Material.CLOCK, plugin.getMessageManager().colorize(timeName), timeLore));

        // First/Last Review (Slot 31)
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        List<String> dateLore = new ArrayList<>();
        String dateFirst = plugin.getMessageManager().getMessage("overwatch.gui.stats.dates-first");
        dateLore.add(plugin.getMessageManager().colorize(dateFirst));

        String dateFirstValue = plugin.getMessageManager().getMessage("overwatch.gui.stats.dates-first-value")
                .replace("%date%", sdf.format(new Date(stats.getFirstReviewAt())));
        dateLore.add(plugin.getMessageManager().colorize(dateFirstValue));
        dateLore.add("");

        String dateLast = plugin.getMessageManager().getMessage("overwatch.gui.stats.dates-last");
        dateLore.add(plugin.getMessageManager().colorize(dateLast));

        String dateLastValue = plugin.getMessageManager().getMessage("overwatch.gui.stats.dates-last-value")
                .replace("%date%", sdf.format(new Date(stats.getLastReviewAt())));
        dateLore.add(plugin.getMessageManager().colorize(dateLastValue));

        String datesName = plugin.getMessageManager().getMessage("overwatch.gui.stats.dates-name");
        inventory.setItem(31, createItem(Material.PAPER, plugin.getMessageManager().colorize(datesName), dateLore));

        // XP Breakdown (Slot 32)
        List<String> xpLore = new ArrayList<>();
        String xpDesc = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-desc");
        xpLore.add(plugin.getMessageManager().colorize(xpDesc));
        xpLore.add("");

        String xpGuilty = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-guilty");
        xpLore.add(plugin.getMessageManager().colorize(xpGuilty));

        String xpSkip = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-skip");
        xpLore.add(plugin.getMessageManager().colorize(xpSkip));
        xpLore.add("");

        String xpNext = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-next");
        xpLore.add(plugin.getMessageManager().colorize(xpNext));

        int needed = getNextLevelXP(stats.getLevel()) - stats.getXp();
        String xpNeeded = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-needed")
                .replace("%xp%", String.valueOf(needed));
        xpLore.add(plugin.getMessageManager().colorize(xpNeeded));

        String xpName = plugin.getMessageManager().getMessage("overwatch.gui.stats.xp-name");
        inventory.setItem(32, createItem(Material.EXPERIENCE_BOTTLE, plugin.getMessageManager().colorize(xpName), xpLore));

        // Rank Info (Slot 33)
        List<String> rankInfoLore = new ArrayList<>();
        String rankSystem = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-system");
        rankInfoLore.add(plugin.getMessageManager().colorize(rankSystem));
        rankInfoLore.add("");

        String rankDiamond = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-diamond");
        rankInfoLore.add((stats.getXp() >= 2500 ? "§b§l✔ " : "§8  ") + plugin.getMessageManager().colorize(rankDiamond));

        String rankGold = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-gold");
        rankInfoLore.add((stats.getXp() >= 1000 && stats.getXp() < 2500 ? "§6§l✔ " : "§8  ") + plugin.getMessageManager().colorize(rankGold));

        String rankSilver = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-silver");
        rankInfoLore.add((stats.getXp() >= 500 && stats.getXp() < 1000 ? "§7§l✔ " : "§8  ") + plugin.getMessageManager().colorize(rankSilver));

        String rankBronze = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-bronze");
        rankInfoLore.add((stats.getXp() < 500 ? "§c§l✔ " : "§8  ") + plugin.getMessageManager().colorize(rankBronze));

        String ranksName = plugin.getMessageManager().getMessage("overwatch.gui.stats.ranks-name");
        inventory.setItem(33, createItem(Material.DIAMOND, plugin.getMessageManager().colorize(ranksName), rankInfoLore));

        // Back Button (Slot 40)
        String backName = plugin.getMessageManager().getMessage("overwatch.gui.stats.back");
        inventory.setItem(40, createItem(Material.ARROW, plugin.getMessageManager().colorize(backName), null));
    }

    private int getNextLevelXP(int level) {
        return (level + 1) * 100;
    }

    private String getProgressBar(int current, int max) {
        int bars = 20;
        int filled = (int) ((current / (double) max) * bars);

        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                sb.append("§a§l|");
            } else {
                sb.append("§8§l|");
            }
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
        if (slot == 40) { // Back
            viewer.closeInventory();
            new OverwatchMenuGUI(plugin, viewer).open();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
