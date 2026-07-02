package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.SoundAction;
import com.reportsystem.common.replay.actions.UseItemAction;
import com.reportsystem.common.replay.actions.ProjectileAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UseItemListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> bowChargeStartTime = new HashMap<>();
    private final Map<UUID, Long> shieldBlockStartTime = new HashMap<>();
    private final Map<UUID, Long> foodEatStartTime = new HashMap<>();

    public UseItemListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack item = event.getItem();
            UseItemAction.UseType useType;
            String itemData = com.reportsystem.spigot.utils.ItemSerializer.serializeItemStack(item);

            if (item.getType() == Material.POTION) {
                useType = UseItemAction.UseType.POTION_DRINK;
                // İçme bitişi - serialize ile potion effect bilgilerini kaydet
                session.addAction(new UseItemAction(useType, 32, true, false, itemData)); // 32 tick = yaklaşık içme süresi
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Potion consumed: " + item.getType().name());
            } else if (item.getType() == Material.MILK_BUCKET) {
                useType = UseItemAction.UseType.MILK_DRINK;
                session.addAction(new UseItemAction(useType, 32, true, false, itemData));
            } else {
                useType = UseItemAction.UseType.FOOD_EAT;
                // Yemek tipini kaydet
                session.addAction(new UseItemAction(useType, 32, true, false, itemData));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Food consumed: " + item.getType().name());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null && event.getItem() != null) {
            Material itemType = event.getItem().getType();
            boolean isMainHand = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND;

            // Kalkan kullanımı
            if (itemType == Material.SHIELD) {
                if (!shieldBlockStartTime.containsKey(player.getUniqueId())) {
                    shieldBlockStartTime.put(player.getUniqueId(), System.currentTimeMillis());
                    session.addAction(new UseItemAction(UseItemAction.UseType.SHIELD_BLOCK, 0, isMainHand, true));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Shield block started");
                }
            }
            // Yay kullanımı başlangıcı
            else if (itemType == Material.BOW) {
                bowChargeStartTime.put(player.getUniqueId(), System.currentTimeMillis());
                session.addAction(new UseItemAction(UseItemAction.UseType.BOW_CHARGE, 0, true, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Bow charge started");
            }
            // Arbalet kullanımı
            else if (itemType == Material.CROSSBOW) {
                session.addAction(new UseItemAction(UseItemAction.UseType.CROSSBOW_CHARGE, 0, true, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Crossbow charge started");
            }
            // Olta kullanımı - SADECE FishingListener handle etsin
            else if (itemType == Material.FISHING_ROD) {
                // Hiçbir şey yapma, FishingListener halledecek
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Fishing rod use detected, letting FishingListener handle it");
            }
            // Trident kullanımı (Riptide)
            else if (itemType == Material.TRIDENT) {
                session.addAction(new UseItemAction(UseItemAction.UseType.TRIDENT_CHARGE, 0, true, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Trident charge started");
            }
            // Dürbün kullanımı
            else if (itemType == Material.SPYGLASS) {
                session.addAction(new UseItemAction(UseItemAction.UseType.SPYGLASS, 0, isMainHand, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Spyglass used");
            }
            // Keçi boynuzu
            else if (itemType == Material.GOAT_HORN) {
                session.addAction(new UseItemAction(UseItemAction.UseType.GOAT_HORN, 0, isMainHand, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Goat horn used");
            }
            // İksir kontrolü - GÜNCELLENDİ
            else if (itemType == Material.POTION || itemType == Material.SPLASH_POTION ||
                    itemType == Material.LINGERING_POTION) {

                if (itemType == Material.POTION) {
                    // Normal iksir - içme animasyonu
                    session.addAction(new UseItemAction(UseItemAction.UseType.POTION_DRINK, 0, isMainHand, true));
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Potion drink started: " + itemType.name());
                }
                // Splash ve lingering potion'lar ProjectileAction ile handle ediliyor
            }
            // Yemek yeme başlangıcı
            else if (itemType.isEdible()) {
                foodEatStartTime.put(player.getUniqueId(), System.currentTimeMillis());
                session.addAction(new UseItemAction(UseItemAction.UseType.FOOD_EAT, 0, isMainHand, true));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Food eating started: " + itemType.name());
            }
        }
    }

    // Kalkan kullanımını durdurmak için
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null && shieldBlockStartTime.containsKey(player.getUniqueId())) {
            // Kalkan kullanımı bitti
            shieldBlockStartTime.remove(player.getUniqueId());
            session.addAction(new UseItemAction(UseItemAction.UseType.SHIELD_BLOCK, 0, true, false));
            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Shield block ended");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            // Yay çekme süresi
            long chargeTime = 0;
            if (bowChargeStartTime.containsKey(player.getUniqueId())) {
                chargeTime = System.currentTimeMillis() - bowChargeStartTime.remove(player.getUniqueId());
            }

            // Yay çekme bitti
            session.addAction(new UseItemAction(UseItemAction.UseType.BOW_CHARGE,
                    (int)(event.getForce() * 20), true, false));

            // Ok fırlatıldı
            if (event.getProjectile() instanceof Arrow) {
                Arrow arrow = (Arrow) event.getProjectile();
                Vector velocity = arrow.getVelocity();

                // Tipped arrow ise potion effect bilgilerini serialize et
                String arrowData = null;
                if (arrow.hasCustomEffects() || arrow.getBasePotionData() != null) {
                    // Arrow'u ItemStack olarak serialize etmek için konsol'dan çekilen bow arrow itemstack'ini kullan
                    ItemStack arrowItem = player.getInventory().getItemInMainHand();
                    if (arrowItem.getType().name().contains("ARROW")) {
                        arrowData = com.reportsystem.spigot.utils.ItemSerializer.serializeItemStack(arrowItem);
                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Tipped arrow shot: " + arrowItem.getType().name());
                    }
                }

                session.addAction(new ProjectileAction(
                        ProjectileAction.ProjectileType.ARROW,
                        velocity.getX(), velocity.getY(), velocity.getZ(),
                        player.getLocation().getYaw(), player.getLocation().getPitch(),
                        null, // potionData için null
                        arrowData
                ));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Arrow shot with force: " + event.getForce());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Projectile projectile = event.getEntity();

            // Debug log
            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Projectile detected: " + projectile.getType().name());

            Vector velocity = projectile.getVelocity();
            ProjectileAction.ProjectileType type = null;

            if (projectile instanceof Snowball) {
                type = ProjectileAction.ProjectileType.SNOWBALL;
            } else if (projectile instanceof Egg) {
                type = ProjectileAction.ProjectileType.EGG;
            } else if (projectile instanceof EnderPearl) {
                type = ProjectileAction.ProjectileType.ENDER_PEARL;
            } else if (projectile instanceof ThrownExpBottle) {
                type = ProjectileAction.ProjectileType.EXPERIENCE_BOTTLE;
            } else if (projectile instanceof ThrownPotion) {
                ThrownPotion potion = (ThrownPotion) projectile;
                type = potion.getItem().getType() == Material.LINGERING_POTION ?
                        ProjectileAction.ProjectileType.LINGERING_POTION :
                        ProjectileAction.ProjectileType.SPLASH_POTION;

                // Potion item'ını serialize et
                String potionData = com.reportsystem.spigot.utils.ItemSerializer.serializeItemStack(potion.getItem());
                session.addAction(new ProjectileAction(
                        type,
                        velocity.getX(), velocity.getY(), velocity.getZ(),
                        player.getLocation().getYaw(), player.getLocation().getPitch(),
                        potionData
                ));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Potion launched: " + type.name());
                return; // Early return to avoid duplicate action
            } else if (projectile instanceof Trident) {
                type = ProjectileAction.ProjectileType.TRIDENT;
            } else if (projectile instanceof FishHook) {
                // FishHook artık burada işlenmiyor, FishingListener hallediyor
                return;
            } else if (projectile instanceof Firework) {
                type = ProjectileAction.ProjectileType.FIREWORK_ROCKET;
            }

            if (type != null) {
                session.addAction(new ProjectileAction(
                        type,
                        velocity.getX(), velocity.getY(), velocity.getZ(),
                        player.getLocation().getYaw(), player.getLocation().getPitch()
                ));
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Projectile launched: " + type.name());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Location hitLoc = event.getEntity().getLocation();
                Sound hitSound = null;
                float volume = 1.0f;
                float pitch = 1.0f;

                // Çarpma tipine göre ses belirle
                if (event.getEntity() instanceof Arrow) {
                    hitSound = event.getHitEntity() != null ? Sound.ENTITY_ARROW_HIT : Sound.ENTITY_ARROW_HIT;
                    pitch = 1.0f + (float) (Math.random() * 0.2 - 0.1); // Slight variation
                } else if (event.getEntity() instanceof Snowball) {
                    hitSound = Sound.ENTITY_SNOWBALL_THROW;
                } else if (event.getEntity() instanceof Egg) {
                    hitSound = Sound.ENTITY_EGG_THROW;
                } else if (event.getEntity() instanceof EnderPearl) {
                    hitSound = Sound.ENTITY_ENDERMAN_TELEPORT;
                } else if (event.getEntity() instanceof ThrownPotion) {
                    hitSound = Sound.BLOCK_GLASS_BREAK;
                }

                if (hitSound != null) {
                    SoundAction soundAction = new SoundAction(
                            hitSound.name(),
                            hitLoc.getX(),
                            hitLoc.getY(),
                            hitLoc.getZ(),
                            volume,
                            pitch
                    );
                    session.addAction(soundAction);

                    String hitType = event.getHitEntity() != null ? "entity" :
                            (event.getHitBlock() != null ? "block" : "unknown");
                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Projectile hit (" + hitType + "): " +
                            event.getEntity().getType().name() + " at " +
                            String.format("%.1f, %.1f, %.1f", hitLoc.getX(), hitLoc.getY(), hitLoc.getZ()));
                }
            }
        }
    }

    // Oyuncu ayrılırsa temizlik yap
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        bowChargeStartTime.remove(uuid);
        shieldBlockStartTime.remove(uuid);
        foodEatStartTime.remove(uuid);
    }
}