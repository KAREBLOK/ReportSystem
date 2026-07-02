package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.EntitySpawnAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntitySpawnListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    // Debounce için son spawn egg kullanım zamanları (player UUID -> son kullanım zamanı)
    private final Map<UUID, Long> lastSpawnEggUse = new ConcurrentHashMap<>();
    private static final long SPAWN_EGG_COOLDOWN = 100; // 100ms cooldown

    public EntitySpawnListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnEggUse(PlayerInteractEvent event) {
        try {
            if (event.isCancelled() || event.getItem() == null) return;

            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();

            // Debounce check - aynı oyuncu çok kısa sürede birden fazla spawn egg kullanmasını engelle
            long currentTime = System.currentTimeMillis();
            Long lastUse = lastSpawnEggUse.get(playerUUID);
            if (lastUse != null && (currentTime - lastUse) < SPAWN_EGG_COOLDOWN) {
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Spawn egg debounced for " + player.getName());
                return;
            }

            RecordingSession session = recordingManager.getSession(playerUUID);

            if (session != null) {
                ItemStack item = event.getItem();

                // Spawn egg kontrolü
                if (item.getType().name().endsWith("_SPAWN_EGG")) {
                    // Cooldown'u güncelle
                    lastSpawnEggUse.put(playerUUID, currentTime);
                    // Spawn egg kullanımını özel bir action olarak kaydet
                    String entityType = item.getType().name().replace("_SPAWN_EGG", "");

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        try {
                            // 1 tick sonra spawn olan entity'yi bul
                            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                                if (entity.getType().name().equals(entityType) &&
                                        entity.getTicksLived() < 5) {

                                    // Entity özelliklerini topla
                                    Map<String, String> properties = extractEntityProperties(entity);

                                    EntitySpawnAction action = new EntitySpawnAction(
                                            entity.getUniqueId(),
                                            entity.getType().name(),
                                            entity.getLocation().getX(),
                                            entity.getLocation().getY(),
                                            entity.getLocation().getZ(),
                                            entity.getLocation().getYaw(),
                                            entity.getLocation().getPitch(),
                                            properties
                                    );
                                    session.addAction(action);

                                    // Entity'yi tracking için session'a ekle
                                    session.trackSpawnedEntity(entity);

                                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Spawn egg used: " + entityType +
                                            " at " + entity.getLocation() + ", UUID: " + entity.getUniqueId() +
                                            ", Properties: " + properties);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("[EntitySpawnListener] Error in spawn egg delayed task: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }, 2L);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[EntitySpawnListener] Error in onSpawnEggUse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Entity'nin özelliklerini çıkarır (renk, baby, isim, vb.)
     */
    private Map<String, String> extractEntityProperties(Entity entity) {
        Map<String, String> props = new HashMap<>();

        try {
            // Tüm LivingEntity'ler için ortak özellikler
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;

                // Baby/Adult
                if (living instanceof Ageable) {
                    Ageable ageable = (Ageable) living;
                    props.put("baby", String.valueOf(!ageable.isAdult()));
                    props.put("age", String.valueOf(ageable.getAge()));
                }

                // Custom name
                if (living.getCustomName() != null) {
                    props.put("customName", living.getCustomName());
                    props.put("customNameVisible", String.valueOf(living.isCustomNameVisible()));
                }

                // AI
                props.put("ai", String.valueOf(living.hasAI()));
            }

            // Koyun - Renk ve kırpık durum
            if (entity instanceof Sheep) {
                Sheep sheep = (Sheep) entity;
                props.put("color", sheep.getColor().name());
                props.put("sheared", String.valueOf(sheep.isSheared()));
            }

            // Kedi - Tip ve tasma
            else if (entity instanceof Cat) {
                Cat cat = (Cat) entity;
                try {
                    // 1.21+ için getType() kullan
                    props.put("catType", cat.getType().name());
                } catch (NoSuchMethodError e) {
                    // Eski versiyonlar için getCatType() fallback
                    try {
                        props.put("catType", cat.getCatType().name());
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Could not get cat type: " + ex.getMessage());
                    }
                }
                if (cat.isTamed() && cat.getCollarColor() != null) {
                    props.put("collarColor", cat.getCollarColor().name());
                    props.put("tamed", "true");
                }
            }

            // Kurt - Tasma rengi
            else if (entity instanceof Wolf) {
                Wolf wolf = (Wolf) entity;
                if (wolf.isTamed() && wolf.getCollarColor() != null) {
                    props.put("collarColor", wolf.getCollarColor().name());
                    props.put("tamed", "true");
                    props.put("angry", String.valueOf(wolf.isAngry()));
                }
            }

            // At - Renk, stil, zırh
            else if (entity instanceof Horse) {
                Horse horse = (Horse) entity;
                props.put("color", horse.getColor().name());
                props.put("style", horse.getStyle().name());
                if (horse.isTamed()) {
                    props.put("tamed", "true");
                }
                if (horse.getInventory().getArmor() != null) {
                    props.put("armor", horse.getInventory().getArmor().getType().name());
                }
                if (horse.getInventory().getSaddle() != null) {
                    props.put("saddle", "true");
                }
            }

            // Llama - Renk
            else if (entity instanceof Llama) {
                Llama llama = (Llama) entity;
                props.put("color", llama.getColor().name());
                props.put("strength", String.valueOf(llama.getStrength()));
                if (llama.isTamed()) {
                    props.put("tamed", "true");
                }
            }

            // Papağan - Variant (renk)
            else if (entity instanceof Parrot) {
                Parrot parrot = (Parrot) entity;
                props.put("variant", parrot.getVariant().name());
                if (parrot.isTamed()) {
                    props.put("tamed", "true");
                }
            }

            // Axolotl - Variant
            else if (entity instanceof Axolotl) {
                Axolotl axolotl = (Axolotl) entity;
                props.put("variant", axolotl.getVariant().name());
            }

            // Fox - Tip (Red/Snow)
            else if (entity instanceof Fox) {
                Fox fox = (Fox) entity;
                props.put("foxType", fox.getFoxType().name());
                props.put("sleeping", String.valueOf(fox.isSleeping()));
                props.put("crouching", String.valueOf(fox.isCrouching()));
            }

            // Panda - Gen
            else if (entity instanceof Panda) {
                Panda panda = (Panda) entity;
                props.put("mainGene", panda.getMainGene().name());
                props.put("hiddenGene", panda.getHiddenGene().name());
            }

            // Villager - Meslek ve tip
            else if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                props.put("profession", villager.getProfession().name());
                props.put("villagerType", villager.getVillagerType().name());
                props.put("level", String.valueOf(villager.getVillagerLevel()));
            }

            // Zombie Villager - Meslek
            else if (entity instanceof ZombieVillager) {
                ZombieVillager zombieVillager = (ZombieVillager) entity;
                props.put("profession", zombieVillager.getVillagerProfession().name());
            }

            // Tropical Fish - Desen ve renk
            else if (entity instanceof TropicalFish) {
                TropicalFish fish = (TropicalFish) entity;
                props.put("pattern", fish.getPattern().name());
                props.put("bodyColor", fish.getBodyColor().name());
                props.put("patternColor", fish.getPatternColor().name());
            }

            // Slime/Magma Cube - Boyut
            else if (entity instanceof Slime) {
                Slime slime = (Slime) entity;
                props.put("size", String.valueOf(slime.getSize()));
            }

            // Creeper - Powered (şimşek vurmuş)
            else if (entity instanceof Creeper) {
                Creeper creeper = (Creeper) entity;
                props.put("powered", String.valueOf(creeper.isPowered()));
            }

            // Rabbit - Tip
            else if (entity instanceof Rabbit) {
                Rabbit rabbit = (Rabbit) entity;
                props.put("rabbitType", rabbit.getRabbitType().name());
            }

            // Shulker - Renk
            else if (entity instanceof Shulker) {
                Shulker shulker = (Shulker) entity;
                if (shulker.getColor() != null) {
                    props.put("color", shulker.getColor().name());
                }
            }

            // Frog - Variant (1.19+)
            else if (entity instanceof Frog) {
                Frog frog = (Frog) entity;
                props.put("variant", frog.getVariant().name());
            }

            // Goat - Screaming (1.17+)
            else if (entity instanceof Goat) {
                Goat goat = (Goat) entity;
                props.put("screaming", String.valueOf(goat.isScreaming()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[EntitySpawnListener] Error extracting entity properties for " +
                    entity.getType().name() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return props;
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        // Cooldown map'ini temizle
        lastSpawnEggUse.remove(event.getPlayer().getUniqueId());
    }
}