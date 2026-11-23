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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** "İncelemeye başla" çift tık kilidi: sorgu async olduğu için aynı oyuncu iki rapor atamasın [THR-09]. */
    private static final Set<UUID> STARTING = ConcurrentHashMap.newKeySet();

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
        loadAsync();
    }

    /** Ana thread: yalnız DB gerektirmeyen öğeler. Slot 4/20/22 {@link #loadAsync()} ile dolar [THR-01]. */
    private void setupItems() {
        // Black glass separator (row 2: slots 10-16)
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, blackGlass);
        }

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

    /**
     * İstatistik + bekleyen kuyruk sayısını async okur, ana thread'de slot 4/20/22'yi doldurur
     * [THR-01][THR-17]. Envanter açıkken yerinde güncellenir.
     */
    private void loadAsync() {
        if (!plugin.isEnabled()) return;
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<OverwatchStats> statsOpt;
            int pendingQueue;
            try {
                statsOpt = plugin.getOverwatchManager().getReviewerStats(uuid);
                pendingQueue = plugin.getOverwatchManager().getPendingQueueCount();
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[OVERWATCH] Failed to load menu data", t);
                statsOpt = Optional.empty();
                pendingQueue = 0;
            }
            if (!plugin.isEnabled()) return; // kapanıyor: runTask atılamaz [THR-17]
            final Optional<OverwatchStats> finalStats = statsOpt;
            final int finalPending = pendingQueue;
            Bukkit.getScheduler().runTask(plugin, () -> fill(finalStats, finalPending));
        });
    }

    /** Ana thread: DB'li slotları (4, 20, 22) doldurur. */
    private void fill(Optional<OverwatchStats> statsOpt, int pendingQueue) {
        // Player Head (Slot 4) - overview
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
        switch (rank.toUpperCase(java.util.Locale.ROOT)) {
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
        if (plugin.getReplayManager().isWatchingReplay(player) ||
                (plugin.getOverwatchReplayListener() != null && plugin.getOverwatchReplayListener().isReviewing(player.getUniqueId()))) {
            player.closeInventory();
            player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("replay.already-watching")));
            return;
        }

        final UUID uuid = player.getUniqueId();
        if (!STARTING.add(uuid)) {
            return; // zaten bir "başla" isteği sürüyor
        }

        player.closeInventory();

        // Kuyruk sorgusu ağır olabilir: DB ana thread'de çalışmaz [THR-01]
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Integer> reportIdOpt;
            try {
                reportIdOpt = plugin.getOverwatchManager().getNextReportForReviewer(player);
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] Failed to get next report", t);
                reportIdOpt = Optional.empty();
            }

            if (!plugin.isEnabled()) {
                STARTING.remove(uuid);
                return; // kapanıyor: runTask atılamaz [THR-17]
            }

            final Optional<Integer> result = reportIdOpt;
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean handedOff = false;
                try {
                    handedOff = handleNextReport(result, uuid);
                } finally {
                    // Replay akışı devraldıysa kilidi o bırakır (startReplay sonucu belli olunca)
                    if (!handedOff) STARTING.remove(uuid);
                }
            });
        });
    }

    /**
     * Ana thread: async sorgunun sonucunu işler.
     *
     * @return true ise {@code STARTING} kilidini replay akışı (startReplay sonucu) bırakacak;
     *         false ise çağıran hemen bırakır.
     */
    private boolean handleNextReport(Optional<Integer> reportIdOpt, UUID uuid) {
        if (reportIdOpt.isEmpty()) {
            if (player.isOnline()) {
                String noReportsMsg = plugin.getMessageManager().getMessage("overwatch.review.no-reports");
                String tryLaterMsg = plugin.getMessageManager().getMessage("overwatch.review.try-later");
                player.sendMessage(plugin.getMessageManager().colorize(noReportsMsg));
                player.sendMessage(plugin.getMessageManager().colorize(tryLaterMsg));
            }
            return false;
        }

        int reportId = reportIdOpt.get();

        if (!player.isOnline()) {
            // Rapor atandı ama oyuncu çıktı: raporu kuyruğa geri ver (DB async)
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> plugin.getOverwatchManager().unassignReport(reportId));
            return false;
        }

        String loadingMsg = plugin.getMessageManager().getMessage("overwatch.review.loading")
                .replace("%id%", String.valueOf(reportId));
        player.sendMessage(plugin.getMessageManager().colorize(loadingMsg));

        // Inceleme kaydini ac: burada yalniz BASLANGIC KONUMU kesinlesir. Gercek envanter/durum
        // ve PDC cokme yedegi, envanterin gercekten temizlendigi tick'te
        // (ReplayControlManager.giveControlItems -> captureIfPending) alinir. Burada alinsaydi
        // aradaki korumasiz pencerede (20 tick + async yukleme + dunya degisimi) sandiga konan
        // esyalar inceleme sonunda geri yazilip cogalirdi.
        plugin.getOverwatchReplayListener().savePlayerState(player);

        // Buradan sonra kilidi replay akisi birakir: basari, basarisizlik, istisna ya da
        // oyuncu cikisi — HER yolda finally ile (ikinci "basla" ilk incelemenin yedegini silmesin).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.getReplayManager().startReplay(reportId, player).whenComplete((started, error) -> {
                    if (!plugin.isEnabled()) {
                        STARTING.remove(uuid);
                        return; // kapanıyor: runTask atılamaz [THR-17]
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (error == null && Boolean.TRUE.equals(started)) {
                                plugin.getOverwatchReplayListener().startReview(player, reportId);
                            } else {
                                if (error != null) {
                                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                                            "[OVERWATCH] startReplay failed for report #" + reportId, error);
                                }
                                // Replay hic baslamadi: GERI YUKLEME degil sadece kaydi dusur ve raporu
                                // serbest birak. (Arada baska bir replay envanteri temizlediyse
                                // dropSavedState PDC yedegini silmez, o replay'e devreder.)
                                plugin.getOverwatchReplayListener().dropSavedState(uuid);
                                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                                        () -> plugin.getOverwatchManager().unassignReport(reportId));

                                if (player.isOnline()) {
                                    String failedMsg = plugin.getMessageManager().getMessage("overwatch.review.replay-failed");
                                    player.sendMessage(plugin.getMessageManager().colorize(failedMsg));
                                }
                            }
                        } finally {
                            STARTING.remove(uuid);
                        }
                    });
                });
            } catch (Throwable t) {
                STARTING.remove(uuid);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[OVERWATCH] startReplay threw", t);
                plugin.getOverwatchReplayListener().dropSavedState(uuid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> plugin.getOverwatchManager().unassignReport(reportId));
            }
        }, 20L);
        return true;
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
