package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

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

            plugin.debug("[NEARBY-TRACKING] Nearby player appeared: " + nearbyPlayer.getName() +
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
                    player.getFoodLevel(), player.getSaturation(), player.getAbsorptionAmount());
            healthAction.setOwnerUUID(player.getUniqueId()); // ← Nearby player için set et
            nearbySession.addAction(healthAction);

            plugin.debug("[NEARBY-TRACKING] Health recorded for nearby player: " +
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
                    player.getFoodLevel(), player.getSaturation(), player.getAbsorptionAmount());
            healthAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(healthAction);

            plugin.debug("[NEARBY-TRACKING] Health regain recorded for nearby player: " + player.getName());
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

            plugin.debug("[NEARBY-TRACKING] Death recorded for nearby player: " + player.getName());
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

            plugin.debug("[NEARBY-TRACKING] Animation recorded for nearby player: " + player.getName());
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

            plugin.debug("[NEARBY-TRACKING] Pose (sneak) recorded for nearby player: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            EntityStateAction sprintAction = new EntityStateAction(
                    EntityStateAction.StateType.SPRINTING,
                    event.isSprinting(),
                    player.getUniqueId()
            );
            sprintAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(sprintAction);

            plugin.debug("[NEARBY-TRACKING] Sprint " + (event.isSprinting() ? "start" : "stop") +
                    " recorded for: " + player.getName());
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

                plugin.debug("[NEARBY-TRACKING] Vehicle mount recorded for nearby player: " +
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

                plugin.debug("[NEARBY-TRACKING] Vehicle dismount recorded for nearby player: " + player.getName());
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

                plugin.debug("[NEARBY-TRACKING] Equipment (main hand) change recorded for: " + player.getName());
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

                plugin.debug("[NEARBY-TRACKING] Hand swap recorded for: " + player.getName());
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

            plugin.debug("[NEARBY-TRACKING] Block place recorded for: " + player.getName() +
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
                    10,
                    event.getBlock().getType().name()
            );
            blockAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(blockAction);

            plugin.debug("[NEARBY-TRACKING] Block break recorded for: " + player.getName() +
                    " - " + event.getBlock().getType());
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

            plugin.debug("[NEARBY-TRACKING] Item pickup recorded for: " + player.getName());
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

            // DROP için eşyanın gerçek VELOCITY'sini kaydet
            org.bukkit.util.Vector vel = event.getItemDrop().getVelocity();

            ItemAction itemAction = new ItemAction(
                    ItemAction.ItemActionType.DROP,
                    itemData,
                    event.getItemDrop().getItemStack().getAmount(),
                    vel.getX(), vel.getY(), vel.getZ()
            );
            itemAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(itemAction);

            plugin.debug("[NEARBY-TRACKING] Item drop recorded for: " + player.getName());
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

                plugin.debug("[NEARBY-TRACKING] Potion effect recorded for: " + player.getName() +
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

                plugin.debug("[NEARBY-TRACKING] Chat recorded for: " + player.getName());
            }
        });
    }

    // ========== ITEM USE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession == null) return;

        // 1. Yemek yeme kontrolü
        ItemStack item = event.getItem();
        if (item != null && item.getType().isEdible()) {
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

            plugin.debug("[NEARBY-TRACKING] Item use (eat) recorded for: " + player.getName());
        }

        // 2. Blok etkileşimi kontrolü (kapı, trapdoor, gate, button, lever)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            Material type = block.getType();
            BlockData blockData = block.getBlockData();

            boolean isInteractable = false;
            String interactionType = "";

            if (blockData instanceof Openable) {
                isInteractable = true;
                Openable openable = (Openable) blockData;

                if (blockData instanceof Door) {
                    Door door = (Door) blockData;
                    Block targetBlock = block;
                    if (door.getHalf() == Bisected.Half.TOP) {
                        targetBlock = block.getRelative(0, -1, 0);
                    }

                    interactionType = openable.isOpen() ? "DOOR_CLOSE" : "DOOR_OPEN";

                    // Ana kapı ve çift kapı kaydı
                    recordNearbyDoorInteraction(nearbySession, targetBlock, interactionType, player);

                } else if (blockData instanceof TrapDoor) {
                    interactionType = openable.isOpen() ? "TRAPDOOR_CLOSE" : "TRAPDOOR_OPEN";
                } else if (type.name().contains("GATE")) {
                    interactionType = openable.isOpen() ? "GATE_CLOSE" : "GATE_OPEN";
                }
            } else if (type.name().contains("BUTTON")) {
                isInteractable = true;
                interactionType = "BUTTON_PRESS";
            } else if (type.name().contains("LEVER")) {
                isInteractable = true;
                interactionType = "LEVER_TOGGLE";
            } else if (type.name().contains("PLATE")) {
                isInteractable = true;
                interactionType = "PRESSURE_PLATE";
            }

            if (isInteractable && !interactionType.isEmpty() && !interactionType.startsWith("DOOR")) {
                org.bukkit.Location loc = block.getLocation();
                BlockAction blockAction = new BlockAction(
                        BlockAction.BlockActionType.INTERACT_BLOCK,
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ(),
                        interactionType
                );
                blockAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(blockAction);

                plugin.debug("[NEARBY-TRACKING] Block interaction: " + interactionType +
                        " by " + player.getName());
            }
        }
    }

    /**
     * Nearby player kapı etkileşimini kaydeder (double door desteği ile)
     */
    private void recordNearbyDoorInteraction(RecordingSession session, Block doorBlock, String interactionType, Player player) {
        org.bukkit.Location loc = doorBlock.getLocation();

        BlockAction blockAction = new BlockAction(
                BlockAction.BlockActionType.INTERACT_BLOCK,
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                interactionType
        );
        blockAction.setOwnerUUID(player.getUniqueId());
        session.addAction(blockAction);

        plugin.debug("[NEARBY-TRACKING] Door interaction: " + interactionType +
                " by " + player.getName());

        // Komşu çift kapıyı kontrol et
        Block[] neighbors = new Block[] {
            doorBlock.getRelative(1, 0, 0),
            doorBlock.getRelative(-1, 0, 0),
            doorBlock.getRelative(0, 0, 1),
            doorBlock.getRelative(0, 0, -1)
        };

        for (Block neighbor : neighbors) {
            if (neighbor.getBlockData() instanceof Door) {
                Door neighborDoor = (Door) neighbor.getBlockData();
                if (neighborDoor.isOpen() == ((Door) doorBlock.getBlockData()).isOpen()) {
                    org.bukkit.Location neighborLoc = neighbor.getLocation();
                    BlockAction neighborAction = new BlockAction(
                            BlockAction.BlockActionType.INTERACT_BLOCK,
                            neighborLoc.getBlockX(),
                            neighborLoc.getBlockY(),
                            neighborLoc.getBlockZ(),
                            interactionType
                    );
                    neighborAction.setOwnerUUID(player.getUniqueId());
                    session.addAction(neighborAction);

                    plugin.debug("[NEARBY-TRACKING] Double door: " + interactionType +
                            " by " + player.getName());
                    break;
                }
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

            plugin.debug("[NEARBY-TRACKING] Item consume recorded for: " + player.getName());
        }
    }

    // ========== PROJECTILE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.util.Vector velocity = event.getProjectile().getVelocity();
            org.bukkit.Location loc = player.getLocation();

            ProjectileAction.ProjectileType type = ProjectileAction.ProjectileType.ARROW;
            if (event.getProjectile() instanceof SpectralArrow) {
                type = ProjectileAction.ProjectileType.ARROW;
            }

            ProjectileAction projectileAction = new ProjectileAction(
                    type,
                    velocity.getX(), velocity.getY(), velocity.getZ(),
                    loc.getYaw(), loc.getPitch()
            );
            projectileAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(projectileAction);

            plugin.debug("[NEARBY-TRACKING] Bow shoot recorded for: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        // Ok zaten EntityShootBowEvent'te yakalanıyor
        if (event.getEntity() instanceof Arrow || event.getEntity() instanceof SpectralArrow) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.util.Vector velocity = event.getEntity().getVelocity();
            org.bukkit.Location loc = player.getLocation();

            ProjectileAction.ProjectileType type = null;
            if (event.getEntity() instanceof Snowball) {
                type = ProjectileAction.ProjectileType.SNOWBALL;
            } else if (event.getEntity() instanceof Egg) {
                type = ProjectileAction.ProjectileType.EGG;
            } else if (event.getEntity() instanceof EnderPearl) {
                type = ProjectileAction.ProjectileType.ENDER_PEARL;
            } else if (event.getEntity() instanceof ThrownExpBottle) {
                type = ProjectileAction.ProjectileType.EXPERIENCE_BOTTLE;
            } else if (event.getEntity() instanceof ThrownPotion) {
                type = ProjectileAction.ProjectileType.SPLASH_POTION;
            } else if (event.getEntity() instanceof Trident) {
                type = ProjectileAction.ProjectileType.TRIDENT;
            } else if (event.getEntity() instanceof Firework) {
                type = ProjectileAction.ProjectileType.FIREWORK_ROCKET;
            } else if (event.getEntity() instanceof FishHook) {
                type = ProjectileAction.ProjectileType.FISHING_HOOK;
            }

            if (type != null) {
                String potionData = null;
                if (event.getEntity() instanceof ThrownPotion) {
                    ThrownPotion potion = (ThrownPotion) event.getEntity();
                    ItemStack potionItem = potion.getItem();
                    if (potionItem != null && potionItem.getItemMeta() instanceof PotionMeta) {
                        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
                        potionData = meta.getBasePotionType() != null ? meta.getBasePotionType().name() : null;
                    }
                }

                ProjectileAction projectileAction = new ProjectileAction(
                        type,
                        velocity.getX(), velocity.getY(), velocity.getZ(),
                        loc.getYaw(), loc.getPitch(),
                        potionData
                );
                projectileAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(projectileAction);

                plugin.debug("[NEARBY-TRACKING] Projectile " + type + " recorded for: " + player.getName());
            }
        }
    }

    // ========== FISHING TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            FishingAction.FishingState state;
            double hookX, hookY, hookZ;

            switch (event.getState()) {
                case FISHING:
                    state = FishingAction.FishingState.CAST;
                    // CAST için hook'un VELOCITY'sini kaydet (konum değil!)
                    if (event.getHook() != null) {
                        org.bukkit.util.Vector vel = event.getHook().getVelocity();
                        hookX = vel.getX();
                        hookY = vel.getY();
                        hookZ = vel.getZ();
                    } else {
                        hookX = 0; hookY = 0.3; hookZ = 0;
                    }
                    break;
                case CAUGHT_FISH:
                    state = FishingAction.FishingState.CAUGHT;
                    hookX = event.getHook() != null ? event.getHook().getLocation().getX() : player.getLocation().getX();
                    hookY = event.getHook() != null ? event.getHook().getLocation().getY() : player.getLocation().getY();
                    hookZ = event.getHook() != null ? event.getHook().getLocation().getZ() : player.getLocation().getZ();
                    break;
                case REEL_IN:
                case IN_GROUND:
                    state = FishingAction.FishingState.REEL_IN;
                    hookX = event.getHook() != null ? event.getHook().getLocation().getX() : player.getLocation().getX();
                    hookY = event.getHook() != null ? event.getHook().getLocation().getY() : player.getLocation().getY();
                    hookZ = event.getHook() != null ? event.getHook().getLocation().getZ() : player.getLocation().getZ();
                    break;
                case FAILED_ATTEMPT:
                    state = FishingAction.FishingState.FAILED;
                    hookX = event.getHook() != null ? event.getHook().getLocation().getX() : player.getLocation().getX();
                    hookY = event.getHook() != null ? event.getHook().getLocation().getY() : player.getLocation().getY();
                    hookZ = event.getHook() != null ? event.getHook().getLocation().getZ() : player.getLocation().getZ();
                    break;
                default:
                    return;
            }

            String caughtItemData = null;
            if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item) {
                Item caughtItem = (Item) event.getCaught();
                caughtItemData = java.util.Base64.getEncoder().encodeToString(
                        ItemSerializer.itemStackToBytes(caughtItem.getItemStack())
                );
            }

            FishingAction fishingAction = caughtItemData != null ?
                    new FishingAction(state, hookX, hookY, hookZ, caughtItemData) :
                    new FishingAction(state, hookX, hookY, hookZ);
            fishingAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(fishingAction);

            plugin.debug("[NEARBY-TRACKING] Fishing " + state + " recorded for: " + player.getName() +
                    (state == FishingAction.FishingState.CAST ?
                            " | velocity=" + String.format("%.3f, %.3f, %.3f", hookX, hookY, hookZ) : ""));
        }
    }

    // ========== FIRE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            FireAction fireAction = new FireAction(true, (int) event.getDuration());
            fireAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(fireAction);

            plugin.debug("[NEARBY-TRACKING] Fire recorded for: " + player.getName());
        }
    }

    // ========== TELEPORT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            if (to == null) return;

            TeleportAction.TeleportCause cause;
            switch (event.getCause()) {
                case ENDER_PEARL:
                    cause = TeleportAction.TeleportCause.ENDER_PEARL;
                    break;
                case CHORUS_FRUIT:
                    cause = TeleportAction.TeleportCause.CHORUS_FRUIT;
                    break;
                case COMMAND:
                    cause = TeleportAction.TeleportCause.COMMAND;
                    break;
                case PLUGIN:
                    cause = TeleportAction.TeleportCause.PLUGIN;
                    break;
                default:
                    cause = TeleportAction.TeleportCause.UNKNOWN;
                    break;
            }

            TeleportAction teleportAction = new TeleportAction(
                    from.getX(), from.getY(), from.getZ(), from.getWorld().getName(),
                    to.getX(), to.getY(), to.getZ(), to.getWorld().getName(),
                    cause
            );
            teleportAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(teleportAction);

            plugin.debug("[NEARBY-TRACKING] Teleport recorded for: " + player.getName() +
                    " cause: " + cause);
        }
    }

    // ========== BUCKET TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            BucketAction.BucketType bucketType = getBucketType(event.getBucket());
            if (bucketType != null) {
                Block block = event.getBlock();
                BucketAction bucketAction = new BucketAction(
                        BucketAction.ActionType.EMPTY,
                        bucketType,
                        block.getX(), block.getY(), block.getZ()
                );
                bucketAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(bucketAction);

                plugin.debug("[NEARBY-TRACKING] Bucket empty " + bucketType +
                        " recorded for: " + player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            BucketAction.BucketType bucketType = getBucketType(event.getBucket());
            if (bucketType != null) {
                Block block = event.getBlock();
                BucketAction bucketAction = new BucketAction(
                        BucketAction.ActionType.FILL,
                        bucketType,
                        block.getX(), block.getY(), block.getZ()
                );
                bucketAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(bucketAction);

                plugin.debug("[NEARBY-TRACKING] Bucket fill " + bucketType +
                        " recorded for: " + player.getName());
            }
        }
    }

    // ========== CONTAINER TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            ContainerAction.ContainerType containerType = getContainerType(event.getInventory().getType());
            if (containerType != null && event.getInventory().getLocation() != null) {
                org.bukkit.Location loc = event.getInventory().getLocation();
                ContainerAction containerAction = new ContainerAction(
                        containerType,
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        true
                );
                containerAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(containerAction);

                plugin.debug("[NEARBY-TRACKING] Container open recorded for: " + player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            ContainerAction.ContainerType containerType = getContainerType(event.getInventory().getType());
            if (containerType != null && event.getInventory().getLocation() != null) {
                org.bukkit.Location loc = event.getInventory().getLocation();
                ContainerAction containerAction = new ContainerAction(
                        containerType,
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        false
                );
                containerAction.setOwnerUUID(player.getUniqueId());
                nearbySession.addAction(containerAction);

                plugin.debug("[NEARBY-TRACKING] Container close recorded for: " + player.getName());
            }
        }
    }

    // ========== DAMAGE TYPE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageDetailed(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.isCancelled()) return;

        Player player = (Player) event.getEntity();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            DamageAction.DamageType damageType;
            switch (event.getCause()) {
                case FALL: damageType = DamageAction.DamageType.FALL; break;
                case ENTITY_ATTACK:
                case ENTITY_SWEEP_ATTACK: damageType = DamageAction.DamageType.ATTACK; break;
                case FIRE:
                case FIRE_TICK: damageType = DamageAction.DamageType.FIRE; break;
                case DROWNING: damageType = DamageAction.DamageType.DROWNING; break;
                case CONTACT: damageType = DamageAction.DamageType.CONTACT; break;
                case HOT_FLOOR: damageType = DamageAction.DamageType.HOT_FLOOR; break;
                case WITHER: damageType = DamageAction.DamageType.WITHER; break;
                case POISON: damageType = DamageAction.DamageType.POISON; break;
                case STARVATION: damageType = DamageAction.DamageType.STARVATION; break;
                case SUFFOCATION: damageType = DamageAction.DamageType.SUFFOCATION; break;
                case VOID: damageType = DamageAction.DamageType.VOID; break;
                case FLY_INTO_WALL: damageType = DamageAction.DamageType.FLY_INTO_WALL; break;
                case CRAMMING: damageType = DamageAction.DamageType.CRAMMING; break;
                case THORNS: damageType = DamageAction.DamageType.THORNS; break;
                case MAGIC: damageType = DamageAction.DamageType.MAGIC; break;
                case BLOCK_EXPLOSION:
                case ENTITY_EXPLOSION: damageType = DamageAction.DamageType.EXPLOSION; break;
                default: damageType = DamageAction.DamageType.OTHER; break;
            }

            DamageAction damageAction = new DamageAction(
                    damageType,
                    event.getFinalDamage(),
                    true
            );
            damageAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(damageAction);
        }
    }

    // ========== ENTITY INTERACTION TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            Entity target = event.getRightClicked();
            org.bukkit.Location targetLoc = target.getLocation();

            InteractEntityAction interactAction = new InteractEntityAction(
                    InteractEntityAction.InteractionType.RIGHT_CLICK,
                    target.getUniqueId(),
                    target.getType().name(),
                    targetLoc.getX(), targetLoc.getY(), targetLoc.getZ()
            );
            interactAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(interactAction);

            plugin.debug("[NEARBY-TRACKING] Entity interact recorded for: " + player.getName() +
                    " -> " + target.getType());
        }
    }

    // ========== BREED TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getBreeder() instanceof Player)) return;

        Player player = (Player) event.getBreeder();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location loc = event.getEntity().getLocation();
            BreedAction breedAction = new BreedAction(
                    event.getEntity().getType().name(),
                    loc.getX(), loc.getY(), loc.getZ()
            );
            breedAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(breedAction);

            plugin.debug("[NEARBY-TRACKING] Breed recorded for: " + player.getName() +
                    " - " + event.getEntity().getType());
        }
    }

    // ========== BLOCK IGNITE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.isCancelled()) return;
        if (event.getPlayer() == null) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            BlockIgniteAction.IgniteType igniteType;
            switch (event.getCause()) {
                case FLINT_AND_STEEL: igniteType = BlockIgniteAction.IgniteType.FLINT_AND_STEEL; break;
                case LAVA: igniteType = BlockIgniteAction.IgniteType.LAVA; break;
                case SPREAD: igniteType = BlockIgniteAction.IgniteType.SPREAD; break;
                case LIGHTNING: igniteType = BlockIgniteAction.IgniteType.LIGHTNING; break;
                default: igniteType = BlockIgniteAction.IgniteType.FLINT_AND_STEEL; break;
            }

            Block block = event.getBlock();
            BlockIgniteAction igniteAction = new BlockIgniteAction(
                    igniteType,
                    block.getX(), block.getY(), block.getZ()
            );
            igniteAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(igniteAction);

            plugin.debug("[NEARBY-TRACKING] Block ignite recorded for: " + player.getName());
        }
    }

    // ========== HANGING ENTITY TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.isCancelled()) return;
        if (event.getPlayer() == null) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            Hanging hanging = event.getEntity();
            org.bukkit.Location loc = hanging.getLocation();

            HangingAction.HangingType hangingType;
            String paintingArt = null;
            if (hanging instanceof Painting) {
                hangingType = HangingAction.HangingType.PAINTING;
                paintingArt = ((Painting) hanging).getArt().name();
            } else if (hanging instanceof GlowItemFrame) {
                hangingType = HangingAction.HangingType.GLOW_ITEM_FRAME;
            } else if (hanging instanceof ItemFrame) {
                hangingType = HangingAction.HangingType.ITEM_FRAME;
            } else {
                return;
            }

            String facing = hanging.getFacing().name();

            HangingAction hangingAction = new HangingAction(
                    HangingAction.ActionType.PLACE,
                    hangingType,
                    loc.getX(), loc.getY(), loc.getZ(),
                    facing, null, paintingArt
            );
            hangingAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(hangingAction);

            plugin.debug("[NEARBY-TRACKING] Hanging place recorded for: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getRemover() instanceof Player)) return;

        Player player = (Player) event.getRemover();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            Hanging hanging = event.getEntity();
            org.bukkit.Location loc = hanging.getLocation();

            HangingAction.HangingType hangingType;
            if (hanging instanceof Painting) {
                hangingType = HangingAction.HangingType.PAINTING;
            } else if (hanging instanceof GlowItemFrame) {
                hangingType = HangingAction.HangingType.GLOW_ITEM_FRAME;
            } else if (hanging instanceof ItemFrame) {
                hangingType = HangingAction.HangingType.ITEM_FRAME;
            } else {
                return;
            }

            HangingAction hangingAction = new HangingAction(
                    HangingAction.ActionType.BREAK,
                    hangingType,
                    loc.getX(), loc.getY(), loc.getZ(),
                    hanging.getFacing().name(), null
            );
            hangingAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(hangingAction);

            plugin.debug("[NEARBY-TRACKING] Hanging break recorded for: " + player.getName());
        }
    }

    // ========== SIGN TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            Block block = event.getBlock();
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) {
                lines[i] = event.getLine(i) != null ? event.getLine(i) : "";
            }

            SignAction signAction = new SignAction(
                    block.getX(), block.getY(), block.getZ(),
                    lines,
                    block.getType().name()
            );
            signAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(signAction);

            plugin.debug("[NEARBY-TRACKING] Sign change recorded for: " + player.getName());
        }
    }

    // ========== ARMOR CHANGE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            EquipmentAction.EquipmentSlot slot;
            switch (event.getSlotType()) {
                case HEAD: slot = EquipmentAction.EquipmentSlot.HELMET; break;
                case CHEST: slot = EquipmentAction.EquipmentSlot.CHESTPLATE; break;
                case LEGS: slot = EquipmentAction.EquipmentSlot.LEGGINGS; break;
                case FEET: slot = EquipmentAction.EquipmentSlot.BOOTS; break;
                default: return;
            }

            EquipmentAction.ItemData itemData = convertToItemData(event.getNewItem());

            EquipmentAction equipmentAction = new EquipmentAction(slot, itemData);
            equipmentAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(equipmentAction);

            plugin.debug("[NEARBY-TRACKING] Armor change (" + slot + ") recorded for: " + player.getName());
        }
    }

    // ========== BED TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location loc = event.getBed().getLocation();
            BedAction bedAction = new BedAction(
                    BedAction.ActionType.ENTER_BED,
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            );
            bedAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(bedAction);
            plugin.debug("[NEARBY-TRACKING] Bed enter recorded for: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location loc = event.getBed().getLocation();
            BedAction bedAction = new BedAction(
                    BedAction.ActionType.LEAVE_BED,
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            );
            bedAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(bedAction);
            plugin.debug("[NEARBY-TRACKING] Bed leave recorded for: " + player.getName());
        }
    }

    // ========== CRAFT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null && event.getRecipe().getResult() != null) {
            ItemStack result = event.getRecipe().getResult();
            CraftAction craftAction = new CraftAction(
                    result.getType().name(),
                    result.getAmount(),
                    event.isShiftClick()
            );
            craftAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(craftAction);
            plugin.debug("[NEARBY-TRACKING] Craft recorded for: " + player.getName() +
                    " - " + result.getType());
        }
    }

    // ========== GAMEMODE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            GameModeAction.GameMode gameMode;
            switch (event.getNewGameMode()) {
                case CREATIVE: gameMode = GameModeAction.GameMode.CREATIVE; break;
                case ADVENTURE: gameMode = GameModeAction.GameMode.ADVENTURE; break;
                case SPECTATOR: gameMode = GameModeAction.GameMode.SPECTATOR; break;
                default: gameMode = GameModeAction.GameMode.SURVIVAL; break;
            }
            GameModeAction gameModeAction = new GameModeAction(gameMode);
            gameModeAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(gameModeAction);
            plugin.debug("[NEARBY-TRACKING] GameMode change recorded for: " + player.getName());
        }
    }

    // ========== BOOK EDIT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerEditBook(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.inventory.meta.BookMeta meta = event.getNewBookMeta();
            String title = meta.hasTitle() ? meta.getTitle() : "";
            String author = meta.hasAuthor() ? meta.getAuthor() : player.getName();
            List<String> pages = meta.hasPages() ? meta.getPages() : new ArrayList<>();

            BookEditAction bookAction = new BookEditAction(title, author, pages, event.isSigning());
            bookAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(bookAction);
            plugin.debug("[NEARBY-TRACKING] Book edit recorded for: " + player.getName());
        }
    }

    // ========== ENCHANT TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnchantItem(org.bukkit.event.enchantment.EnchantItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getEnchanter();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            String itemBefore = event.getItem().getType().name();
            String itemAfter = event.getItem().getType().name(); // Same type, different enchants
            EnchantAction enchantAction = new EnchantAction(
                    itemBefore, itemAfter,
                    event.getExpLevelCost(),
                    event.whichButton()
            );
            enchantAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(enchantAction);
            plugin.debug("[NEARBY-TRACKING] Enchant recorded for: " + player.getName());
        }
    }

    // ========== ENTITY DYE TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSheepDyeWool(org.bukkit.event.entity.SheepDyeWoolEvent event) {
        if (event.isCancelled()) return;
        if (event.getPlayer() == null) return;

        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location loc = event.getEntity().getLocation();
            EntityDyeAction dyeAction = new EntityDyeAction(
                    event.getEntity().getUniqueId(),
                    "SHEEP",
                    event.getColor().name(),
                    loc.getX(), loc.getY(), loc.getZ()
            );
            dyeAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(dyeAction);
            plugin.debug("[NEARBY-TRACKING] Sheep dye recorded for: " + player.getName());
        }
    }

    // ========== PORTAL TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            org.bukkit.Location from = event.getFrom();
            org.bukkit.Location to = event.getTo();
            if (to == null) return;

            PortalAction.PortalType portalType;
            switch (event.getCause()) {
                case NETHER_PORTAL: portalType = PortalAction.PortalType.NETHER; break;
                case END_PORTAL: portalType = PortalAction.PortalType.END; break;
                case END_GATEWAY: portalType = PortalAction.PortalType.END_GATEWAY; break;
                default: return;
            }

            PortalAction portalAction = new PortalAction(
                    portalType,
                    from.getX(), from.getY(), from.getZ(), from.getWorld().getName(),
                    to.getX(), to.getY(), to.getZ(), to.getWorld().getName()
            );
            portalAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(portalAction);
            plugin.debug("[NEARBY-TRACKING] Portal recorded for: " + player.getName());
        }
    }

    // ========== FARMING TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerHarvestBlock(PlayerHarvestBlockEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (plugin.getRecordingManager().isRecording(player.getUniqueId())) return;

        RecordingSession nearbySession = findNearbyRecordingSession(player);
        if (nearbySession != null) {
            Block block = event.getHarvestedBlock();
            FarmingAction farmingAction = new FarmingAction(
                    FarmingAction.FarmingType.CROP_HARVEST,
                    block.getX(), block.getY(), block.getZ(),
                    block.getType().name()
            );
            farmingAction.setOwnerUUID(player.getUniqueId());
            nearbySession.addAction(farmingAction);
            plugin.debug("[NEARBY-TRACKING] Harvest recorded for: " + player.getName());
        }
    }

    // ========== EXPLOSION TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        org.bukkit.Location explosionLoc = event.getLocation();

        // Aktif kayıt session'larına yakınsa kaydet
        for (Map.Entry<UUID, RecordingSession> entry : plugin.getRecordingManager().getActiveRecordingSessions().entrySet()) {
            Player recordedPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (recordedPlayer == null) continue;
            if (!recordedPlayer.getWorld().equals(explosionLoc.getWorld())) continue;

            double distance = recordedPlayer.getLocation().distance(explosionLoc);
            if (distance <= NEARBY_DISTANCE) {
                ExplosionAction.ExplosionType type = ExplosionAction.ExplosionType.GENERIC;
                if (event.getEntity() instanceof TNTPrimed) type = ExplosionAction.ExplosionType.TNT;
                else if (event.getEntity() instanceof Creeper) type = ExplosionAction.ExplosionType.CREEPER;
                else if (event.getEntity() instanceof EnderCrystal) type = ExplosionAction.ExplosionType.END_CRYSTAL;
                else if (event.getEntity() instanceof Fireball) type = ExplosionAction.ExplosionType.FIREBALL;
                else if (event.getEntity() instanceof WitherSkull) type = ExplosionAction.ExplosionType.WITHER;

                ExplosionAction explosionAction = new ExplosionAction(
                        type,
                        explosionLoc.getX(), explosionLoc.getY(), explosionLoc.getZ(),
                        event.getYield(), false, !event.blockList().isEmpty()
                );
                // Patlama oyuncu-spesifik değil, ownerUUID set etme
                entry.getValue().addAction(explosionAction);
            }
        }
    }

    // ========== FALLING BLOCK TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof FallingBlock)) return;

        org.bukkit.Location blockLoc = event.getBlock().getLocation();

        for (Map.Entry<UUID, RecordingSession> entry : plugin.getRecordingManager().getActiveRecordingSessions().entrySet()) {
            Player recordedPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (recordedPlayer == null) continue;
            if (!recordedPlayer.getWorld().equals(blockLoc.getWorld())) continue;

            double distance = recordedPlayer.getLocation().distance(blockLoc);
            if (distance <= NEARBY_DISTANCE) {
                FallingBlockAction fallingAction = new FallingBlockAction(
                        event.getTo().name(),
                        blockLoc.getX(), blockLoc.getY(), blockLoc.getZ()
                );
                entry.getValue().addAction(fallingAction);
            }
        }
    }

    // ========== NOTE BLOCK TRACKING ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNotePlay(org.bukkit.event.block.NotePlayEvent event) {
        if (event.isCancelled()) return;

        org.bukkit.Location blockLoc = event.getBlock().getLocation();

        for (Map.Entry<UUID, RecordingSession> entry : plugin.getRecordingManager().getActiveRecordingSessions().entrySet()) {
            Player recordedPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (recordedPlayer == null) continue;
            if (!recordedPlayer.getWorld().equals(blockLoc.getWorld())) continue;

            double distance = recordedPlayer.getLocation().distance(blockLoc);
            if (distance <= NEARBY_DISTANCE) {
                NoteBlockAction noteAction = new NoteBlockAction(
                        blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(),
                        event.getInstrument().name(),
                        event.getNote().getId()
                );
                entry.getValue().addAction(noteAction);
            }
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Material'den BucketType'a dönüştürür
     */
    private BucketAction.BucketType getBucketType(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.contains("WATER")) return BucketAction.BucketType.WATER;
        if (name.contains("LAVA")) return BucketAction.BucketType.LAVA;
        if (name.contains("MILK")) return BucketAction.BucketType.MILK;
        if (name.contains("POWDER_SNOW")) return BucketAction.BucketType.POWDER_SNOW;
        if (name.contains("FISH") || name.contains("COD") || name.contains("SALMON") ||
            name.contains("PUFFERFISH") || name.contains("TROPICAL")) return BucketAction.BucketType.FISH;
        return null;
    }

    /**
     * InventoryType'ı ContainerType'a dönüştürür
     */
    private ContainerAction.ContainerType getContainerType(InventoryType inventoryType) {
        switch (inventoryType) {
            case CHEST: return ContainerAction.ContainerType.CHEST;
            case ENDER_CHEST: return ContainerAction.ContainerType.ENDER_CHEST;
            case SHULKER_BOX: return ContainerAction.ContainerType.SHULKER_BOX;
            case BARREL: return ContainerAction.ContainerType.BARREL;
            case HOPPER: return ContainerAction.ContainerType.HOPPER;
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER: return ContainerAction.ContainerType.FURNACE;
            case DISPENSER: return ContainerAction.ContainerType.DISPENSER;
            case DROPPER: return ContainerAction.ContainerType.DROPPER;
            default: return null;
        }
    }

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

                // DEBUG - Her 50 harekette bir log
                if (nearbySession.getActions().size() % 50 == 0) {
                    plugin.debug("[NEARBY-MOVE] Recording movement for " + player.getName() +
                            " UUID: " + player.getUniqueId() +
                            " | Distance: " + String.format("%.2f", distance) +
                            " | Total actions: " + nearbySession.getActions().size());
                }
            }
        }
    }
}
