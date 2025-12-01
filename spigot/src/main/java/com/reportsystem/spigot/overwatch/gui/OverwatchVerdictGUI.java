package com.reportsystem.spigot.overwatch.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdict GUI - Opens after replay ends for reviewer to vote
 */
public class OverwatchVerdictGUI implements InventoryHolder {

    private final ReportSystemSpigot plugin;
    private final Player player;
    private final int reportId;
    private final long reviewStartTime;
    private final Inventory inventory;

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

        String title = plugin.getMessageManager().getMessage("overwatch.gui.verdict.title");
        this.inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager().colorize(title));

        setupMainMenu();
    }

    private void setupMainMenu() {
        inventory.clear();
        state = VerdictState.MAIN_MENU;

        // Fill with glass panes
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, grayGlass);
        }

        // Report Info (Top row)
        Report report = plugin.getReportService().getReportById(reportId);
        if (report != null) {
            List<String> reportLore = new ArrayList<>();
            reportLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.report-info.lore-reported")
                    .replace("%reported%", report.getReportedPlayerName()));
            reportLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.report-info.lore-reporter")
                    .replace("%reporter%", report.getReporterName()));
            reportLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.report-info.lore-reason")
                    .replace("%reason%", report.getReason()));
            reportLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.report-info.lore-server")
                    .replace("%server%", report.getServerName()));

            String infoName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.report-info.name")
                    .replace("%id%", String.valueOf(reportId));
            inventory.setItem(4, createItem(Material.PAPER, infoName, reportLore));
        }

        // GUILTY Button (Slot 20)
        List<String> guiltyLore = new ArrayList<>();
        guiltyLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.guilty.lore-desc1"));
        guiltyLore.add("");
        guiltyLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.guilty.lore-desc2"));
        guiltyLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.guilty.lore-desc3"));
        guiltyLore.add("");
        guiltyLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.guilty.lore-click"));

        String guiltyName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.guilty.name");
        inventory.setItem(20, createItem(Material.RED_CONCRETE, guiltyName, guiltyLore));

        // INNOCENT Button (Slot 22)
        List<String> innocentLore = new ArrayList<>();
        innocentLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.innocent.lore-desc1"));
        innocentLore.add("");
        innocentLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.innocent.lore-desc2"));
        innocentLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.innocent.lore-desc3"));
        innocentLore.add("");
        innocentLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.innocent.lore-click"));

        String innocentName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.innocent.name");
        inventory.setItem(22, createItem(Material.GREEN_CONCRETE, innocentName, innocentLore));

        // SKIP Button (Slot 24)
        List<String> skipLore = new ArrayList<>();
        skipLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.lore-desc1"));
        skipLore.add("");
        skipLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.lore-desc2"));
        skipLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.lore-desc3"));
        skipLore.add("");
        skipLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.lore-note"));
        skipLore.add("");
        skipLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.lore-click"));

        String skipName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.skip.name");
        inventory.setItem(24, createItem(Material.YELLOW_CONCRETE, skipName, skipLore));

        // Back to NPC Button (Slot 49)
        String backName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.back.name");
        List<String> backLore = new ArrayList<>();
        backLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.back.lore"));
        inventory.setItem(49, createItem(Material.BARRIER, backName, backLore));
    }

    private void setupCategorySelection() {
        inventory.clear();
        state = VerdictState.CATEGORY_SELECTION;

        // Fill with glass panes
        ItemStack redGlass = createItem(Material.RED_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, redGlass);
        }

        // Title
        String categoryTitle = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-title");
        inventory.setItem(4, createItem(Material.PAPER, categoryTitle, null));

        // Combat Hacks (Slot 20)
        List<String> combatLore = new ArrayList<>();
        combatLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-combat.lore-desc1"));
        combatLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-combat.lore-desc2"));

        String combatName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-combat.name");
        inventory.setItem(20, createItem(Material.DIAMOND_SWORD, combatName, combatLore));

        // Movement Hacks (Slot 22)
        List<String> movementLore = new ArrayList<>();
        movementLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-movement.lore-desc1"));
        movementLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-movement.lore-desc2"));

        String movementName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-movement.name");
        inventory.setItem(22, createItem(Material.FEATHER, movementName, movementLore));

        // Visual Hacks (Slot 24)
        List<String> visualLore = new ArrayList<>();
        visualLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-visual.lore-desc1"));
        visualLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-visual.lore-desc2"));

        String visualName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-visual.name");
        inventory.setItem(24, createItem(Material.ENDER_EYE, visualName, visualLore));

        // Other (Slot 26)
        List<String> otherLore = new ArrayList<>();
        otherLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-other.lore-desc1"));
        otherLore.add(plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-other.lore-desc2"));

        String otherName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-other.name");
        inventory.setItem(26, createItem(Material.BARRIER, otherName, otherLore));

        // Back Button (Slot 49)
        String backName = plugin.getMessageManager().getMessage("overwatch.gui.verdict.category-back.name");
        inventory.setItem(49, createItem(Material.ARROW, backName, null));
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

            case 49: // Back to NPC
                player.closeInventory();
                new OverwatchMenuGUI(plugin, player).open();
                break;
        }
    }

    private void handleCategoryClick(int slot) {
        String category = null;
        String subcategory = null;

        switch (slot) {
            case 20: // Combat
                category = "COMBAT";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.combat");
                break;
            case 22: // Movement
                category = "MOVEMENT";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.movement");
                break;
            case 24: // Visual
                category = "VISUAL";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.visual");
                break;
            case 26: // Other
                category = "OTHER";
                subcategory = plugin.getMessageManager().getMessage("overwatch.verdict.categories.other");
                break;
            case 49: // Back
                setupMainMenu();
                return;
        }

        if (category != null) {
            // Use localized subcategory, fallback to category if message not found
            if (subcategory == null || subcategory.startsWith("overwatch.verdict.categories.")) {
                subcategory = category;
            }
            submitVerdict(selectedVerdict, category, subcategory);
        }
    }

    private void submitVerdict(String verdict, String category, String subcategory) {
        // Mark as submitted (important for close event handling)
        verdictSubmitted = true;

        // Calculate review duration
        int reviewDuration = (int) ((System.currentTimeMillis() - reviewStartTime) / 1000);

        // Submit to manager
        plugin.getOverwatchManager().submitVerdict(
            player,
            reportId,
            verdict,
            category,
            subcategory,
            reviewDuration
        );

        // Close GUI
        player.closeInventory();

        // Send feedback
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.header")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.title")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.header")));
        player.sendMessage("");

        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.report-id")
                        .replace("%id%", String.valueOf(reportId))));

        // Get localized verdict name
        String verdictDisplay = getLocalizedVerdict(verdict);
        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.your-verdict")
                        .replace("%verdict%", verdictDisplay)));

        if (!"NONE".equals(category)) {
            // Get localized category name
            String categoryDisplay = getLocalizedCategory(category);
            player.sendMessage(plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.verdict.category")
                            .replace("%category%", categoryDisplay)));
        }

        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.review-time")
                        .replace("%time%", String.valueOf(reviewDuration))));
        player.sendMessage("");

        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.thank-you")));
        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.footer")));

        // Play sound
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Get localized verdict display name (includes color codes from messages)
     */
    private String getLocalizedVerdict(String verdict) {
        String key = "overwatch.verdict.types." + verdict.toLowerCase();
        String localized = plugin.getMessageManager().getMessage(key);

        // If message not found, fallback to uppercase verdict
        if (localized == null || localized.equals(key)) {
            return verdict.toUpperCase();
        }

        return localized;
    }

    /**
     * Get localized category display name
     */
    private String getLocalizedCategory(String category) {
        String key = "overwatch.verdict.categories." + category.toLowerCase();
        String localized = plugin.getMessageManager().getMessage(key);

        // If message not found, fallback to category name
        if (localized == null || localized.equals(key)) {
            return category;
        }

        return localized;
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
