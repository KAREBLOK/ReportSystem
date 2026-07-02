package com.reportsystem.spigot.overwatch.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener for Overwatch GUI clicks
 */
public class OverwatchGUIListener implements Listener {

    private final ReportSystemSpigot plugin;

    public OverwatchGUIListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getInventory().getHolder();

        // Check if it's an Overwatch GUI
        if (holder instanceof OverwatchMenuGUI) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
                return;
            }
            ((OverwatchMenuGUI) holder).handleClick(event.getSlot());

        } else if (holder instanceof OverwatchVerdictGUI) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
                return;
            }
            ((OverwatchVerdictGUI) holder).handleClick(event.getSlot());

        } else if (holder instanceof OverwatchStatsGUI) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
                return;
            }
            ((OverwatchStatsGUI) holder).handleClick(event.getSlot());

        } else if (holder instanceof OverwatchLeaderboardGUI) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) {
                return;
            }
            ((OverwatchLeaderboardGUI) holder).handleClick(event.getSlot());
        }
    }

    /**
     * Handle inventory close - unassign report if verdict GUI closed without submitting
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();

        // Check if OverwatchVerdictGUI was closed without submitting
        if (holder instanceof OverwatchVerdictGUI) {
            OverwatchVerdictGUI verdictGUI = (OverwatchVerdictGUI) holder;

            if (!verdictGUI.isVerdictSubmitted()) {
                // Verdict GUI submit edilmeden kapatildi
                if (plugin.getOverwatchReplayListener() != null
                        && plugin.getOverwatchReplayListener().isReviewing(player.getUniqueId())) {
                    // Post-replay itemlerine geri don (review iptal etme)
                    plugin.debug("[OVERWATCH] Player " + player.getName() +
                        " closed verdict GUI without submitting - returning to post-replay");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            plugin.getOverwatchReplayListener().returnToPostReplay(player);
                        }
                    }, 1L);
                } else {
                    // Eski davranis - raporu birak
                    int reportId = verdictGUI.getReportId();
                    plugin.debug("[OVERWATCH] Player " + player.getName() +
                        " closed verdict GUI without submitting - unassigning report #" + reportId);
                    plugin.getOverwatchManager().unassignReport(reportId);
                    player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("overwatch.verdict.cancelled")));
                }
            }
        }
    }
}
