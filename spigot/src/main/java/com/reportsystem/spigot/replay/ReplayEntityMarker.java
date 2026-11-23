package com.reportsystem.spigot.replay;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Replay sırasında canlı dünyaya spawn edilen entity'leri işaretler (PDC damgası + persistent=false)
 * ve sunucu çökmesi / temizlik atlanması sonrası kalan "hayalet" entity'leri chunk yüklenirken
 * ve eklenti açılırken siler.
 */
public class ReplayEntityMarker implements Listener {

    private static NamespacedKey key;
    private final JavaPlugin plugin;

    public ReplayEntityMarker(JavaPlugin plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "replay_entity");
    }

    /** Listener'ı kaydeder ve zaten yüklü dünyalardaki artıkları bir sonraki tick'te temizler. */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    removeIfMarked(entity);
                }
            }
        });
    }

    /** Entity'yi replay'e ait olarak damgalar ve diske yazılmasını engeller. */
    public static void mark(Entity entity) {
        if (entity == null) return;
        try {
            entity.setPersistent(false);
            if (key != null) {
                entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            }
        } catch (Exception ignored) {
            // Entity zaten geçersizse sessizce geç
        }
    }

    /** Entity replay damgası taşıyor mu? (PDC anahtarı var mı) */
    public static boolean isMarked(Entity entity) {
        if (entity == null || key == null) return false;
        try {
            return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Replay'in spawn ettiği FallingBlock yere değince gerçek dünyaya blok koymasın.
     * İptal edilince sunucu FallingBlock'u kendisi kaldırır (blok koymaz, eşya düşürmez);
     * remove() ek güvence içindir.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlock && isMarked(entity)) {
            event.setCancelled(true);
            entity.remove();
        }
    }

    /** Replay mob'u (lav/düşme/başka mob) ölürse gerçek loot ve XP düşmesin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (isMarked(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /** Replay entity'si (koyun yünü, tavuk yumurtası vb.) gerçek dünyaya eşya düşürmesin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(EntityDropItemEvent event) {
        if (isMarked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay çerçevesi/tablosu (fizik, vuruş, patlama) kırılıp kendini + içindeki eşyayı düşürmesin.
     * Desteği kalkan (PHYSICS) veya önü kapanan (OBSTRUCTION) çerçeve iptal edilince havada asılı
     * kalırdı; bu durumda eşya düşürmeden kaldırılır (remove() drop üretmez).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent event) {
        if (isMarked(event.getEntity())) {
            event.setCancelled(true);
            HangingBreakEvent.RemoveCause cause = event.getCause();
            if (cause == HangingBreakEvent.RemoveCause.PHYSICS || cause == HangingBreakEvent.RemoveCause.OBSTRUCTION) {
                event.getEntity().remove();
            }
        }
    }

    /** Replay aracı (minecart/tekne) kırılıp eşya düşürmesin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (isMarked(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay entity'lerinin gerçek dünyayla hasar etkileşimi kesilir:
     * - replay çerçevesine/tablosuna vurarak içindeki eşyayı düşürme engellenir;
     * - replay entity'si (mob, ok, mızrak vb.) gerçek oyunculara/moblara hasar veremez;
     * - fırlatanı replay entity'si olan mermi de hasar veremez.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Hanging && isMarked(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        Entity damager = event.getDamager();
        if (isMarked(damager)) {
            event.setCancelled(true);
            return;
        }
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity && isMarked((Entity) shooter)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Replay entity'si hiçbir sebeple hasar almaz (yaratıcı moddaki oyuncu setInvulnerable'ı aşar).
     * İstisna: KILL (/kill ile yönetici temizliği) ve VOID (dünya dışına düşen entity sonsuza dek kalmasın).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMarkedDamaged(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (isMarked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay mob'u kimseyi hedef almasın (AI kapalı olsa da hasar/eklenti tetiklemeleri için güvence);
     * gerçek mob'lar da replay entity'sini hedef almasın.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {
        if (isMarked(event.getEntity()) || isMarked(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay mermisi (ok, mızrak, kartopu, yumurta) bir bloğa/entity'ye çarpınca gerçek etki bırakmasın:
     * yumurta tavuk doğurmaz, ok hedef bloğu/düğmeyi tetiklemez veya bloğa saplanıp kalmaz.
     * İptal edilen mermi yoluna devam edeceği için kaldırılır (remove() eşya düşürmez).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (isMarked(projectile)) {
            event.setCancelled(true);
            projectile.remove();
        }
    }

    /** Replay creeper'ı / TNT'si vb. patlamaya hazırlanamasın. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (isMarked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Yine de patlarsa gerçek dünyada blok kırmasın. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isMarked(event.getEntity())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    /** Replay çerçevesine eşya koyma/çıkarma/döndürme engellenir. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame && isMarked(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            removeIfMarked(entity);
        }
    }

    private void removeIfMarked(Entity entity) {
        try {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                entity.remove();
            }
        } catch (Exception ignored) {
        }
    }
}
