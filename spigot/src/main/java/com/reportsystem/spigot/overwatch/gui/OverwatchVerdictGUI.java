package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.Report;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Verdict GUI - DeluxeMenu inspired clean design
 * Main Menu Layout (54 slots / 6 rows):
 *   Row 1: .... REPORT_INFO ....    (slot 4 = report info)
 *   Row 2: .BBBBBBB.               (slots 10-16 = black glass separator)
 *   Row 3: .. GUILTY . INNOCENT . SKIP ..  (slots 20, 22, 24)
 *   Row 4-5: .........              (empty)
 *   Row 6: .... BACK ....           (slot 49 = back)
 *
 * Category Selection Layout:
 *   Row 1: .... TITLE ....          (slot 4 = category title)
 *   Row 2: .RRRRRRR.               (slots 10-16 = red glass separator)
 *   Row 3: . COMBAT . MOVE . VISUAL . OTHER .  (slots 19, 21, 23, 25)
 *   Row 4-5: .........              (empty)
 *   Row 6: .... BACK ....           (slot 49 = back)
 */
public class OverwatchVerdictGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player player;
    private final int reportId;
    private final long reviewStartTime;
    private final Inventory inventory;
    private final FileConfiguration cfg;

    private VerdictState state = VerdictState.MAIN_MENU;
    private String selectedVerdict = null;
    private boolean verdictSubmitted = false;

    public enum VerdictState {
        MAIN_MENU,
        CATEGORY_SELECTION
    }

    public OverwatchVerdictGUI(ReportSystemSpigot plugin, Player player, int reportId, long reviewStartTime) {
        this.plugin = plugin;
        this.player = player;
        this.reportId = reportId;
        this.reviewStartTime = reviewStartTime;
        this.cfg = new GUIConfig(plugin, "overwatch-verdict").getConfig();

        String title = cfg.getString("title", "&8&nOverwatch - Karar Ver");
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager().colorize(title));

        setupMainMenu();
    }

    private void setupMainMenu() {
        inventory.clear();
        state = VerdictState.MAIN_MENU;

        // Black glass separator (row 2: slots 10-16)
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, blackGlass);
        }

        // Report Info (Slot 4)
        Report report = plugin.getReportService().getReportById(reportId);
        if (report != null) {
            List<String> reportLore = new ArrayList<>();
            reportLore.add("");
            reportLore.add(cfg.getString("report-info.lore-reported", "").replace("%reported%", report.getReportedPlayerName()));
            reportLore.add(cfg.getString("report-info.lore-reporter", "").replace("%reporter%", report.getReporterName()));
            reportLore.add(cfg.getString("report-info.lore-reason", "").replace("%reason%", report.getReason()));
            reportLore.add(cfg.getString("report-info.lore-server", "").replace("%server%", report.getServerName()));
            reportLore.add("");

            inventory.setItem(4, createItem(Material.PAPER,
                    cfg.getString("report-info.name", "").replace("%id%", String.valueOf(reportId)), reportLore));
        }

        // GUILTY Button (Slot 20)
        List<String> guiltyLore = new ArrayList<>();
        guiltyLore.add("");
        guiltyLore.add(cfg.getString("guilty.lore-desc1", ""));
        guiltyLore.add("");
        guiltyLore.add(cfg.getString("guilty.lore-desc2", ""));
        guiltyLore.add(cfg.getString("guilty.lore-desc3", ""));
        guiltyLore.add("");
        guiltyLore.add(cfg.getString("guilty.lore-click", ""));
        inventory.setItem(20, createItem(Material.RED_CONCRETE, cfg.getString("guilty.name", ""), guiltyLore));

        // INNOCENT Button (Slot 22)
        List<String> innocentLore = new ArrayList<>();
        innocentLore.add("");
        innocentLore.add(cfg.getString("innocent.lore-desc1", ""));
        innocentLore.add("");
        innocentLore.add(cfg.getString("innocent.lore-desc2", ""));
        innocentLore.add(cfg.getString("innocent.lore-desc3", ""));
        innocentLore.add("");
        innocentLore.add(cfg.getString("innocent.lore-click", ""));
        inventory.setItem(22, createItem(Material.GREEN_CONCRETE, cfg.getString("innocent.name", ""), innocentLore));

        // SKIP Button (Slot 24)
        List<String> skipLore = new ArrayList<>();
        skipLore.add("");
        skipLore.add(cfg.getString("skip.lore-desc1", ""));
        skipLore.add("");
        skipLore.add(cfg.getString("skip.lore-desc2", ""));
        skipLore.add(cfg.getString("skip.lore-desc3", ""));
        skipLore.add("");
        skipLore.add(cfg.getString("skip.lore-note", ""));
        skipLore.add("");
        skipLore.add(cfg.getString("skip.lore-click", ""));
        inventory.setItem(24, createItem(Material.YELLOW_CONCRETE, cfg.getString("skip.name", ""), skipLore));

        // Back Button (Slot 49)
        List<String> backLore = new ArrayList<>();
        backLore.add(cfg.getString("back.lore", ""));
        inventory.setItem(49, createItem(Material.ARROW, cfg.getString("back.name", "&8←"), backLore));
    }

    private void setupCategorySelection() {
        inventory.clear();
        state = VerdictState.CATEGORY_SELECTION;

        // Red glass separator (row 2: slots 10-16)
        ItemStack redGlass = createItem(Material.RED_STAINED_GLASS_PANE, "&7", null);
        for (int i = 10; i <= 16; i++) {
            inventory.setItem(i, redGlass);
        }

        // Title (Slot 4)
        inventory.setItem(4, createItem(Material.PAPER, cfg.getString("category-title", ""), null));

        // Combat (Slot 19)
        List<String> combatLore = new ArrayList<>();
        combatLore.add("");
        combatLore.add(cfg.getString("category-combat.lore-desc1", ""));
        combatLore.add(cfg.getString("category-combat.lore-desc2", ""));
        combatLore.add("");
        inventory.setItem(19, createItem(Material.DIAMOND_SWORD, cfg.getString("category-combat.name", ""), combatLore));

        // Movement (Slot 21)
        List<String> movementLore = new ArrayList<>();
        movementLore.add("");
        movementLore.add(cfg.getString("category-movement.lore-desc1", ""));
        movementLore.add(cfg.getString("category-movement.lore-desc2", ""));
        movementLore.add("");
        inventory.setItem(21, createItem(Material.FEATHER, cfg.getString("category-movement.name", ""), movementLore));

        // Visual (Slot 23)
        List<String> visualLore = new ArrayList<>();
        visualLore.add("");
        visualLore.add(cfg.getString("category-visual.lore-desc1", ""));
        visualLore.add(cfg.getString("category-visual.lore-desc2", ""));
        visualLore.add("");
        inventory.setItem(23, createItem(Material.ENDER_EYE, cfg.getString("category-visual.name", ""), visualLore));

        // Other (Slot 25)
        List<String> otherLore = new ArrayList<>();
        otherLore.add("");
        otherLore.add(cfg.getString("category-other.lore-desc1", ""));
        otherLore.add(cfg.getString("category-other.lore-desc2", ""));
        otherLore.add("");
        inventory.setItem(25, createItem(Material.BARRIER, cfg.getString("category-other.name", ""), otherLore));

        // Back Button (Slot 49)
        inventory.setItem(49, createItem(Material.ARROW, cfg.getString("category-back.name", "&8←"), null));
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

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (state == VerdictState.MAIN_MENU) {
            handleMainMenuClick(slot);
        } else if (state == VerdictState.CATEGORY_SELECTION) {
            handleCategoryClick(slot);
        }
    }

    private void handleMainMenuClick(int slot) {
        switch (slot) {
            case 20: // GUILTY
                selectedVerdict = "GUILTY";
                setupCategorySelection();
                break;
            case 22: // INNOCENT
                submitVerdict("INNOCENT", "NONE", "NONE");
                break;
            case 24: // SKIP
                submitVerdict("SKIP", "NONE", "NONE");
                break;
            case 49: // Back
                player.closeInventory();
                new OverwatchMenuGUI(plugin, player).open();
                break;
        }
    }

    private void handleCategoryClick(int slot) {
        String category = null;
        String subcategory = null;

        switch (slot) {
            case 19: // Combat
                category = "COMBAT";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.combat");
                break;
            case 21: // Movement
                category = "MOVEMENT";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.movement");
                break;
            case 23: // Visual
                category = "VISUAL";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.visual");
                break;
            case 25: // Other
                category = "OTHER";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.other");
                break;
            case 49: // Back
                setupMainMenu();
                return;
        }

        if (category != null) {
            if (subcategory == null || subcategory.startsWith("overwatch.verdict.categories.")) {
                subcategory = category;
            }
            submitVerdict(selectedVerdict, category, subcategory);
        }
    }

    private void submitVerdict(String verdict, String category, String subcategory) {
        verdictSubmitted = true;
        int reviewDuration = (int) ((System.currentTimeMillis() - reviewStartTime) / 1000);

        plugin.getOverwatchManager().submitVerdict(player, reportId, verdict, category, subcategory, reviewDuration);

        player.closeInventory();

        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.header")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.title")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.header")));
        player.sendMessage("");

        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.report-id").replace("%id%", String.valueOf(reportId))));

        String verdictDisplay = getLocalizedVerdict(verdict);
        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.your-verdict").replace("%verdict%", verdictDisplay)));

        if (!"NONE".equals(category)) {
            String categoryDisplay = getLocalizedCategory(category);
            player.sendMessage(plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.verdict.category").replace("%category%", categoryDisplay)));
        }

        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.review-time").replace("%time%", String.valueOf(reviewDuration))));
        player.sendMessage("");

        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.thank-you")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.footer")));

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (plugin.getOverwatchReplayListener() != null
                        && plugin.getOverwatchReplayListener().hasSavedState(player.getUniqueId())) {
                    plugin.getOverwatchReplayListener().restoreAndReturn(player);
                } else {
                    plugin.getReplayManager().restoreViewerLocation(player);
                }
            }
        }, 40L);
    }

    private String getLocalizedVerdict(String verdict) {
        String key = "overwatch.verdict.types." + verdict.toLowerCase();
        String localized = plugin.getMessageManager().getMessage(key);
        return (localized == null || localized.equals(key)) ? verdict.toUpperCase() : localized;
    }

    private String getLocalizedCategory(String category) {
        String key = "overwatch.verdict.categories." + category.toLowerCase();
        String localized = plugin.getMessageManager().getMessage(key);
        return (localized == null || localized.equals(key)) ? category : localized;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public boolean isVerdictSubmitted() {
        return verdictSubmitted;
    }

    public int getReportId() {
        return reportId;
    }
}
