package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.GUIConfig;
import com.reportsystem.spigot.overwatch.OverwatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main Overwatch Menu GUI - DeluxeMenu inspired clean design
 * Layout (45 slots / 5 rows):
 *   Row 1: .... HEAD ....         (slot 4 = player head)
 *   Row 2: .BBBBBBB.              (slots 10-16 = black glass separator)
 *   Row 3: .. REVIEW . STATS . BOARD ..  (slots 20, 22, 24)
 *   Row 4: .... TUTORIAL ....     (slot 31)
 *   Row 5: .... BACK ....         (slot 40)
 */
public class OverwatchMenuGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player player;
    private final Inventory inventory;
    private final FileConfiguration cfg;

    public OverwatchMenuGUI(ReportSystemSpigot plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.cfg = new GUIConfig(plugin, "overwatch-menu").getConfig();

        String title = cfg.getString("title", "&8&nOverwatch");
        this.inventory = Bukkit.createInventory(this, 45, plugin.getMessageManager().colorize(title));

        setupItems();
    }

    private void setupItems() {
        // Black glass separator (row 2: slots 10-16)
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, blackGlass);
        }

        // Player Head (Slot 4) - overview
        Optional<OverwatchStats> statsOpt = plugin.getOverwatchManager().getReviewerStats(player.getUniqueId());
        List<String> headLore = new ArrayList<>();
        headLore.add("");

        if (statsOpt.isPresent()) {
            OverwatchStats stats = statsOpt.get();
            headLore.add(cfg.getString("player-head.lore-level", "").replace("%level%", String.valueOf(stats.getLevel())));
            headLore.add(cfg.getString("player-head.lore-rank", "")
                    .replace("%rank_color%", getRankColor(stats.getRank()))
                    .replace("%rank%", stats.getRank()));
            headLore.add(cfg.getString("player-head.lore-xp", "").replace("%xp%", String.valueOf(stats.getXp())));
            headLore.add(cfg.getString("player-head.lore-total", "").replace("%total%", String.valueOf(stats.getTotalReviews())));
        } else {
            headLore.add(cfg.getString("stats.lore-no-stats1", ""));
        }
        headLore.add("");

        String headName = cfg.getString("player-head.name", "&b%player%").replace("%player%", player.getName());
        inventory.setItem(4, createItem(Material.PLAYER_HEAD, headName, headLore));

        // Start Reviewing (Slot 20)
        List<String> reviewLore = new ArrayList<>();
        reviewLore.add("");
        reviewLore.add(cfg.getString("start-review.lore-desc1", ""));
        reviewLore.add(cfg.getString("start-review.lore-desc2", ""));
        reviewLore.add("");
        int pendingQueue = plugin.getOverwatchManager().getPendingQueueCount();
        reviewLore.add(cfg.getString("start-review.lore-pending", "").replace("%count%", String.valueOf(pendingQueue)));
        reviewLore.add("");
        reviewLore.add(cfg.getString("start-review.lore-click", ""));

        inventory.setItem(20, createItem(Material.ENDER_PEARL, cfg.getString("start-review.name", ""), reviewLore));

        // My Statistics (Slot 22)
        List<String> statsLore = new ArrayList<>();
        statsLore.add("");
        if (statsOpt.isPresent()) {
            OverwatchStats stats = statsOpt.get();
            statsLore.add(cfg.getString("stats.lore-level", "").replace("%level%", String.valueOf(stats.getLevel())));
            statsLore.add(cfg.getString("stats.lore-rank", "")
                    .replace("%rank_color%", getRankColor(stats.getRank()))
                    .replace("%rank%", stats.getRank()));
            statsLore.add(cfg.getString("stats.lore-xp", "").replace("%xp%", String.valueOf(stats.getXp())));
            statsLore.add("");
            statsLore.add(cfg.getString("stats.lore-total", "").replace("%total%", String.valueOf(stats.getTotalReviews())));
            statsLore.add(cfg.getString("stats.lore-guilty", "").replace("%guilty%", String.valueOf(stats.getGuiltyVerdicts())));
            statsLore.add(cfg.getString("stats.lore-innocent", "").replace("%innocent%", String.valueOf(stats.getInnocentVerdicts())));
            statsLore.add(cfg.getString("stats.lore-skip", "").replace("%skip%", String.valueOf(stats.getSkippedVerdicts())));
        } else {
            statsLore.add(cfg.getString("stats.lore-no-stats1", ""));
            statsLore.add("");
            statsLore.add(cfg.getString("stats.lore-no-stats2", ""));
            statsLore.add(cfg.getString("stats.lore-no-stats3", ""));
        }
        statsLore.add("");
        statsLore.add(cfg.getString("stats.lore-click", ""));

        inventory.setItem(22, createItem(Material.BOOK, cfg.getString("stats.name", ""), statsLore));

        // Leaderboard (Slot 24)
        List<String> leaderboardLore = new ArrayList<>();
        leaderboardLore.add("");
        leaderboardLore.add(cfg.getString("leaderboard.lore-desc1", ""));
        leaderboardLore.add(cfg.getString("leaderboard.lore-desc2", ""));
        leaderboardLore.add("");
        leaderboardLore.add(cfg.getString("leaderboard.lore-click", ""));

        inventory.setItem(24, createItem(Material.GOLDEN_APPLE, cfg.getString("leaderboard.name", ""), leaderboardLore));

        // How it Works (Slot 31)
        List<String> tutorialLore = new ArrayList<>();
        tutorialLore.add("");
        tutorialLore.add(cfg.getString("tutorial.lore-header", ""));
        tutorialLore.add("");
        tutorialLore.add(cfg.getString("tutorial.lore-step1", ""));
        tutorialLore.add(cfg.getString("tutorial.lore-step2", ""));
        tutorialLore.add(cfg.getString("tutorial.lore-step3", ""));
        tutorialLore.add(cfg.getString("tutorial.lore-step4", ""));
        tutorialLore.add("");
        tutorialLore.add(cfg.getString("tutorial.lore-click", ""));

        inventory.setItem(31, createItem(Material.KNOWLEDGE_BOOK, cfg.getString("tutorial.name", ""), tutorialLore));

        // Back Button (Slot 40)
        inventory.setItem(40, createItem(Material.ARROW, cfg.getString("back", "&8←"), null));
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
            case 20: // Start Reviewing
                startReviewing();
                break;
            case 22: // My Statistics
                new OverwatchStatsGUI(plugin, player, player.getUniqueId()).open();
                break;
            case 24: // Leaderboard
                new OverwatchLeaderboardGUI(plugin, player).open();
                break;
            case 31: // Tutorial
                showTutorial();
                break;
            case 40: // Back
                player.closeInventory();
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

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getReplayManager().startReplay(reportId, player).thenAccept(started -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (started) {
                            plugin.getOverwatchReplayListener().startReview(player, reportId);
                        } else {
                            String failedMsg = plugin.getMessageManager().getMessage("overwatch.review.replay-failed");
                            player.sendMessage(plugin.getMessageManager().colorize(failedMsg));
                        }
                    });
                });
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
