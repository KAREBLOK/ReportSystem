package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.UUID;

/**
 * Yakındaki oyuncuların aksiyonlarını kaydeder
 * Ana oyuncu kaydedilirken, yakındaki oyuncuların da önemli aksiyonları kaydedilir
 */
public class NearbyPlayerTrackerListener implements Listener {

    private final ReportSystemSpigot plugin;
    private static final double NEARBY_DISTANCE = 50.0; // 50 blok mesafe

    // Nearby player'ların spawn edilip edilmediğini takip et
    private final Map<UUID, Map<UUID, Boolean>> spawnedNearbyPlayers = new HashMap<>(); // <RecordingSessionUUID, <NearbyPlayerUUID, spawned>>

    public NearbyPlayerTrackerListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Oyuncunun yakındaki bir recorded player'a ait session'unu bulur
     */
    private RecordingSession findNearbyRecordingSession(Player player) {
        for (Map.Entry<UUID, RecordingSession> entry : plugin.getRecordingManager().getActiveRecordingSessions().entrySet()) {
            Player recordedPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (recordedPlayer != null && !recordedPlayer.equals(player)) {
                // Aynı dünyada mı?
                if (!recordedPlayer.getWorld().equals(player.getWorld())) continue;

                // Mesafe kontrolü
                double distance = recordedPlayer.getLocation().distance(player.getLocation());
                if (distance <= NEARBY_DISTANCE) {
                    // İlk kez yakındaysa spawn action ekle
                    ensureNearbyPlayerSpawned(entry.getValue(), entry.getKey(), player);
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Nearby player'ın spawn edildiğinden emin olur
     */
    private void ensureNearbyPlayerSpawned(RecordingSession session, UUID recordedPlayerUUID, Player nearbyPlayer) {
        // Bu session için spawn map'i al veya oluştur
        Map<UUID, Boolean> sessionSpawns = spawnedNearbyPlayers.computeIfAbsent(recordedPlayerUUID, k -> new HashMap<>());

        // Eğer daha önce spawn edilmediyse
        if (!sessionSpawns.getOrDefault(nearbyPlayer.getUniqueId(), false)) {
            // Equipment map'i oluştur
            Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipmentMap = new HashMap<>();

            // Equipment'ı map'e ekle
            addEquipmentToMap(equipmentMap, nearbyPlayer);

            // Nearby player spawn action ekle
            NearbyPlayerAction spawnAction = new NearbyPlayerAction(
                    NearbyPlayerAction.ActionType.PLAYER_APPEAR,
                    nearbyPlayer.getUniqueId(),
                    nearbyPlayer.getName(),
                    nearbyPlayer.getLocation().getX(),
                    nearbyPlayer.getLocation().getY(),
                    nearbyPlayer.getLocation().getZ(),
                    nearbyPlayer.getLocation().getYaw(),
                    nearbyPlayer.getLocation().getPitch(),
                    null, // skinTexture - can be null for now
                    null, // skinSignature - can be null for now
                    equipmentMap
            );
            session.addAction(spawnAction);

            // Spawn edildi olarak işaretle
            sessionSpawns.put(nearbyPlayer.getUniqueId(), true);

            plugin.getLogger().info("[NEARBY-TRACKING] Nearby player appeared: " + nearbyPlayer.getName() +
                    " for recorded player session");
        }
    }

    /**
     * Equipment'ı map'e ekler
     */
    private void addEquipmentToMap(Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipmentMap, Player player) {
        // Main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && !mainHand.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.MAIN_HAND, createItemData(mainHand));
        }

        // Off hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.OFF_HAND, createItemData(offHand));
        }

        // Armor
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && !helmet.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.HELMET, createItemData(helmet));
        }

        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && !chestplate.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.CHESTPLATE, createItemData(chestplate));
        }

        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && !leggings.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.LEGGINGS, createItemData(leggings));
        }

        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && !boots.getType().isAir()) {
            equipmentMap.put(EquipmentAction.EquipmentSlot.BOOTS, createItemData(boots));
        }
    }

    /**
     * ItemStack'den ItemData oluşturur
     */
    private EquipmentAction.ItemData createItemData(ItemStack item) {
        byte[] itemData = ItemSerializer.itemStackToBytes(item);
        Map<String, Integer> enchantments = new HashMap<>();
        item.getEnchantments().forEach((enchant, level) -> {
            enchantments.put(enchant.getKey().getKey(), level);
        });

        return new EquipmentAction.ItemData(
                item.getType().name(),
                item.getAmount(),
                item.getDurability(),
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : null,
                item.hasItemMeta() && item.getItemMeta().hasLore() ? item.getItemMeta().getLore() : null,
                enchantments,
                itemData,
                item.hasItemMeta() && item.getItemMeta().hasCustomModelData() ? item.getItemMeta().getCustomModelData() : 0,
                item.hasItemMeta() && item.getItemMeta().isUnbreakable()
        );
    }

    // ========== HEALTH TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getEntity();

        // Bu oyuncu kaydediliyorsa, zaten HealthListener tarafından handle ediliyor
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        // Yakındaki bir session var mı?
        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            double newHealth = Math.max(0, player.getHealth() - event.getFinalDamage());

            HealthAction healthAction = new HealthAction(newHealth, player.getMaxHealth(),
                    player.getFoodLevel(), player.getSaturation());
            healthAction.setOwnerUUID(player.getUniqueId()); // ← Nearby player için set et
            nearbySession.addAction(healthAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Health recorded for nearby player: " +
                    player.getName() + " - Health: " + newHealth);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + event.getAmount());

            HealthAction healthAction = new HealthAction(newHealth, player.getMaxHealth(),
                    player.getFoodLevel(), player.getSaturation());
            healthAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(healthAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Health regain recorded for nearby player: " + player.getName());
        }
    }

    // ========== DEATH TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            String deathMessage = event.getEntity().getLastDamageCause() != null ?
                    event.getEntity().getLastDamageCause().getCause().name() : "Unknown";

            DeathAction deathAction = new DeathAction(
                    player.getName() + " died: " + deathMessage,
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
            );
            deathAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(deathAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Death recorded for nearby player: " + player.getName());
        }
    }

    // ========== ANIMATION TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            AnimationAction animationAction = new AnimationAction(
                    AnimationAction.AnimationType.SWING_MAIN_HAND
            );
            animationAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(animationAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Animation recorded for nearby player: " + player.getName());
        }
    }

    // ========== POSE TRACKING (Sneak, Sprint) ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            PoseAction.PoseType poseType = event.isSneaking() ?
                    PoseAction.PoseType.SNEAKING : PoseAction.PoseType.STANDING;

            PoseAction poseAction = new PoseAction(poseType);
            poseAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(poseAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Pose (sneak) recorded for nearby player: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            // Sprint durumu için özel bir işaretleme yapmıyoruz
            // Çünkü pose sadece görsel duruşu gösteriyor
            plugin.getLogger().fine("[NEARBY-TRACKING] Sprint toggle for nearby player: " + player.getName());
        }
    }

    // ========== VEHICLE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getEntered();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            VehicleAction.VehicleType vehicleType = convertToVehicleType(event.getVehicle().getType().name());
            if (vehicleType != null) {
                VehicleAction vehicleAction = new VehicleAction(
                        VehicleAction.ActionType.MOUNT,
                        vehicleType,
                        event.getVehicle().getLocation().getX(),
                        event.getVehicle().getLocation().getY(),
                        event.getVehicle().getLocation().getZ()
                );
                vehicleAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(vehicleAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Vehicle mount recorded for nearby player: " +
                        player.getName() + " - Vehicle: " + event.getVehicle().getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getExited();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            VehicleAction.VehicleType vehicleType = convertToVehicleType(event.getVehicle().getType().name());
            if (vehicleType != null) {
                VehicleAction vehicleAction = new VehicleAction(
                        VehicleAction.ActionType.DISMOUNT,
                        vehicleType,
                        event.getVehicle().getLocation().getX(),
                        event.getVehicle().getLocation().getY(),
                        event.getVehicle().getLocation().getZ()
                );
                vehicleAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(vehicleAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Vehicle dismount recorded for nearby player: " + player.getName());
            }
        }
    }

    // ========== EQUIPMENT CHANGE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            // 1 tick sonra item'i al (değişim tamamlansın)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
                EquipmentAction.ItemData itemData = convertToItemData(newItem);

                EquipmentAction equipmentAction = new EquipmentAction(
                        EquipmentAction.EquipmentSlot.MAIN_HAND,
                        itemData
                );
                equipmentAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(equipmentAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Equipment (main hand) change recorded for: " + player.getName());
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();

                EquipmentAction mainHandAction = new EquipmentAction(
                        EquipmentAction.EquipmentSlot.MAIN_HAND,
                        convertToItemData(mainHand)
                );
                mainHandAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(mainHandAction);

                EquipmentAction offHandAction = new EquipmentAction(
                        EquipmentAction.EquipmentSlot.OFF_HAND,
                        convertToItemData(offHand)
                );
                offHandAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(offHandAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Hand swap recorded for: " + player.getName());
            }, 2L);
        }
    }

    // ========== BLOCK TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            BlockAction blockAction = new BlockAction(
                    BlockAction.BlockActionType.PLACE_BLOCK,
                    event.getBlock().getX(),
                    event.getBlock().getY(),
                    event.getBlock().getZ(),
                    event.getBlock().getType().name()
            );
            blockAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(blockAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Block place recorded for: " + player.getName() +
                    " - " + event.getBlock().getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            BlockAction blockAction = new BlockAction(
                    BlockAction.BlockActionType.STOP_BREAKING,
                    event.getBlock().getX(),
                    event.getBlock().getY(),
                    event.getBlock().getZ(),
                    10
            );
            blockAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(blockAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Block break recorded for: " + player.getName());
        }
    }

    // ========== ITEM PICKUP/DROP TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            String itemData = java.util.Base64.getEncoder().encodeToString(
                    ItemSerializer.itemStackToBytes(event.getItem().getItemStack())
            );

            ItemAction itemAction = new ItemAction(
                    ItemAction.ItemActionType.PICKUP,
                    itemData,
                    event.getItem().getItemStack().getAmount(),
                    event.getItem().getLocation().getX(),
                    event.getItem().getLocation().getY(),
                    event.getItem().getLocation().getZ()
            );
            itemAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(itemAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Item pickup recorded for: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            String itemData = java.util.Base64.getEncoder().encodeToString(
                    ItemSerializer.itemStackToBytes(event.getItemDrop().getItemStack())
            );

            ItemAction itemAction = new ItemAction(
                    ItemAction.ItemActionType.DROP,
                    itemData,
                    event.getItemDrop().getItemStack().getAmount(),
                    event.getItemDrop().getLocation().getX(),
                    event.getItemDrop().getLocation().getY(),
                    event.getItemDrop().getLocation().getZ()
            );
            itemAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(itemAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Item drop recorded for: " + player.getName());
        }
    }

    // ========== POTION EFFECT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            PotionEffect newEffect = event.getNewEffect();
            if (newEffect != null) {
                PotionEffectAction potionAction = new PotionEffectAction(
                        PotionEffectAction.ActionType.ADD,
                        newEffect.getType().getName(),
                        newEffect.getAmplifier(),
                        newEffect.getDuration(),
                        newEffect.isAmbient(),
                        newEffect.hasParticles()
                );
                potionAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(potionAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Potion effect recorded for: " + player.getName() +
                        " - " + newEffect.getType().getName());
            }
        }
    }

    // ========== CHAT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        // Async event olduğu için sync task ile kaydet
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            RecordingSession nearbySession = findNearbyRecordingSession(player);
            if (nearbySession != null) {
                String formattedMessage = "<" + player.getName() + "> " + event.getMessage();
                ChatAction chatAction = new ChatAction(formattedMessage, false);
                chatAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(chatAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Chat recorded for: " + player.getName());
            }
        });
    }

    // ========== ITEM USE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            ItemStack item = event.getItem();
            if (item != null && item.getType().isEdible()) {
                // Yemek yeme
                String itemData = java.util.Base64.getEncoder().encodeToString(
                        ItemSerializer.itemStackToBytes(item)
                );
                UseItemAction useAction = new UseItemAction(
                        UseItemAction.UseType.FOOD_EAT,
                        0,
                        true,
                        true,
                        itemData
                );
                useAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(useAction);

                plugin.getLogger().info("[NEARBY-TRACKING] Item use (eat) recorded for: " + player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            String itemData = java.util.Base64.getEncoder().encodeToString(
                    ItemSerializer.itemStackToBytes(event.getItem())
            );
            UseItemAction.UseType useType = event.getItem().getType().name().contains("MILK") ?
                    UseItemAction.UseType.MILK_DRINK : UseItemAction.UseType.POTION_DRINK;

            UseItemAction useAction = new UseItemAction(
                    useType,
                    0,
                    true,
                    true,
                    itemData
            );
            useAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(useAction);

            plugin.getLogger().info("[NEARBY-TRACKING] Item consume recorded for: " + player.getName());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Entity type'ı VehicleType enum'a dönüştürür
     */
    private VehicleAction.VehicleType convertToVehicleType(String entityType) {
        if (entityType == null) return null;

        String type = entityType.toUpperCase();
        if (type.contains("MINECART")) return VehicleAction.VehicleType.MINECART;
        if (type.contains("BOAT")) return VehicleAction.VehicleType.BOAT;
        if (type.contains("HORSE")) return VehicleAction.VehicleType.HORSE;
        if (type.contains("PIG")) return VehicleAction.VehicleType.PIG;
        if (type.contains("STRIDER")) return VehicleAction.VehicleType.STRIDER;
        if (type.contains("LLAMA")) return VehicleAction.VehicleType.LLAMA;
        if (type.contains("CAMEL")) return VehicleAction.VehicleType.CAMEL;

        return null; // Desteklenmeyen vehicle tipi
    }

    /**
     * ItemStack'i ItemData'ya dönüştürür
     */
    private EquipmentAction.ItemData convertToItemData(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        String displayName = null;
        List<String> lore = null;
        int customModelData = 0;
        boolean unbreakable = false;

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();

            if (meta.hasDisplayName()) {
                displayName = meta.getDisplayName();
            }

            if (meta.hasLore()) {
                lore = meta.getLore();
            }

            if (meta.hasCustomModelData()) {
                customModelData = meta.getCustomModelData();
            }

            unbreakable = meta.isUnbreakable();
        }

        // Enchantment'ları topla
        java.util.Map<String, Integer> enchantments = new HashMap<>();
        itemStack.getEnchantments().forEach((enchant, level) -> {
            enchantments.put(enchant.getKey().getKey(), level);
        });

        // ItemStack'i serialize et
        byte[] itemData = ItemSerializer.itemStackToBytes(itemStack);

        return new EquipmentAction.ItemData(
                itemStack.getType().name(),
                itemStack.getAmount(),
                itemStack.getDurability(),
                displayName,
                lore,
                enchantments,
                itemData,
                customModelData,
                unbreakable
        );
    }

    // ========== MOVEMENT TRACKING ==========

    private final Map<UUID, Long> lastMovementTime = new HashMap<>();
    private static final long MOVEMENT_THROTTLE = 100; // 100ms = 0.1 saniye

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Eğer bu oyuncu zaten kaydediliyorsa skip (kendi movement'ı zaten kaydediliyor)
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        // Throttle - çok sık kaydetmeyelim
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMovementTime.get(player.getUniqueId());
        if (lastTime != null && (currentTime - lastTime) < MOVEMENT_THROTTLE) {
            return;
        }

        // Yakındaki session var mı?
        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            // Sadece anlamlı hareket varsa kaydet (en az 0.1 blok - daha hassas)
            double distance = event.getFrom().distance(event.getTo());
            if (distance > 0.1) {
                LocationAction locationAction = new LocationAction(
                        event.getTo().getX(),
                        event.getTo().getY(),
                        event.getTo().getZ(),
                        event.getTo().getYaw(),
                        event.getTo().getPitch(),
                        player.isOnGround()
                );
                locationAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(locationAction);

                lastMovementTime.put(player.getUniqueId(), currentTime);

                // CRITICAL DEBUG - Her 50 harekette bir log
                if (nearbySession.getActions().size() % 50 == 0) {
                    plugin.getLogger().warning("[NEARBY-MOVE] Recording movement for " + player.getName() +
                            " UUID: " + player.getUniqueId() +
                            " | Distance: " + String.format("%.2f", distance) +
                            " | Total actions: " + nearbySession.getActions().size());
                }
            }
        }
    }
}
