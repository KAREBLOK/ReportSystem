package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Projectile;

/**
 * Prevents replay viewers from interacting with the world
 * and protects them from all damage sources
 */
public class ReplayProtectionListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReplayProtectionListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Replay izliyor veya Overwatch post-replay'de (koruma gerekli)
     */
    private boolean needsProtection(Player player) {
        if (plugin.getReplayManager().isWatchingReplay(player)) return true;
        if (plugin.getOverwatchReplayListener() != null
                && plugin.getOverwatchReplayListener().needsProtection(player.getUniqueId())) return true;
        return false;
    }

    // ==================== HASAR KORUMASI ====================

    /**
     * Tum hasar kaynaklarini engelle (mob, dusme, ates, bogulma, void, vs.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay viewer'in baska entity'lere hasar vermesini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Replay projectile hasarini engelle
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
                event.setCancelled(true);
                event.setDamage(0);
                return;
            }
        }

        // Viewer'in baskasina hasar vermesini engelle
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (needsProtection(damager)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Moblarin replay viewer'i hedef almasini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player && needsProtection((Player) target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Moblarin replay viewer'i hedef almasini engelle (living entity)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLiving(EntityTargetLivingEntityEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player && needsProtection((Player) target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Aclik barinin degismesini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== ETKILESIM KORUMASI ====================

    /**
     * Item toplama engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Item dusurme engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blok kirma engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blok koyma engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Envanter tiklama engelle (GUI'ler haric)
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Dunya etkilesimlerini engelle (kontrol itemleri haric)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (needsProtection(event.getPlayer())) {
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Replay projectile hit efektlerini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
            event.setCancelled(true);
            projectile.remove();
        }
    }
}
