package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.OverwatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main Overwatch Menu GUI - Opens when player clicks NPC
 */
public class OverwatchMenuGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player player;
    private final Inventory inventory;

    public OverwatchMenuGUI(ReportSystemSpigot plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        String title = plugin.getMessageManager().getMessage("overwatch.gui.menu.title");
        this.inventory = Bukkit.createInventory(this, 27, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Fill with glass panes
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, grayGlass);
        }

        // Start Reviewing (Slot 11)
        List<String> reviewLore = new ArrayList<>();
        reviewLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.start-review.lore-desc1"));
        reviewLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.start-review.lore-desc2"));
        reviewLore.add("");
        int pendingQueue = plugin.getOverwatchManager().getPendingQueueCount();
        String pendingMsg = plugin.getMessageManager().getMessage("overwatch.gui.menu.start-review.lore-pending")
                .replace("%count%", String.valueOf(pendingQueue));
        reviewLore.add(pendingMsg);
        reviewLore.add("");
        reviewLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.start-review.lore-click"));

        String reviewName = plugin.getMessageManager().getMessage("overwatch.gui.menu.start-review.name");
        inventory.setItem(11, createItem(Material.ENDER_PEARL, reviewName, reviewLore));

        // My Statistics (Slot 13)
        List<String> statsLore = new ArrayList<>();
        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(player.getUniqueId());

        if (statsOpt.isPresent()) {
            OverwatchStats stats = statsOpt.get();

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-level")
                    .replace("%level%", String.valueOf(stats.getLevel())));

            String rankMsg = plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-rank")
                    .replace("%rank_color%", getRankColor(stats.getRank()))
                    .replace("%rank%", stats.getRank());
            statsLore.add(rankMsg);

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-xp")
                    .replace("%xp%", String.valueOf(stats.getXp())));
            statsLore.add("");

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-total")
                    .replace("%total%", String.valueOf(stats.getTotalReviews())));

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-guilty")
                    .replace("%guilty%", String.valueOf(stats.getGuiltyVerdicts())));

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-innocent")
                    .replace("%innocent%", String.valueOf(stats.getInnocentVerdicts())));

            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-skip")
                    .replace("%skip%", String.valueOf(stats.getSkippedVerdicts())));
        } else {
            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-no-stats1"));
            statsLore.add("");
            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-no-stats2"));
            statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-no-stats3"));
        }
        statsLore.add("");
        statsLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.lore-click"));

        String statsName = plugin.getMessageManager().getMessage("overwatch.gui.menu.stats.name");
        inventory.setItem(13, createItem(Material.PLAYER_HEAD, statsName, statsLore));

        // Leaderboard (Slot 15)
        List<String> leaderboardLore = new ArrayList<>();
        leaderboardLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.leaderboard.lore-desc1"));
        leaderboardLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.leaderboard.lore-desc2"));
        leaderboardLore.add("");
        leaderboardLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.leaderboard.lore-click"));

        String leaderboardName = plugin.getMessageManager().getMessage("overwatch.gui.menu.leaderboard.name");
        inventory.setItem(15, createItem(Material.GOLDEN_APPLE, leaderboardName, leaderboardLore));

        // How it Works (Slot 22)
        List<String> tutorialLore = new ArrayList<>();
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-header"));
        tutorialLore.add("");
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-step1"));
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-step2"));
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-step3"));
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-step4"));
        tutorialLore.add("");
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-consensus-header"));
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-consensus-desc1"));
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-consensus-desc2"));
        tutorialLore.add("");
        tutorialLore.add(plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.lore-click"));

        String tutorialName = plugin.getMessageManager().getMessage("overwatch.gui.menu.tutorial.name");
        inventory.setItem(22, createItem(Material.BOOK, tutorialName, tutorialLore));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getMessageManager().colorize(name));
            if (lore != null) {
                List<String> colorizedLore = new ArrayList<>();
                for (String line : lore) {
                    colorizedLore.add(plugin.getMessageManager().colorize(line));
                }
                meta.setLore(colorizedLore);
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

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        switch (slot) {
            case 11: // Start Reviewing
                startReviewing();
                break;
            case 13: // My Statistics
                new OverwatchStatsGUI(plugin, player, player.getUniqueId()).open();
                break;
            case 15: // Leaderboard
                new OverwatchLeaderboardGUI(plugin, player).open();
                break;
            case 22: // Tutorial
                showTutorial();
                break;
        }
    }

    private void startReviewing() {
        player.closeInventory();

        Optional<Integer> reportIdOpt = plugin.getOverwatchManager().getNextReportForReviewer(player);

        if (reportIdOpt.isPresent()) {
            int reportId = reportIdOpt.get();

            String loadingMsg = plugin.getMessageManager().getMessage("overwatch.review.loading")
                    .replace("%id%", String.valueOf(reportId));
            player.sendMessage(plugin.getMessageManager().colorize(loadingMsg));

            // Start replay in 1 second (give player time to prepare)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                boolean started = plugin.getReplayManager().startReplay(reportId, player);

                if (started) {
                    // Register this as an Overwatch review session
                    plugin.getOverwatchReplayListener().startReview(player, reportId);
                } else {
                    String failedMsg = plugin.getMessageManager().getMessage("overwatch.review.replay-failed");
                    player.sendMessage(plugin.getMessageManager().colorize(failedMsg));
                }
            }, 20L);

        } else {
            String noReportsMsg = plugin.getMessageManager().getMessage("overwatch.review.no-reports");
            String tryLaterMsg = plugin.getMessageManager().getMessage("overwatch.review.try-later");
            player.sendMessage(plugin.getMessageManager().colorize(noReportsMsg));
            player.sendMessage(plugin.getMessageManager().colorize(tryLaterMsg));
        }
    }

    private void showTutorial() {
        player.closeInventory();

        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.header")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.title")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.header")));
        player.sendMessage("");
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step1")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step2")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step3")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step4")));
        player.sendMessage(plugin.getMessageManager().colorize("   " + plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step4-guilty")));
        player.sendMessage(plugin.getMessageManager().colorize("   " + plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step4-innocent")));
        player.sendMessage(plugin.getMessageManager().colorize("   " + plugin.getMessageManager().getMessage("overwatch.commands.tutorial.step4-skip")));
        player.sendMessage("");
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.rewards-header")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.rewards-review")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.rewards-level")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.rewards-leaderboard")));
        player.sendMessage("");
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.consensus-header")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.consensus-desc1")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.consensus-desc2")));
        player.sendMessage("");
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.important")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.commands.tutorial.footer")));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
