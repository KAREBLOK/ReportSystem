package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.ExplosionAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExplosionListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    // Explosion'ları takip etmek için (çift kayıt önleme)
    private final Map<Location, Long> recentExplosions = new HashMap<>();

    public ExplosionListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        Location loc = event.getLocation();

        // Çok yakın zamanda aynı konumda explosion olduysa skip et
        if (isRecentExplosion(loc)) {
            return;
        }

        Entity entity = event.getEntity();
        ExplosionAction.ExplosionType explosionType = ExplosionAction.ExplosionType.GENERIC;

        // Explosion türünü belirle
        if (entity instanceof TNTPrimed) {
            explosionType = ExplosionAction.ExplosionType.TNT;
        } else if (entity instanceof Creeper) {
            explosionType = ExplosionAction.ExplosionType.CREEPER;
        } else if (entity instanceof EnderCrystal) {
            explosionType = ExplosionAction.ExplosionType.END_CRYSTAL;
        } else if (entity instanceof Fireball) {
            explosionType = ExplosionAction.ExplosionType.FIREBALL;
        } else if (entity instanceof Wither) {
            explosionType = ExplosionAction.ExplosionType.WITHER;
        } else if (entity instanceof EnderDragon) {
            explosionType = ExplosionAction.ExplosionType.ENDER_DRAGON;
        }

        // Yakındaki kaydedilen oyuncular için explosion kaydet
        float yield = event.getYield();
        boolean setFire = false;

        // Fireball yangın çıkarabilir
        if (entity instanceof Fireball) {
            setFire = ((Fireball) entity).isIncendiary();
        }

        boolean breakBlocks = !event.blockList().isEmpty();

        // Yakındaki recording session'ları bul ve kaydet
        for (Entity nearbyEntity : loc.getWorld().getNearbyEntities(loc, 30, 30, 30)) {
            if (nearbyEntity instanceof Player) {
                Player player = (Player) nearbyEntity;
                RecordingSession session = recordingManager.getSession(player.getUniqueId());

                if (session != null) {
                    session.addAction(new ExplosionAction(
                            explosionType,
                            loc.getX(),
                            loc.getY(),
                            loc.getZ(),
                            yield,
                            setFire,
                            breakBlocks
                    ));

                    plugin.getLogger().info("[RECORDING-DEBUG] Explosion recorded: " + explosionType.name() +
                            " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() +
                            " (power: " + yield + ")");
                }
            }
        }

        // Recent explosion olarak kaydet
        recentExplosions.put(loc, System.currentTimeMillis());

        // 1 saniye sonra temizle
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recentExplosions.remove(loc);
        }, 20L);
    }

    /**
     * Bed explosion için
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedExplosion(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Location loc = event.getEntity().getLocation();

            // Bed explosion detection
            if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL &&
                    loc.getBlock().getType().name().contains("BED")) {

                if (isRecentExplosion(loc)) {
                    return;
                }

                session.addAction(new ExplosionAction(
                        ExplosionAction.ExplosionType.BED,
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        5.0f, // Bed explosion power
                        true, // Yangın çıkarır
                        true  // Blokları kırar
                ));

                recentExplosions.put(loc, System.currentTimeMillis());

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    recentExplosions.remove(loc);
                }, 20L);

                plugin.getLogger().info("[RECORDING-DEBUG] Bed explosion recorded at " +
                        loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
        }
    }

    /**
     * Çok yakın zamanda aynı yerde explosion olmuş mu kontrol et
     */
    private boolean isRecentExplosion(Location loc) {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<Location, Long> entry : recentExplosions.entrySet()) {
            Location recentLoc = entry.getKey();
            long time = entry.getValue();

            // 500ms içinde ve 3 blok içinde ise recent sayılır
            if (currentTime - time < 500 && recentLoc.distance(loc) < 3.0) {
                return true;
            }
        }

        return false;
    }
}
