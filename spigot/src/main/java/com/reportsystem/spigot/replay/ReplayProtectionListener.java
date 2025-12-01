package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Projectile;

/**
 * Prevents replay viewers from interacting with the world
 * - No item pickup
 * - No item drop
 * - No block break
 * - No block place
 * - No inventory interaction (except control items)
 */
public class ReplayProtectionListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReplayProtectionListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent item pickup while watching replay
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Check if player is watching a replay
        if (plugin.getReplayManager().isWatchingReplay(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent item drop while watching replay
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.getReplayManager().isWatchingReplay(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent block break while watching replay
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (plugin.getReplayManager().isWatchingReplay(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent block place while watching replay
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (plugin.getReplayManager().isWatchingReplay(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent inventory clicks while watching replay (except for control items)
     * Control items are handled by ReplayControlManager
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (plugin.getReplayManager().isWatchingReplay(player)) {
            // Cancel all inventory interactions
            // Control items are handled in ReplayControlManager with HIGHEST priority
            event.setCancelled(true);
        }
    }

    /**
     * Prevent world interactions while watching replay (except control items)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (plugin.getReplayManager().isWatchingReplay(player)) {
            // Don't cancel AIR interactions (allow control item clicks)
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent replay projectiles from dealing damage
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if damager is a replay projectile
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
                event.setCancelled(true);
                event.setDamage(0);
            }
        }
    }

    /**
     * Cancel replay projectile hit effects
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
            event.setCancelled(true);
            // Remove the projectile immediately
            projectile.remove();
        }
    }
}
