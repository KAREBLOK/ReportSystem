package com.reportsystem.spigot.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.utils.ItemSerializer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class ReplayActionPlayer {

    private final JavaPlugin plugin;
    private final ReplayPlayer replayPlayer;

    // Değiştirilen blokları takip etmek için
    private final Map<String, Material> originalBlocks = new HashMap<>();

    // Entity flag'leri saklamak için
    private byte currentEntityFlags = 0;

    // Mount edilmiş entity'ler için takip
    private final Map<Integer, Entity> mountedEntities = new HashMap<>();

    // FishHook entity'lerini takip etmek için
    private final List<FishHook> activeFishHooks = new ArrayList<>();

    // Spawn edilen entity'leri takip etmek için (replay bitince silinecek)
    private final List<Entity> spawnedEntities = new ArrayList<>();
    // UUID -> Entity mapping (entity movement için)
    private final Map<UUID, Entity> spawnedEntitiesMap = new HashMap<>();

    public ReplayActionPlayer(JavaPlugin plugin, ReplayPlayer replayPlayer) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
    }

    /**
     * Bir action'ı oynatır
     */
    public void playAction(ReplayAction action) {
        // Debug log
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[REPLAY-DEBUG] Playing action: " + action.getClass().getSimpleName());
        }

        // ÖNEMLİ: Eğer action bir nearby player'a aitse, onu ayrı handle et
        if (!action.isMainPlayer()) {
            playNearbyPlayerAction(action);
            return;
        }

        // Ana oyuncu için normal akış
        if (action instanceof LocationAction) {
            playLocation((LocationAction) action);
        } else if (action instanceof AnimationAction) {
            playAnimation((AnimationAction) action);
        } else if (action instanceof EntityStateAction) {
            playEntityState((EntityStateAction) action);
        } else if (action instanceof EquipmentAction) {
            playEquipment((EquipmentAction) action);
        } else if (action instanceof BlockAction) {
            playBlockAction((BlockAction) action);
        } else if (action instanceof NearbyPlayerAction) {
            replayPlayer.getNpcManager().playNearbyPlayerAction((NearbyPlayerAction) action);
        } else if (action instanceof PlayerInfoAction) {
            replayPlayer.getNpcManager().updatePlayerInfo((PlayerInfoAction) action);
        } else if (action instanceof DamageAction) {
            playDamageAnimation((DamageAction) action);
        } else if (action instanceof VelocityAction) {
            playVelocity((VelocityAction) action);
        } else if (action instanceof UseItemAction) {
            playUseItem((UseItemAction) action);
        } else if (action instanceof ProjectileAction) {
            playProjectile((ProjectileAction) action);
        } else if (action instanceof PoseAction) {
            playPose((PoseAction) action);
        } else if (action instanceof DeathAction) {
            playDeath((DeathAction) action);
        } else if (action instanceof FireAction) {
            playFire((FireAction) action);
        } else if (action instanceof VehicleAction) {
            playVehicle((VehicleAction) action);
        } else if (action instanceof BedAction) {
            playBed((BedAction) action);
        } else if (action instanceof ChatAction) {
            playChat((ChatAction) action);
        } else if (action instanceof FishingAction) {
            playFishing((FishingAction) action);
        } else if (action instanceof InteractEntityAction) {
            playInteractEntity((InteractEntityAction) action);
        } else if (action instanceof TeleportAction) {
            playTeleport((TeleportAction) action);
        } else if (action instanceof HealthAction) {
            playHealth((HealthAction) action);
        } else if (action instanceof PotionEffectAction) {
            playPotionEffect((PotionEffectAction) action);
        } else if (action instanceof GameModeAction) {
            playGameMode((GameModeAction) action);
        } else if (action instanceof WeatherAction) {
            playWeather((WeatherAction) action);
        } else if (action instanceof SoundAction) {
            playSound((SoundAction) action);
        } else if (action instanceof ItemAction) {
            playItem((ItemAction) action);
        } else if (action instanceof ExplosionAction) {
            playExplosion((ExplosionAction) action);
        } else if (action instanceof EntityUpdateAction) {
            playEntityUpdate((EntityUpdateAction) action);
        } else if (action instanceof EntitySpawnAction) {
            playEntitySpawn((EntitySpawnAction) action);
        } else if (action instanceof EntityDyeAction) {
            playEntityDye((EntityDyeAction) action);
        } else if (action instanceof HangingAction) {
            playHanging((HangingAction) action);
        } else if (action instanceof FallingBlockAction) {
            playFallingBlock((FallingBlockAction) action);
        } else if (action instanceof BreedAction) {
            playBreed((BreedAction) action);
        } else if (action instanceof BucketAction) {
            playBucket((BucketAction) action);
        } else if (action instanceof NoteBlockAction) {
            playNoteBlock((NoteBlockAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.SignAction) {
            playSign((com.reportsystem.common.replay.actions.SignAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.AnvilAction) {
            playAnvil((com.reportsystem.common.replay.actions.AnvilAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.BrewAction) {
            playBrew((com.reportsystem.common.replay.actions.BrewAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.CraftAction) {
            playCraft((com.reportsystem.common.replay.actions.CraftAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.EnchantAction) {
            playEnchant((com.reportsystem.common.replay.actions.EnchantAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.UtilityBlockAction) {
            playUtilityBlock((com.reportsystem.common.replay.actions.UtilityBlockAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.BookEditAction) {
            playBookEdit((com.reportsystem.common.replay.actions.BookEditAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.PortalAction) {
            playPortal((com.reportsystem.common.replay.actions.PortalAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.FarmingAction) {
            playFarming((com.reportsystem.common.replay.actions.FarmingAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.PlayerStateAction) {
            playPlayerState((com.reportsystem.common.replay.actions.PlayerStateAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.DecorationAction) {
            playDecoration((com.reportsystem.common.replay.actions.DecorationAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.RedstoneAction) {
            playRedstone((com.reportsystem.common.replay.actions.RedstoneAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.EntityCommandAction) {
            playEntityCommand((com.reportsystem.common.replay.actions.EntityCommandAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.ContainerAction) {
            playContainer((com.reportsystem.common.replay.actions.ContainerAction) action);
        } else if (action instanceof com.reportsystem.common.replay.actions.BlockIgniteAction) {
            playBlockIgnite((com.reportsystem.common.replay.actions.BlockIgniteAction) action);
        }
    }

    /**
     * Location action'ını oynatır
     */
    private void playLocation(LocationAction action) {
        // Nearby player'a mı ait?
        if (!action.isMainPlayer()) {
            // CRITICAL DEBUG
            plugin.getLogger().warning("[PLAYBACK-NEARBY] Detected nearby player LocationAction!" +
                    " | Owner UUID: " + action.getOwnerUUID() +
                    " | Location: " + String.format("%.1f, %.1f, %.1f", action.getX(), action.getY(), action.getZ()));

            // Nearby player hareketi - NearbyPlayerAction olarak işle
            NearbyPlayerAction nearbyMove = new NearbyPlayerAction(
                    NearbyPlayerAction.ActionType.PLAYER_MOVE,
                    action.getOwnerUUID(),
                    null, // name - gerekmez
                    action.getX(),
                    action.getY(),
                    action.getZ(),
                    action.getYaw(),
                    action.getPitch(),
                    null, null, null
            );
            replayPlayer.getNpcManager().playNearbyPlayerAction(nearbyMove);
            return;
        }

        // Ana player hareketi
        Location newLocation = new Location(
                replayPlayer.getLastLocation().getWorld(),
                action.getX(),
                action.getY(),
                action.getZ(),
                action.getYaw(),
                action.getPitch()
        );

        // DEBUG: LocationAction oynatılıyor
        plugin.getLogger().info("[REPLAY-DEBUG] Playing main player LocationAction: " +
                String.format("%.1f, %.1f, %.1f", action.getX(), action.getY(), action.getZ()));

        // NPC'yi hareket ettir
        replayPlayer.getNpcManager().teleportNPC(newLocation, mountedEntities);
        replayPlayer.setLastLocation(newLocation);
    }

    /**
     * Animasyon oynatır
     */
    private void playAnimation(AnimationAction action) {
        WrapperPlayServerEntityAnimation.EntityAnimationType animationType;
        switch (action.getAnimationType()) {
            case SWING_MAIN_HAND:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
                break;
            case TAKE_DAMAGE:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.HURT;
                break;
            case SWING_OFF_HAND:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND;
                break;
            default:
                return;
        }

        replayPlayer.getNpcManager().sendAnimation(animationType);
    }

    /**
     * Entity durumunu oynatır
     */
    private void playEntityState(EntityStateAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Entity state: " + action.getStateType() +
                " = " + action.isEnabled() +
                " | Nearby: " + action.isForNearbyPlayer());

        // Nearby player için state mi?
        if (action.isForNearbyPlayer()) {
            playNearbyEntityState(action);
            return;
        }

        // Recorded player için - mevcut flag'leri koru ve güncelle
        switch (action.getStateType()) {
            case SNEAKING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x02;
                } else {
                    currentEntityFlags &= ~0x02;
                }
                break;
            case SPRINTING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x08;
                } else {
                    currentEntityFlags &= ~0x08;
                }
                break;
            case SWIMMING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x10;
                } else {
                    currentEntityFlags &= ~0x10;
                }
                break;
            case GLIDING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x80;
                } else {
                    currentEntityFlags &= ~0x80;
                }
                break;
            case FLYING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x04;
                } else {
                    currentEntityFlags &= ~0x04;
                }
                break;
            case BURNING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x01;
                } else {
                    currentEntityFlags &= ~0x01;
                }
                break;
            case INVISIBLE:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x20;
                } else {
                    currentEntityFlags &= ~0x20;
                }
                break;
            case GLOWING:
                if (action.isEnabled()) {
                    currentEntityFlags |= 0x40;
                } else {
                    currentEntityFlags &= ~0x40;
                }
                break;
        }

        replayPlayer.getNpcManager().updateEntityFlags(currentEntityFlags);
    }

    /**
     * Nearby player için entity state oynatır
     */
    private void playNearbyEntityState(EntityStateAction action) {
        // Nearby player entity ID'yi bul
        Integer nearbyEntityId = replayPlayer.getNpcManager().getNearbyPlayerEntityId(action.getEntityUUID());

        if (nearbyEntityId == null) {
            plugin.getLogger().warning("[REPLAY-DEBUG] Nearby player entity ID not found for UUID: " + action.getEntityUUID());
            return;
        }

        // Entity flags hesapla
        byte entityFlags = 0;

        switch (action.getStateType()) {
            case SNEAKING:
                if (action.isEnabled()) entityFlags |= 0x02;
                break;
            case SPRINTING:
                if (action.isEnabled()) entityFlags |= 0x08;
                break;
            case SWIMMING:
                if (action.isEnabled()) entityFlags |= 0x10;
                break;
            case GLIDING:
                if (action.isEnabled()) entityFlags |= 0x80;
                break;
            case FLYING:
                if (action.isEnabled()) entityFlags |= 0x04;
                break;
            case BURNING:
                if (action.isEnabled()) entityFlags |= 0x01;
                break;
            case INVISIBLE:
                if (action.isEnabled()) entityFlags |= 0x20;
                break;
            case GLOWING:
                if (action.isEnabled()) entityFlags |= 0x40;
                break;
        }

        // Metadata paketi gönder
        replayPlayer.getNpcManager().updateNearbyPlayerEntityFlags(nearbyEntityId, entityFlags);

        plugin.getLogger().info("[REPLAY-DEBUG] Nearby player state updated: EntityID=" + nearbyEntityId +
                " | State=" + action.getStateType() + " | Enabled=" + action.isEnabled());
    }

    /**
     * Equipment action'ını oynatır
     */
    private void playEquipment(EquipmentAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing equipment: " + action.getSlot() +
                " - " + (action.getItemData() != null ? action.getItemData().getMaterial() : "EMPTY"));

        EquipmentSlot slot = null;
        ItemStack item = null;

        // Slot'u belirle
        switch (action.getSlot()) {
            case MAIN_HAND:
                slot = EquipmentSlot.MAIN_HAND;
                break;
            case OFF_HAND:
                slot = EquipmentSlot.OFF_HAND;
                break;
            case HELMET:
                slot = EquipmentSlot.HELMET;
                break;
            case CHESTPLATE:
                slot = EquipmentSlot.CHEST_PLATE;
                break;
            case LEGGINGS:
                slot = EquipmentSlot.LEGGINGS;
                break;
            case BOOTS:
                slot = EquipmentSlot.BOOTS;
                break;
        }

        // Item'ı oluştur
        if (action.getItemData() != null) {
            try {
                if (action.getItemData().getItemStackData() != null) {
                    item = ItemSerializer.itemStackFromBytes(
                            action.getItemData().getItemStackData()
                    );
                } else {
                    Material material = Material.valueOf(action.getItemData().getMaterial());
                    item = new ItemStack(material, action.getItemData().getAmount());
                    item.setDurability(action.getItemData().getDurability());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Failed to create item: " + e.getMessage());
            }
        }

        if (slot != null) {
            List<Equipment> equipmentList = new ArrayList<>();
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem = null;

            if (item != null && item.getType() != Material.AIR) {
                // Özel/complex item'ları skip et veya stone ile değiştir
                Material mat = item.getType();
                if (mat == Material.SUSPICIOUS_SAND ||
                    mat == Material.SUSPICIOUS_GRAVEL ||
                    mat.name().contains("COMMAND") ||
                    mat.name().contains("STRUCTURE") ||
                    mat.name().contains("JIGSAW")) {
                    plugin.getLogger().info("[REPLAY-DEBUG] Skipping complex equipment item: " + mat.name());
                    // STONE ile değiştir
                    item = new ItemStack(Material.STONE, item.getAmount());
                }

                try {
                    String materialName = item.getType().name().toLowerCase();
                    com.github.retrooper.packetevents.protocol.item.type.ItemType itemType = null;

                    // Önce direkt minecraft: prefix ile dene
                    itemType = com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName(
                            "minecraft:" + materialName
                    );

                    // Bulunamazsa özel mapping kullan
                    if (itemType == null) {
                        itemType = getMappedItemType(item.getType());
                    }

                    // Hala bulunamazsa varsayılan kullan
                    if (itemType == null) {
                        plugin.getLogger().warning("[REPLAY-DEBUG] Unknown item type: " + item.getType().name() +
                                ", using STONE as fallback");
                        itemType = com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE;
                    }

                    if (itemType != null) {
                        packetItem = com.github.retrooper.packetevents.protocol.item.ItemStack.builder()
                                .type(itemType)
                                .amount(item.getAmount())
                                .build();

                        plugin.getLogger().info("[REPLAY-DEBUG] Created packet item: " + itemType.getName().toString() +
                                " x" + item.getAmount());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("[REPLAY-DEBUG] Error creating packet item: " + e.getMessage());
                    e.printStackTrace();
                    packetItem = null;
                }
            }

            equipmentList.add(new Equipment(slot, packetItem));
            replayPlayer.getNpcManager().sendEquipment(equipmentList);
        }
    }

    /**
     * Material'i PacketEvents ItemType'a çevirir
     */
    private com.github.retrooper.packetevents.protocol.item.type.ItemType getMappedItemType(Material material) {
        // Önce direkt minecraft: prefix ile dene
        String materialName = material.name().toLowerCase();
        com.github.retrooper.packetevents.protocol.item.type.ItemType itemType =
                com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName("minecraft:" + materialName);

        if (itemType != null) {
            return itemType;
        }

        // Manuel mapping
        switch (material) {
            // İksirler
            case POTION:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.POTION;
            case SPLASH_POTION:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.SPLASH_POTION;
            case LINGERING_POTION:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.LINGERING_POTION;

            // Olta
            case FISHING_ROD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.FISHING_ROD;

            // Makas
            case SHEARS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.SHEARS;

            // Tekneler
            case OAK_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.OAK_BOAT;
            case SPRUCE_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.SPRUCE_BOAT;
            case BIRCH_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BIRCH_BOAT;
            case JUNGLE_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.JUNGLE_BOAT;
            case ACACIA_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.ACACIA_BOAT;
            case DARK_OAK_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DARK_OAK_BOAT;
            case CHERRY_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.CHERRY_BOAT;
            case MANGROVE_BOAT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.MANGROVE_BOAT;
            case BAMBOO_RAFT:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BAMBOO_RAFT;

            // Diğer önemli itemler
            case FLINT_AND_STEEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.FLINT_AND_STEEL;

            // Aletler - Taş
            case STONE_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE_PICKAXE;
            case STONE_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE_AXE;
            case STONE_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE_SHOVEL;
            case STONE_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE_HOE;
            case STONE_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE_SWORD;

            // Aletler - Demir
            case IRON_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_PICKAXE;
            case IRON_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_AXE;
            case IRON_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_SHOVEL;
            case IRON_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_HOE;
            case IRON_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_SWORD;

            // Aletler - Elmas
            case DIAMOND_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_PICKAXE;
            case DIAMOND_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_AXE;
            case DIAMOND_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_SHOVEL;
            case DIAMOND_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_HOE;
            case DIAMOND_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_SWORD;

            // Aletler - Netherite
            case NETHERITE_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_PICKAXE;
            case NETHERITE_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_AXE;
            case NETHERITE_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_SHOVEL;
            case NETHERITE_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_HOE;
            case NETHERITE_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_SWORD;

            // Aletler - Altın
            case GOLDEN_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_PICKAXE;
            case GOLDEN_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_AXE;
            case GOLDEN_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_SHOVEL;
            case GOLDEN_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_HOE;
            case GOLDEN_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_SWORD;

            // Aletler - Tahta
            case WOODEN_PICKAXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WOODEN_PICKAXE;
            case WOODEN_AXE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WOODEN_AXE;
            case WOODEN_SHOVEL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WOODEN_SHOVEL;
            case WOODEN_HOE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WOODEN_HOE;
            case WOODEN_SWORD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WOODEN_SWORD;

            // Zırhlar - Demir
            case IRON_HELMET:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_HELMET;
            case IRON_CHESTPLATE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_CHESTPLATE;
            case IRON_LEGGINGS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_LEGGINGS;
            case IRON_BOOTS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.IRON_BOOTS;

            // Zırhlar - Elmas
            case DIAMOND_HELMET:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_HELMET;
            case DIAMOND_CHESTPLATE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_CHESTPLATE;
            case DIAMOND_LEGGINGS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_LEGGINGS;
            case DIAMOND_BOOTS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND_BOOTS;

            // Zırhlar - Netherite
            case NETHERITE_HELMET:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_HELMET;
            case NETHERITE_CHESTPLATE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_CHESTPLATE;
            case NETHERITE_LEGGINGS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_LEGGINGS;
            case NETHERITE_BOOTS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.NETHERITE_BOOTS;

            // Zırhlar - Altın
            case GOLDEN_HELMET:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_HELMET;
            case GOLDEN_CHESTPLATE:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_CHESTPLATE;
            case GOLDEN_LEGGINGS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_LEGGINGS;
            case GOLDEN_BOOTS:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GOLDEN_BOOTS;

            // Bow ve Crossbow
            case BOW:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BOW;
            case CROSSBOW:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.CROSSBOW;

            // Shield
            case SHIELD:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.SHIELD;

            // Yün - Tüm renkler
            case WHITE_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.WHITE_WOOL;
            case ORANGE_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.ORANGE_WOOL;
            case MAGENTA_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.MAGENTA_WOOL;
            case LIGHT_BLUE_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.LIGHT_BLUE_WOOL;
            case YELLOW_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.YELLOW_WOOL;
            case LIME_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.LIME_WOOL;
            case PINK_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.PINK_WOOL;
            case GRAY_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GRAY_WOOL;
            case LIGHT_GRAY_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.LIGHT_GRAY_WOOL;
            case CYAN_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.CYAN_WOOL;
            case PURPLE_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.PURPLE_WOOL;
            case BLUE_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BLUE_WOOL;
            case BROWN_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BROWN_WOOL;
            case GREEN_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.GREEN_WOOL;
            case RED_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.RED_WOOL;
            case BLACK_WOOL:
                return com.github.retrooper.packetevents.protocol.item.type.ItemTypes.BLACK_WOOL;

            default:
                plugin.getLogger().warning("[REPLAY-DEBUG] Unknown item type mapping for: " + material.name());
                return null;
        }
    }

    /**
     * Blok aksiyonlarını oynatır
     */
    private void playBlockAction(BlockAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Block action: " + action.getActionType() +
                " at " + action.getX() + "," + action.getY() + "," + action.getZ());

        Location blockLoc = new Location(
                replayPlayer.getLastLocation().getWorld(),
                action.getX(), action.getY(), action.getZ()
        );

        switch (action.getActionType()) {
            case START_BREAKING:
                // Blok kırma animasyonu başlat
                for (int stage = 0; stage <= 9; stage++) {
                    final int currentStage = stage;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        replayPlayer.getNpcManager().sendBlockBreakAnimation(
                                action.getX(), action.getY(), action.getZ(), currentStage
                        );
                    }, stage * 2L);
                }
                break;

            case BREAK_PROGRESS:
                replayPlayer.getNpcManager().sendBlockBreakAnimation(
                        action.getX(), action.getY(), action.getZ(), action.getStage()
                );
                break;

            case STOP_BREAKING:
                replayPlayer.getNpcManager().sendBlockBreakAnimation(
                        action.getX(), action.getY(), action.getZ(), -1
                );

                // Bloğu sadece izleyiciler için kır
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!replayPlayer.getViewers().isEmpty()) {
                        String blockKey = action.getX() + "," + action.getY() + "," + action.getZ();
                        if (!originalBlocks.containsKey(blockKey)) {
                            originalBlocks.put(blockKey, blockLoc.getBlock().getType());
                        }

                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                        }

                        // Ses ve parçacık efektleri
                        playBlockBreakEffects(blockLoc);
                    }
                });
                break;

            case PLACE_BLOCK:
                plugin.getLogger().info("[REPLAY-DEBUG] Block place at " +
                        action.getX() + "," + action.getY() + "," + action.getZ() +
                        " type: " + action.getBlockType());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!replayPlayer.getViewers().isEmpty() && action.getBlockType() != null) {
                        String blockKey = action.getX() + "," + action.getY() + "," + action.getZ();
                        if (!originalBlocks.containsKey(blockKey)) {
                            originalBlocks.put(blockKey, blockLoc.getBlock().getType());
                        }

                        try {
                            // Parse block data (format: "MATERIAL" or "MATERIAL:property1:property2...")
                            String[] parts = action.getBlockType().split(":");
                            Material material = Material.valueOf(parts[0]);

                            // Özel bloklar için sendBlockChange düzgün çalışmıyor - skip et
                            if (material == Material.SUSPICIOUS_SAND ||
                                material == Material.SUSPICIOUS_GRAVEL ||
                                material.name().contains("COMMAND") || // Command blocks
                                material.name().contains("STRUCTURE") || // Structure blocks
                                material.name().contains("JIGSAW") || // Jigsaw blocks
                                material.name().contains("SPAWNER")) { // Spawners
                                plugin.getLogger().info("[REPLAY-DEBUG] Skipping complex block: " + material.name());
                                return;
                            }

                            org.bukkit.block.data.BlockData blockData = material.createBlockData();

                            // Özel blok tipleri için property'leri uygula
                            if (parts.length > 1) {
                                if (blockData instanceof org.bukkit.block.data.type.Bed && parts.length >= 3) {
                                    // Format: "RED_BED:NORTH:HEAD"
                                    org.bukkit.block.data.type.Bed bed = (org.bukkit.block.data.type.Bed) blockData;
                                    org.bukkit.block.BlockFace facing = org.bukkit.block.BlockFace.valueOf(parts[1]);
                                    org.bukkit.block.data.type.Bed.Part part = org.bukkit.block.data.type.Bed.Part.valueOf(parts[2]);

                                    bed.setFacing(facing);
                                    bed.setPart(part);

                                    // Yatak 2 bloktan oluşur - diğer yarısını da yerleştir
                                    org.bukkit.block.data.type.Bed.Part otherPart = part == org.bukkit.block.data.type.Bed.Part.HEAD ?
                                            org.bukkit.block.data.type.Bed.Part.FOOT : org.bukkit.block.data.type.Bed.Part.HEAD;

                                    // Diğer bloğun lokasyonunu hesapla
                                    org.bukkit.block.Block otherBlock;
                                    if (part == org.bukkit.block.data.type.Bed.Part.FOOT) {
                                        // FOOT yerleştiriliyorsa, HEAD facing yönünde
                                        otherBlock = blockLoc.getBlock().getRelative(facing);
                                    } else {
                                        // HEAD yerleştiriliyorsa, FOOT facing'in tersi yönünde
                                        otherBlock = blockLoc.getBlock().getRelative(facing.getOppositeFace());
                                    }

                                    // Diğer yarıyı oluştur
                                    org.bukkit.block.data.type.Bed otherHalf = (org.bukkit.block.data.type.Bed) material.createBlockData();
                                    otherHalf.setFacing(facing);
                                    otherHalf.setPart(otherPart);

                                    // Her iki bloğu da viewer'lara gönder
                                    for (Player viewer : replayPlayer.getViewers()) {
                                        try {
                                            viewer.sendBlockChange(blockLoc, bed);
                                            viewer.sendBlockChange(otherBlock.getLocation(), otherHalf);
                                        } catch (Exception e) {
                                            plugin.getLogger().warning("[REPLAY-DEBUG] Failed to send bed block change to " + viewer.getName() + ": " + e.getMessage());
                                        }
                                    }

                                    plugin.getLogger().info("[REPLAY-DEBUG] Bed placed: " + part + " at " + blockLoc +
                                            ", other half: " + otherPart + " at " + otherBlock.getLocation());

                                    return; // Skip normal block placement
                                } else if (blockData instanceof org.bukkit.block.data.type.Door && parts.length >= 4) {
                                    // Format: "OAK_DOOR:NORTH:BOTTOM:LEFT"
                                    org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) blockData;
                                    door.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                    door.setHalf(org.bukkit.block.data.Bisected.Half.valueOf(parts[2]));
                                    door.setHinge(org.bukkit.block.data.type.Door.Hinge.valueOf(parts[3]));
                                } else if (blockData instanceof org.bukkit.block.data.type.Stairs && parts.length >= 3) {
                                    // Format: "OAK_STAIRS:NORTH:BOTTOM"
                                    org.bukkit.block.data.type.Stairs stairs = (org.bukkit.block.data.type.Stairs) blockData;
                                    stairs.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                    stairs.setHalf(org.bukkit.block.data.Bisected.Half.valueOf(parts[2]));
                                } else if (blockData instanceof org.bukkit.block.data.Directional && parts.length >= 2) {
                                    // Format: "FURNACE:NORTH"
                                    org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) blockData;
                                    directional.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                }
                            }

                            for (Player viewer : replayPlayer.getViewers()) {
                                try {
                                    viewer.sendBlockChange(blockLoc, blockData);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("[REPLAY-DEBUG] Failed to send block change to " + viewer.getName() + ": " + e.getMessage());
                                }
                            }

                            // Ses efekti
                            playBlockPlaceSound(blockLoc, material);
                        } catch (Exception e) {
                            plugin.getLogger().warning("[REPLAY-DEBUG] Invalid block type: " + action.getBlockType());
                            e.printStackTrace();
                        }
                    }
                });
                break;

            case INTERACT_BLOCK:
                plugin.getLogger().info("[REPLAY-DEBUG] Block interact: " + action.getBlockType() +
                        " at " + action.getX() + "," + action.getY() + "," + action.getZ());

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!replayPlayer.getViewers().isEmpty()) {
                        org.bukkit.block.Block block = blockLoc.getBlock();
                        org.bukkit.block.data.BlockData blockData = block.getBlockData();

                        // Swing animasyonu
                        replayPlayer.getNpcManager().sendAnimation(
                                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
                        );

                        // Etkileşim tipine göre blok state değiştir
                        if (action.getBlockType() != null) {
                            String interactionType = action.getBlockType();

                            if (interactionType.contains("DOOR")) {
                                // Kapı aç/kapat
                                if (blockData instanceof org.bukkit.block.data.Openable) {
                                    org.bukkit.block.data.Openable openable =
                                        (org.bukkit.block.data.Openable) blockData.clone();

                                    // Action tipine göre direkt set yap (toggle değil!)
                                    boolean shouldBeOpen = interactionType.equals("DOOR_OPEN");
                                    openable.setOpen(shouldBeOpen);

                                    // Viewer'lara göster
                                    for (Player viewer : replayPlayer.getViewers()) {
                                        viewer.sendBlockChange(blockLoc, openable);
                                    }

                                    // Ses efekti
                                    Sound doorSound = shouldBeOpen ?
                                        Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;

                                    if (block.getType().name().contains("IRON")) {
                                        doorSound = shouldBeOpen ?
                                            Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
                                    }

                                    blockLoc.getWorld().playSound(blockLoc, doorSound, 1.0f, 1.0f);

                                    plugin.getLogger().info("[REPLAY-DEBUG] Door state changed: " +
                                        (shouldBeOpen ? "OPEN" : "CLOSED"));
                                }
                            } else if (interactionType.contains("TRAPDOOR")) {
                                if (blockData instanceof org.bukkit.block.data.Openable) {
                                    org.bukkit.block.data.Openable openable =
                                        (org.bukkit.block.data.Openable) blockData.clone();

                                    // Action tipine göre direkt set yap (toggle değil!)
                                    boolean shouldBeOpen = interactionType.equals("TRAPDOOR_OPEN");
                                    openable.setOpen(shouldBeOpen);

                                    for (Player viewer : replayPlayer.getViewers()) {
                                        viewer.sendBlockChange(blockLoc, openable);
                                    }

                                    Sound trapdoorSound = shouldBeOpen ?
                                        Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;

                                    if (block.getType().name().contains("IRON")) {
                                        trapdoorSound = shouldBeOpen ?
                                            Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE;
                                    }

                                    blockLoc.getWorld().playSound(blockLoc, trapdoorSound, 1.0f, 1.0f);
                                }
                            } else if (interactionType.contains("GATE")) {
                                if (blockData instanceof org.bukkit.block.data.Openable) {
                                    org.bukkit.block.data.Openable openable =
                                        (org.bukkit.block.data.Openable) blockData.clone();

                                    // Action tipine göre direkt set yap (toggle değil!)
                                    boolean shouldBeOpen = interactionType.equals("GATE_OPEN");
                                    openable.setOpen(shouldBeOpen);

                                    for (Player viewer : replayPlayer.getViewers()) {
                                        viewer.sendBlockChange(blockLoc, openable);
                                    }

                                    Sound gateSound = shouldBeOpen ?
                                        Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
                                    blockLoc.getWorld().playSound(blockLoc, gateSound, 1.0f, 1.0f);
                                }
                            } else if (interactionType.contains("BUTTON")) {
                                blockLoc.getWorld().playSound(blockLoc, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.3f, 0.6f);
                                // Particle efekti
                                blockLoc.getWorld().spawnParticle(Particle.CRIT,
                                    blockLoc.clone().add(0.5, 0.5, 0.5), 3, 0.1, 0.1, 0.1, 0);
                            } else if (interactionType.contains("LEVER")) {
                                if (blockData instanceof org.bukkit.block.data.Powerable) {
                                    org.bukkit.block.data.Powerable lever =
                                        (org.bukkit.block.data.Powerable) blockData.clone();

                                    // Lever durumunu toggle yap
                                    lever.setPowered(!lever.isPowered());

                                    for (Player viewer : replayPlayer.getViewers()) {
                                        viewer.sendBlockChange(blockLoc, lever);
                                    }

                                    blockLoc.getWorld().playSound(blockLoc, Sound.BLOCK_LEVER_CLICK, 0.3f, 0.6f);
                                }
                            }
                        }
                    }
                });
                break;
        }
    }

    /**
     * Blok kırma efektleri
     */
    private void playBlockBreakEffects(Location location) {
        org.bukkit.block.Block block = location.getBlock();
        Sound breakSound = Sound.BLOCK_STONE_BREAK;

        if (block.getType().name().contains("WOOD") || block.getType().name().contains("LOG")) {
            breakSound = Sound.BLOCK_WOOD_BREAK;
        } else if (block.getType().name().contains("GRASS") || block.getType().name().contains("DIRT")) {
            breakSound = Sound.BLOCK_GRASS_BREAK;
        } else if (block.getType().name().contains("SAND")) {
            breakSound = Sound.BLOCK_SAND_BREAK;
        } else if (block.getType().name().contains("GLASS")) {
            breakSound = Sound.BLOCK_GLASS_BREAK;
        }

        location.getWorld().playSound(location.add(0.5, 0.5, 0.5), breakSound, 1.0f, 1.0f);

        location.getWorld().spawnParticle(
                Particle.BLOCK,
                location,
                20,
                0.3, 0.3, 0.3,
                0.05,
                location.getBlock().getBlockData()
        );
    }

    /**
     * Blok yerleştirme sesi
     */
    private void playBlockPlaceSound(Location location, Material material) {
        Sound placeSound = Sound.BLOCK_STONE_PLACE;

        if (material.name().contains("WOOD") || material.name().contains("LOG")) {
            placeSound = Sound.BLOCK_WOOD_PLACE;
        } else if (material.name().contains("WOOL")) {
            placeSound = Sound.BLOCK_WOOL_PLACE;
        } else if (material.name().contains("GRASS") || material.name().contains("DIRT")) {
            placeSound = Sound.BLOCK_GRASS_PLACE;
        } else if (material.name().contains("SAND")) {
            placeSound = Sound.BLOCK_SAND_PLACE;
        }

        location.getWorld().playSound(location.add(0.5, 0.5, 0.5), placeSound, 1.0f, 1.0f);
    }

    /**
     * Hasar animasyonunu oynatır
     */
    private void playDamageAnimation(DamageAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Damage animation: " + action.getDamageType() +
                " amount: " + action.getDamageAmount());

        replayPlayer.getNpcManager().sendAnimation(
                WrapperPlayServerEntityAnimation.EntityAnimationType.HURT
        );

        if (action.shouldPlaySound()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getLastLocation();
                if (!replayPlayer.getViewers().isEmpty() && loc != null) {
                    Sound damageSound = Sound.ENTITY_PLAYER_HURT;

                    switch (action.getDamageType()) {
                        case FALL:
                            if (action.getDamageAmount() > 3) {
                                damageSound = Sound.ENTITY_PLAYER_BIG_FALL;
                            } else {
                                damageSound = Sound.ENTITY_PLAYER_SMALL_FALL;
                            }
                            break;
                        case FIRE:
                            damageSound = Sound.ENTITY_PLAYER_HURT_ON_FIRE;
                            break;
                        case DROWNING:
                            damageSound = Sound.ENTITY_PLAYER_HURT_DROWN;
                            break;
                    }

                    loc.getWorld().playSound(loc, damageSound, 1.0f, 1.0f);

                    // Hasar parçacıkları
                    loc.getWorld().spawnParticle(
                            Particle.DAMAGE_INDICATOR,
                            loc.clone().add(0, 1, 0),
                            (int) Math.max(5, action.getDamageAmount()),
                            0.2, 0.5, 0.2,
                            0.1
                    );
                }
            });
        }
    }

    /**
     * Velocity'yi oynatır
     */
    private void playVelocity(VelocityAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing velocity: " +
                String.format("%.2f, %.2f, %.2f", action.getVelocityX(), action.getVelocityY(), action.getVelocityZ()));

        replayPlayer.getNpcManager().sendVelocity(
                action.getVelocityX(), action.getVelocityY(), action.getVelocityZ()
        );
    }

    /**
     * UseItem action'ını oynatır
     */
    private void playUseItem(UseItemAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing use item: " + action.getUseType() +
                " started=" + action.isStarted());

        List<EntityData<?>> metadata = new ArrayList<>();

        if (action.isStarted()) {
            currentEntityFlags |= 0x10; // Using item flag
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, currentEntityFlags));
            metadata.add(new EntityData(8, EntityDataTypes.BYTE,
                    (byte)(action.isMainHand() ? 0x01 : 0x02)));
        } else {
            currentEntityFlags &= ~0x10;
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, currentEntityFlags));
        }

        replayPlayer.getNpcManager().sendMetadata(metadata);

        // Özel durumlar için ekstra efektler
        if (action.getUseType() == UseItemAction.UseType.FOOD_EAT && !action.isStarted()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getLastLocation();
                if (!replayPlayer.getViewers().isEmpty() && loc != null) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
                }
            });
        }
    }

    /**
     * Projectile action'ını oynatır
     */
    private void playProjectile(ProjectileAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing projectile: " + action.getType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getLastLocation();
            if (!replayPlayer.getViewers().isEmpty() && loc != null) {
                org.bukkit.World world = loc.getWorld();
                Location spawnLoc = loc.clone().add(0, 1.5, 0);

                spawnLoc.setYaw(action.getYaw());
                spawnLoc.setPitch(action.getPitch());

                Vector velocity = new Vector(
                        action.getVelocityX(),
                        action.getVelocityY(),
                        action.getVelocityZ()
                );

                // Projectile spawn et (potionData ile)
                spawnProjectile(world, spawnLoc, velocity, action.getType(), action.getPotionData());

                // Atış sesi
                world.playSound(spawnLoc, getSoundForProjectile(action.getType()), 1.0f, 1.0f);
            }
        });
    }

    /**
     * Projectile spawn eder - potionData parametreli versiyon
     */
    private void spawnProjectile(org.bukkit.World world, Location location, Vector velocity, ProjectileAction.ProjectileType type, String potionData) {
        // REPLAY_PROJECTILE metadata key - bu projectile'ların gerçek oyuncuları etkilememesi için
        final String REPLAY_META = "REPLAY_PROJECTILE";

        switch (type) {
            case ARROW:
                Arrow arrow = world.spawnArrow(location, velocity, (float)velocity.length(), 0);
                arrow.setDamage(0); // Hasar vermesin
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED); // Alınamasın
                arrow.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                scheduleRemove(arrow, 100L); // 5 saniye sonra kaldır
                break;
            case SNOWBALL:
                Snowball snowball = world.spawn(location, Snowball.class);
                snowball.setVelocity(velocity);
                snowball.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                scheduleRemove(snowball, 100L);
                break;
            case ENDER_PEARL:
                // Ender pearl teleport edebilir, sadece görsel efekt için ses çal
                world.playSound(location, Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);
                // Gerçek pearl spawn etme - tehlikeli
                break;
            case EGG:
                Egg egg = world.spawn(location, Egg.class);
                egg.setVelocity(velocity);
                egg.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                scheduleRemove(egg, 100L);
                break;
            case FISHING_HOOK:
                world.playSound(location, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);
                FishHook fishHook = world.spawn(location, FishHook.class);
                fishHook.setVelocity(velocity);
                fishHook.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                scheduleRemove(fishHook, 60L);
                break;
            case EXPERIENCE_BOTTLE:
                // XP bottle gerçek XP verebilir, sadece ses çal
                world.playSound(location, Sound.ENTITY_EXPERIENCE_BOTTLE_THROW, 1.0f, 1.0f);
                break;
            case SPLASH_POTION:
            case LINGERING_POTION:
                // Potionlar efekt verebilir, sadece ses çal
                world.playSound(location, Sound.ENTITY_SPLASH_POTION_THROW, 1.0f, 1.0f);
                break;
            case TRIDENT:
                Trident trident = world.spawn(location, Trident.class);
                trident.setVelocity(velocity);
                trident.setDamage(0);
                trident.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                trident.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                scheduleRemove(trident, 100L);
                break;
            case FIREWORK_ROCKET:
                // Firework patlayabilir ve hasar verebilir, sadece ses çal
                world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                break;
        }
    }

    /**
     * Entity'yi belirli süre sonra kaldırır
     */
    private void scheduleRemove(org.bukkit.entity.Entity entity, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.remove();
            }
        }, ticks);
    }

    /**
     * Projectile spawn eder - eski versiyon (backward compatibility)
     */
    private void spawnProjectile(org.bukkit.World world, Location location, Vector velocity, ProjectileAction.ProjectileType type) {
        spawnProjectile(world, location, velocity, type, null);
    }

    /**
     * Projectile tipi için ses döndürür
     */
    private Sound getSoundForProjectile(ProjectileAction.ProjectileType type) {
        switch (type) {
            case ARROW: return Sound.ENTITY_ARROW_SHOOT;
            case SNOWBALL:
            case EGG: return Sound.ENTITY_SNOWBALL_THROW;
            case ENDER_PEARL: return Sound.ENTITY_ENDER_PEARL_THROW;
            case EXPERIENCE_BOTTLE:
            case SPLASH_POTION: return Sound.ENTITY_SPLASH_POTION_THROW;
            case TRIDENT: return Sound.ITEM_TRIDENT_THROW;
            case FISHING_HOOK: return Sound.ENTITY_FISHING_BOBBER_THROW;
            default: return Sound.ENTITY_SNOWBALL_THROW;
        }
    }

    /**
     * Pose action'ını oynatır
     */
    private void playPose(PoseAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing pose: " + action.getPoseType());

        EntityPose pose = EntityPose.STANDING;

        switch (action.getPoseType()) {
            case FALL_FLYING:
                pose = EntityPose.FALL_FLYING;
                break;
            case SLEEPING:
                pose = EntityPose.SLEEPING;
                break;
            case SWIMMING:
                pose = EntityPose.SWIMMING;
                break;
            case SPIN_ATTACK:
                pose = EntityPose.SPIN_ATTACK;
                break;
            case SNEAKING:
                pose = EntityPose.CROUCHING;
                break;
            case DYING:
                pose = EntityPose.DYING;
                break;
            case STANDING:
            default:
                pose = EntityPose.STANDING;
                break;
        }

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, pose));
        replayPlayer.getNpcManager().sendMetadata(metadata);
    }

    /**
     * Death action'ını oynatır
     */
    private void playDeath(DeathAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing death: " + action.getDeathMessage());

        // Ölüm animasyonu
        replayPlayer.getNpcManager().sendEntityStatus((byte) 3);

        // Ölüm mesajı
        if (action.getDeathMessage() != null && !action.getDeathMessage().isEmpty()) {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§c" + action.getDeathMessage());
            }
        }

        // Ölüm pozu
        playPose(new PoseAction(PoseAction.PoseType.DYING));

        // Ölüm efektleri
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!replayPlayer.getViewers().isEmpty() && replayPlayer.getLastLocation() != null) {
                Location deathLoc = new Location(replayPlayer.getLastLocation().getWorld(),
                        action.getDeathX(), action.getDeathY(), action.getDeathZ());

                deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

                deathLoc.getWorld().spawnParticle(
                        Particle.CLOUD,
                        deathLoc.clone().add(0, 1, 0),
                        20, 0.3, 0.5, 0.3, 0.05
                );
            }
        });
    }

    /**
     * Fire action'ını oynatır
     */
    private void playFire(FireAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing fire: " + action.isOnFire() +
                " ticks: " + action.getFireTicks());

        List<EntityData<?>> metadata = new ArrayList<>();

        if (action.isOnFire()) {
            currentEntityFlags |= 0x01;
        } else {
            currentEntityFlags &= ~0x01;
        }

        metadata.add(new EntityData(0, EntityDataTypes.BYTE, currentEntityFlags));

        if (action.getFireTicks() > 0) {
            metadata.add(new EntityData(1, EntityDataTypes.INT, action.getFireTicks()));
        }

        replayPlayer.getNpcManager().sendMetadata(metadata);

        // Yanma efektleri
        if (action.isOnFire()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getLastLocation();
                if (!replayPlayer.getViewers().isEmpty() && loc != null) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.7f, 1.0f);

                    loc.getWorld().spawnParticle(
                            Particle.FLAME,
                            loc.clone().add(0, 0.5, 0),
                            10, 0.2, 0.3, 0.2, 0.02
                    );
                }
            });
        }
    }

    /**
     * Vehicle action'ını oynatır
     */
    private void playVehicle(VehicleAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing vehicle: " + action.getActionType() +
                " type: " + action.getVehicleType());

        if (action.getActionType() == VehicleAction.ActionType.PLACE) {
            // Vehicle'ı sadece spawn et, mount etme
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getLastLocation();
                if (loc != null) {
                    Location vehicleLoc = new Location(loc.getWorld(),
                            action.getVehicleX(), action.getVehicleY(), action.getVehicleZ());

                    Entity vehicle = null;
                    try {
                        switch (action.getVehicleType()) {
                            case MINECART:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Minecart.class);
                                break;
                            case BOAT:
                                Boat boat = vehicleLoc.getWorld().spawn(vehicleLoc, Boat.class);
                                boat.setBoatType(Boat.Type.OAK);
                                vehicle = boat;
                                break;
                            case HORSE:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Horse.class);
                                break;
                            case PIG:
                                Pig pig = vehicleLoc.getWorld().spawn(vehicleLoc, Pig.class);
                                pig.setSaddle(true);
                                vehicle = pig;
                                break;
                            case STRIDER:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Strider.class);
                                break;
                            case LLAMA:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Llama.class);
                                break;
                            case CAMEL:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Camel.class);
                                break;
                        }

                        if (vehicle != null) {
                            // Spawn edilen vehicle'ı track et (replay bitince silinecek)
                            spawnedEntities.add(vehicle);

                            plugin.getLogger().info("[REPLAY-DEBUG] Vehicle placed: " +
                                    action.getVehicleType() + " at " + vehicleLoc);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[REPLAY-DEBUG] Error placing vehicle: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } else if (action.getActionType() == VehicleAction.ActionType.MOUNT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getLastLocation();
                if (!replayPlayer.getViewers().isEmpty() && loc != null) {
                    Location vehicleLoc = new Location(loc.getWorld(),
                            action.getVehicleX(), action.getVehicleY(), action.getVehicleZ());

                    Entity vehicle = null;
                    try {
                        switch (action.getVehicleType()) {
                            case MINECART:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Minecart.class);
                                break;
                            case BOAT:
                                Boat boat = vehicleLoc.getWorld().spawn(vehicleLoc, Boat.class);
                                boat.setBoatType(Boat.Type.OAK);
                                vehicle = boat;
                                break;
                            case HORSE:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Horse.class);
                                break;
                            case PIG:
                                Pig pig = vehicleLoc.getWorld().spawn(vehicleLoc, Pig.class);
                                pig.setSaddle(true);
                                vehicle = pig;
                                break;
                            case STRIDER:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Strider.class);
                                break;
                            case LLAMA:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Llama.class);
                                break;
                            case CAMEL:
                                vehicle = vehicleLoc.getWorld().spawn(vehicleLoc, Camel.class);
                                break;
                        }

                        if (vehicle != null) {
                            int vehicleEntityId = vehicle.getEntityId();
                            mountedEntities.put(vehicleEntityId, vehicle);
                            replayPlayer.getNpcManager().sendMountPacket(vehicleEntityId);
                            replayPlayer.getNpcManager().teleportNPC(vehicleLoc, mountedEntities);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[REPLAY-DEBUG] Error spawning vehicle: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } else {
            // Araçtan inme
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Entity vehicle : mountedEntities.values()) {
                    if (vehicle != null && vehicle.isValid()) {
                        vehicle.remove();
                    }
                }
                mountedEntities.clear();
            });
        }
    }

    /**
     * Bed action'ını oynatır
     */
    private void playBed(BedAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing bed: " + action.getActionType() +
                " at " + action.getBedX() + "," + action.getBedY() + "," + action.getBedZ());

        if (action.getActionType() == BedAction.ActionType.ENTER_BED) {
            playPose(new PoseAction(PoseAction.PoseType.SLEEPING));

            Location bedLoc = new Location(replayPlayer.getLastLocation().getWorld(),
                    action.getBedX() + 0.5, action.getBedY() + 0.5625, action.getBedZ() + 0.5);

            replayPlayer.getNpcManager().teleportNPC(bedLoc, mountedEntities);
            replayPlayer.setLastLocation(bedLoc);
        } else {
            playPose(new PoseAction(PoseAction.PoseType.STANDING));
        }
    }

    /**
     * Chat action'ını oynatır
     */
    private void playChat(ChatAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing chat: " + action.getMessage());

        String prefix = "§7[§6Replay§7] §e" + replayPlayer.getReplay().getRecordedPlayer() + "§7: ";
        String message = action.isCommand() ? "§8" + action.getMessage() : "§f" + action.getMessage();

        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendMessage(prefix + message);

            if (!action.isCommand()) {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.0f);
            }
        }
    }

    /**
     * Fishing action'ını oynatır - 1.21.4 için güncellenmiş
     */
    private void playFishing(FishingAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing fishing: " + action.getState());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location playerLoc = replayPlayer.getLastLocation();
            if (playerLoc == null) return;

            Location hookLoc = new Location(
                    playerLoc.getWorld(),
                    action.getHookX(), action.getHookY(), action.getHookZ()
            );

            org.bukkit.World world = playerLoc.getWorld();

            switch (action.getState()) {
                case CAST:
                    // Olta atma sesi
                    world.playSound(playerLoc, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);

                    plugin.getLogger().info("[REPLAY-DEBUG] FishHook casting - using particle animation");

                    // FishHook spawn edilemiyor, particle ile simüle et
                    Location spawnLoc = playerLoc.clone().add(0, 1.5, 0);

                    // Olta ipinin uçuş animasyonu - particle trail
                    new org.bukkit.scheduler.BukkitRunnable() {
                        double progress = 0.0;

                        @Override
                        public void run() {
                            if (progress >= 1.0) {
                                // Animasyon bitti - hook konumunda splash efekti
                                world.playSound(hookLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.0f);
                                world.spawnParticle(Particle.SPLASH, hookLoc, 10, 0.3, 0.1, 0.3, 0.1);
                                world.spawnParticle(Particle.BUBBLE_POP, hookLoc, 5, 0.2, 0.1, 0.2, 0.05);
                                this.cancel();
                                return;
                            }

                            // Olta ipinin o anki konumu (lerp)
                            double x = spawnLoc.getX() + (hookLoc.getX() - spawnLoc.getX()) * progress;
                            double y = spawnLoc.getY() + (hookLoc.getY() - spawnLoc.getY()) * progress;
                            double z = spawnLoc.getZ() + (hookLoc.getZ() - spawnLoc.getZ()) * progress;

                            // Parabolik yay efekti (olta havada yay çizer)
                            double arc = Math.sin(progress * Math.PI) * 2.0;
                            y += arc;

                            Location currentLoc = new Location(world, x, y, z);

                            // Particle trail
                            world.spawnParticle(Particle.DRIPPING_WATER, currentLoc, 2, 0.05, 0.05, 0.05, 0);
                            world.spawnParticle(Particle.BUBBLE_POP, currentLoc, 1, 0, 0, 0, 0);

                            progress += 0.05; // Her tick %5 ilerle
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    break;

                case CAUGHT:
                    world.playSound(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                    // SPLASH particle
                    world.spawnParticle(Particle.SPLASH, hookLoc, 20, 0.3, 0.3, 0.3, 0.1);

                    // Viewer'lara title göster
                    for (Player viewer : replayPlayer.getViewers()) {
                        viewer.sendTitle("", "§a✓ Balık yakalandı!", 10, 20, 10);
                    }
                    break;

                case REEL_IN:
                    world.playSound(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.5f, 1.5f);
                    // Çekme efekti
                    world.spawnParticle(Particle.BUBBLE_POP,
                            hookLoc, 10, 0.2, 0.2, 0.2, 0.05);
                    break;

                case FAILED:
                    world.playSound(hookLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
                    break;
            }
        });
    }

    /**
     * InteractEntity action'ını oynatır
     */
    private void playInteractEntity(InteractEntityAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing entity interaction: " + action.getType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getTargetX(), action.getTargetY(), action.getTargetZ()
            );

            // Olta ile entity yakalama kontrolü
            if (action.getTargetEntityType().endsWith("_FISHING_CAUGHT")) {
                String entityTypeName = action.getTargetEntityType().replace("_FISHING_CAUGHT", "");

                // Yakındaki entity'yi bul
                for (Entity nearbyEntity : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                    // GERÇEK OYUNCULARI ASLA ETKİLEME! Sadece hayvanlar ve mob'lar
                    if (nearbyEntity instanceof Player) {
                        continue; // Gerçek oyuncuları atla
                    }

                    if (nearbyEntity.getType().name().equals(entityTypeName)) {
                        // Entity'yi NPC'ye doğru çek
                        Vector pullVector = replayPlayer.getLastLocation().toVector()
                                .subtract(nearbyEntity.getLocation().toVector())
                                .normalize()
                                .multiply(0.5);

                        nearbyEntity.setVelocity(pullVector);

                        // Efektler - 1.21.4 için güncellenmiş
                        loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, nearbyEntity.getLocation(), 10, 0.3, 0.3, 0.3, 0.1);

                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.playSound(loc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                        }
                        break;
                    }
                }
                return;
            }

            // NOT: Entity spawn artık EntitySpawnAction ile handle ediliyor (entity özellikleriyle birlikte)

            // Leash kontrolü
            if (action.getTargetEntityType().endsWith("_LEASHED")) {
                String entityTypeName = action.getTargetEntityType().replace("_LEASHED", "");

                // Yakındaki entity'yi bul ve görsel efekt göster
                for (Entity nearbyEntity : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                    // Gerçek oyuncuları asla etkileme
                    if (nearbyEntity instanceof Player) {
                        continue;
                    }

                    if (nearbyEntity.getType().name().equals(entityTypeName) && nearbyEntity instanceof LivingEntity) {
                        // Leash efekti
                        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, nearbyEntity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);

                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.playSound(loc, Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.0f);
                        }
                        break;
                    }
                }
                return;
            }

            // Unleash kontrolü
            if (action.getTargetEntityType().endsWith("_UNLEASHED")) {
                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.playSound(loc, Sound.ENTITY_LEASH_KNOT_BREAK, 1.0f, 1.0f);
                }
                return;
            }

            // Normal entity interaction kodları
            for (Player viewer : replayPlayer.getViewers()) {
                // İnteraksiyon tipine göre efekt
                switch (action.getType()) {
                    case SHEAR:
                        // Kırpma animasyonu
                        replayPlayer.getNpcManager().sendAnimation(
                                WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
                        );

                        // Kırpma efektleri
                        viewer.playSound(loc, Sound.ENTITY_SHEEP_SHEAR, 1.0f, 1.0f);

                        // Yün rengi bilgisini parse et (format: "SHEEP:WHITE" veya sadece "SHEEP")
                        String entityType = action.getTargetEntityType();
                        org.bukkit.DyeColor woolColor = org.bukkit.DyeColor.WHITE; // Default

                        if (entityType != null && entityType.contains(":")) {
                            String[] parts = entityType.split(":");
                            if (parts.length >= 2) {
                                try {
                                    woolColor = org.bukkit.DyeColor.valueOf(parts[1]);
                                } catch (Exception e) {
                                    // Default white kullan
                                }
                            }
                        }

                        // Doğru renk yün parçacıkları
                        Material woolMaterial = Material.valueOf(woolColor.name() + "_WOOL");
                        loc.getWorld().spawnParticle(Particle.ITEM,
                                loc.clone().add(0, 0.5, 0),
                                20,
                                0.3, 0.3, 0.3,
                                0.05,
                                new ItemStack(woolMaterial)
                        );

                        plugin.getLogger().info("[REPLAY-DEBUG] Shearing effect with wool color: " + woolColor.name());

                        // Replay sırasında spawn edilen koyunu kırpılmış olarak göster
                        // Gerçek dünya entity'lerini değil, sadece spawnedEntities listesindeki entity'leri etkile
                        for (Entity entity : spawnedEntities) {
                            if (entity instanceof org.bukkit.entity.Sheep && entity.isValid()) {
                                // Lokasyon yakınlığı kontrolü (2 blok yarıçap)
                                if (entity.getLocation().distance(loc) < 2.0) {
                                    org.bukkit.entity.Sheep sheep = (org.bukkit.entity.Sheep) entity;
                                    if (!sheep.isSheared()) {
                                        sheep.setSheared(true);
                                        plugin.getLogger().info("[REPLAY-DEBUG] Spawned sheep sheared at " +
                                            sheep.getLocation() + ", color: " + sheep.getColor().name());
                                        break; // Sadece en yakın koyunu kırp
                                    }
                                }
                            }
                        }
                        break;

                    case FEED:
                        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
                        break;

                    case TAME:
                        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
                        viewer.playSound(loc, Sound.ENTITY_WOLF_WHINE, 1.0f, 1.0f);
                        break;

                    case BREED:
                        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
                        viewer.playSound(loc, Sound.ENTITY_DONKEY_ANGRY, 1.0f, 1.0f);
                        break;

                    case MILK:
                        viewer.playSound(loc, Sound.ENTITY_COW_MILK, 1.0f, 1.0f);
                        break;

                    case TRADE:
                        viewer.playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                        break;

                    case LEFT_CLICK:
                        loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
                        break;
                }
            }
        });
    }

    /**
     * Teleport action'ını oynatır
     */
    private void playTeleport(TeleportAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing teleport: " + action.getCause());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // From world - backward compatibility için null kontrolü
            org.bukkit.World fromWorld;
            if (action.getFromWorld() != null) {
                fromWorld = plugin.getServer().getWorld(action.getFromWorld());
                if (fromWorld == null) {
                    plugin.getLogger().warning("[REPLAY] From world not found: " + action.getFromWorld() + ", using current world");
                    fromWorld = replayPlayer.getLastLocation().getWorld();
                }
            } else {
                // Eski kayıtlar için - world bilgisi yok
                fromWorld = replayPlayer.getLastLocation().getWorld();
            }

            // To world - backward compatibility için null kontrolü
            org.bukkit.World toWorld;
            if (action.getToWorld() != null) {
                toWorld = plugin.getServer().getWorld(action.getToWorld());
                if (toWorld == null) {
                    plugin.getLogger().warning("[REPLAY] To world not found: " + action.getToWorld() + ", using current world");
                    toWorld = replayPlayer.getLastLocation().getWorld();
                }
            } else {
                // Eski kayıtlar için - world bilgisi yok
                toWorld = replayPlayer.getLastLocation().getWorld();
            }

            Location fromLoc = new Location(
                    fromWorld,
                    action.getFromX(), action.getFromY(), action.getFromZ()
            );

            Location toLoc = new Location(
                    toWorld,
                    action.getToX(), action.getToY(), action.getToZ()
            );

            // Teleport efektleri
            for (Player viewer : replayPlayer.getViewers()) {
                // Başlangıç noktası efektleri
                fromLoc.getWorld().spawnParticle(Particle.PORTAL, fromLoc.clone().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
                viewer.playSound(fromLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                // Bitiş noktası efektleri
                toLoc.getWorld().spawnParticle(Particle.PORTAL, toLoc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);

                // NPC'yi teleport et
                replayPlayer.getNpcManager().teleportNPC(toLoc, replayPlayer.getActionPlayer().getMountedEntities());
                replayPlayer.setLastLocation(toLoc);

                // Eğer farklı bir dünyaya TP yapılıyorsa izleyiciyi de TP ettir
                if (!viewer.getWorld().equals(toWorld)) {
                    viewer.teleport(toLoc);
                    viewer.sendMessage("§e✦ Replay: " + toWorld.getName() + " dünyasına geçiliyor...");
                    plugin.getLogger().info("[REPLAY] Teleporting viewer " + viewer.getName() +
                            " to world: " + toWorld.getName());
                }

                // Bilgi göster
                switch (action.getCause()) {
                    case ENDER_PEARL:
                        viewer.sendActionBar("§d✦ Ender Pearl Teleport");
                        break;
                    case CHORUS_FRUIT:
                        viewer.sendActionBar("§5✦ Chorus Fruit Teleport");
                        break;
                    case COMMAND:
                        viewer.sendActionBar("§6✦ Command Teleport (" + toWorld.getName() + ")");
                        break;
                    default:
                        viewer.sendActionBar("§7✦ Teleport (" + toWorld.getName() + ")");
                }
            }
        });
    }

    /**
     * Health action'ını oynatır
     */
    private void playHealth(HealthAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing health: " + action.getHealth() + "/" + action.getMaxHealth());

        // Can durumunu action bar'da göster
        String healthBar = createHealthBar(action.getHealth(), action.getMaxHealth());
        String foodBar = createFoodBar(action.getFoodLevel());

        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendActionBar(healthBar + " §7| " + foodBar);

            // Düşük can uyarısı
            if (action.getHealth() <= 6) {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            }
        }
    }

    /**
     * Potion effect action'ını oynatır
     */
    private void playPotionEffect(PotionEffectAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing potion effect: " + action.getActionType() + " - " + action.getEffectType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getLastLocation();

            for (Player viewer : replayPlayer.getViewers()) {
                switch (action.getActionType()) {
                    case ADD:
                        // ENTITY_EFFECT particle'ı Color parametresi gerektiriyor
                        loc.getWorld().spawnParticle(Particle.ENTITY_EFFECT,
                                loc.clone().add(0, 1, 0),
                                20,
                                0.5, 0.5, 0.5,
                                0,
                                org.bukkit.Color.fromRGB(255, 0, 255)); // Mor renk
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
                        viewer.sendActionBar("§a+ " + action.getEffectType() + " " + (action.getAmplifier() + 1));
                        break;

                    case REMOVE:
                        viewer.sendActionBar("§c- " + action.getEffectType());
                        break;

                    case CLEAR_ALL:
                        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);
                        viewer.sendActionBar("§c✖ Tüm efektler temizlendi");
                        break;
                }
            }
        });
    }

    /**
     * GameMode action'ını oynatır
     */
    private void playGameMode(GameModeAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing gamemode: " + action.getGameMode());

        String modeName = "";
        switch (action.getGameMode()) {
            case SURVIVAL:
                modeName = "§aSurvival";
                break;
            case CREATIVE:
                modeName = "§dCreative";
                break;
            case ADVENTURE:
                modeName = "§eAdventure";
                break;
            case SPECTATOR:
                modeName = "§7Spectator";
                break;
        }

        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendTitle("", modeName + " §7modu", 10, 30, 10);
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
    }

    /**
     * Weather action'ını oynatır
     */
    private void playWeather(WeatherAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing weather: " + action.getWeatherType());

        for (Player viewer : replayPlayer.getViewers()) {
            switch (action.getWeatherType()) {
                case CLEAR:
                    viewer.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
                    viewer.sendActionBar("§e☀ Güneşli");
                    break;

                case RAIN:
                    viewer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
                    viewer.sendActionBar("§b🌧 Yağmurlu");
                    break;

                case THUNDER:
                    viewer.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
                    viewer.sendActionBar("§5⚡ Fırtınalı");
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f);
                    break;
            }
        }
    }

    /**
     * Sound action'ını oynatır
     */
    private void playSound(SoundAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing sound: " + action.getSoundName());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location soundLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            try {
                Sound sound = Sound.valueOf(action.getSoundName());

                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.playSound(soundLoc, sound, action.getVolume(), action.getPitch());
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Unknown sound: " + action.getSoundName());
            }
        });
    }

    // Helper metodlar
    private String createHealthBar(double health, double maxHealth) {
        StringBuilder bar = new StringBuilder("§c❤ ");
        int hearts = (int) Math.ceil(health / 2);
        int maxHearts = (int) Math.ceil(maxHealth / 2);

        for (int i = 0; i < maxHearts; i++) {
            if (i < hearts) {
                bar.append("§c♥");
            } else {
                bar.append("§7♥");
            }
        }

        return bar.toString() + " §f" + String.format("%.1f", health);
    }

    private String createFoodBar(int foodLevel) {
        StringBuilder bar = new StringBuilder("§6🍖 ");
        int maxFood = 10;

        for (int i = 0; i < maxFood; i++) {
            if (i < foodLevel / 2) {
                bar.append("§6◆");
            } else {
                bar.append("§7◆");
            }
        }

        return bar.toString() + " §f" + foodLevel;
    }

    /**
     * Değiştirilen blokları eski haline getirir
     */
    public void restoreBlocks() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!originalBlocks.isEmpty()) {
                for (Player viewer : replayPlayer.getViewers()) {
                    for (Map.Entry<String, Material> entry : originalBlocks.entrySet()) {
                        String[] coords = entry.getKey().split(",");
                        Location loc = new Location(viewer.getWorld(),
                                Integer.parseInt(coords[0]),
                                Integer.parseInt(coords[1]),
                                Integer.parseInt(coords[2])
                        );

                        org.bukkit.block.Block realBlock = loc.getBlock();
                        viewer.sendBlockChange(loc, realBlock.getBlockData());
                    }
                }

                plugin.getLogger().info("[REPLAY-DEBUG] " + originalBlocks.size() + " blok restore edildi.");
                originalBlocks.clear();
            }
        });
    }

    /**
     * Explosion action'ını oynatır
     */
    private void playExplosion(ExplosionAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing explosion: " + action.getExplosionType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location explosionLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            org.bukkit.World world = explosionLoc.getWorld();

            // Explosion efekti - SADECE GÖRSELhiçbir entity'ye hasar verme!
            // createExplosion yerine particle ve ses kullanıyoruz
            world.spawnParticle(Particle.EXPLOSION_EMITTER, explosionLoc, 1);
            world.spawnParticle(Particle.EXPLOSION, explosionLoc, 10,
                    action.getPower() / 2.0, action.getPower() / 2.0, action.getPower() / 2.0, 0.1);

            // Explosion tipi için özel ses ve particle efektleri
            switch (action.getExplosionType()) {
                case CREEPER:
                    world.playSound(explosionLoc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                    world.spawnParticle(Particle.EXPLOSION, explosionLoc, 5, 1, 1, 1, 0.1);
                    break;

                case TNT:
                    world.playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, explosionLoc, 1);
                    break;

                case BED:
                    world.playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);
                    world.spawnParticle(Particle.LAVA, explosionLoc, 20, 2, 2, 2, 0.1);
                    break;

                case END_CRYSTAL:
                    world.playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.2f);
                    world.spawnParticle(Particle.DRAGON_BREATH, explosionLoc, 50, 2, 2, 2, 0.1);
                    break;

                case FIREBALL:
                    world.playSound(explosionLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
                    world.spawnParticle(Particle.FLAME, explosionLoc, 30, 1.5, 1.5, 1.5, 0.1);
                    break;

                case WITHER:
                    world.playSound(explosionLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 1.0f);
                    world.spawnParticle(Particle.SMOKE, explosionLoc, 40, 2, 2, 2, 0.1);
                    break;

                default:
                    world.playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    world.spawnParticle(Particle.EXPLOSION, explosionLoc, 3, 1, 1, 1, 0.05);
                    break;
            }

            // Viewer'lara kamera shake efekti (titreşim)
            for (Player viewer : replayPlayer.getViewers()) {
                double distance = viewer.getLocation().distance(explosionLoc);
                if (distance < 20) {
                    // Yakınlıkta ise screen shake (velocity ile simüle edilir)
                    viewer.sendTitle("", "", 5, 10, 5);
                }
            }
        });
    }

    /**
     * EntityUpdate action'ını oynatır (spawned entity'lerin hareketini gösterir)
     */
    private void playEntityUpdate(EntityUpdateAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity entity = spawnedEntitiesMap.get(action.getEntityUuid());

            if (entity != null && entity.isValid()) {
                Location newLoc = new Location(
                        replayPlayer.getLastLocation().getWorld(),
                        action.getX(),
                        action.getY(),
                        action.getZ(),
                        action.getYaw(),
                        action.getPitch()
                );

                // Entity'yi yeni pozisyona teleport et
                entity.teleport(newLoc);

                plugin.getLogger().info("[REPLAY-DEBUG] Entity updated: " + action.getEntityType() +
                        " to " + newLoc.toVector());
            } else {
                plugin.getLogger().warning("[REPLAY-DEBUG] Entity not found for update: UUID " +
                        action.getEntityUuid());
            }
        });
    }

    /**
     * EntitySpawn action'ını oynatır - Entity'yi özelliklerine sahip olarak spawn eder
     */
    private void playEntitySpawn(EntitySpawnAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ(),
                    action.getYaw(),
                    action.getPitch()
            );

            try {
                EntityType entityType = EntityType.valueOf(action.getEntityType());
                Entity spawnedEntity = loc.getWorld().spawnEntity(loc, entityType);

                // Özellikleri uygula
                applyEntityProperties(spawnedEntity, action.getProperties());

                // Entity'yi track et - replay bitince silinecek
                spawnedEntities.add(spawnedEntity);
                spawnedEntitiesMap.put(action.getEntityUuid(), spawnedEntity);

                // Spawn efektleri
                loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.3, 0.3, 0.05);

                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.playSound(loc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
                }

                plugin.getLogger().info("[REPLAY-DEBUG] Spawned entity with properties: " +
                        action.getEntityType() + " - " + action.getProperties());

            } catch (Exception e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Failed to spawn entity: " +
                        action.getEntityType() + " - " + e.getMessage());
            }
        });
    }

    /**
     * Entity'ye özellikleri uygular (renk, baby, isim, vb.)
     */
    private void applyEntityProperties(Entity entity, Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return;

        // Baby/Adult
        if (entity instanceof Ageable && properties.containsKey("baby")) {
            Ageable ageable = (Ageable) entity;
            boolean isBaby = Boolean.parseBoolean(properties.get("baby"));
            if (isBaby) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            if (properties.containsKey("age")) {
                ageable.setAge(Integer.parseInt(properties.get("age")));
            }
        }

        // Custom name
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (properties.containsKey("customName")) {
                living.setCustomName(properties.get("customName"));
                if (properties.containsKey("customNameVisible")) {
                    living.setCustomNameVisible(Boolean.parseBoolean(properties.get("customNameVisible")));
                }
            }
            if (properties.containsKey("ai")) {
                living.setAI(Boolean.parseBoolean(properties.get("ai")));
            }
        }

        // Koyun
        if (entity instanceof Sheep) {
            Sheep sheep = (Sheep) entity;
            if (properties.containsKey("color")) {
                sheep.setColor(org.bukkit.DyeColor.valueOf(properties.get("color")));
            }
            if (properties.containsKey("sheared")) {
                sheep.setSheared(Boolean.parseBoolean(properties.get("sheared")));
            }
        }

        // Kedi
        else if (entity instanceof Cat) {
            Cat cat = (Cat) entity;
            if (properties.containsKey("catType")) {
                cat.setCatType(Cat.Type.valueOf(properties.get("catType")));
            }
            if (properties.containsKey("tamed") && Boolean.parseBoolean(properties.get("tamed"))) {
                cat.setTamed(true);
                if (properties.containsKey("collarColor")) {
                    cat.setCollarColor(org.bukkit.DyeColor.valueOf(properties.get("collarColor")));
                }
            }
        }

        // Kurt
        else if (entity instanceof Wolf) {
            Wolf wolf = (Wolf) entity;
            if (properties.containsKey("tamed") && Boolean.parseBoolean(properties.get("tamed"))) {
                wolf.setTamed(true);
                if (properties.containsKey("collarColor")) {
                    wolf.setCollarColor(org.bukkit.DyeColor.valueOf(properties.get("collarColor")));
                }
            }
            if (properties.containsKey("angry")) {
                wolf.setAngry(Boolean.parseBoolean(properties.get("angry")));
            }
        }

        // At
        else if (entity instanceof Horse) {
            Horse horse = (Horse) entity;
            if (properties.containsKey("color")) {
                horse.setColor(Horse.Color.valueOf(properties.get("color")));
            }
            if (properties.containsKey("style")) {
                horse.setStyle(Horse.Style.valueOf(properties.get("style")));
            }
            if (properties.containsKey("tamed")) {
                horse.setTamed(Boolean.parseBoolean(properties.get("tamed")));
            }
            if (properties.containsKey("saddle")) {
                horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            }
            if (properties.containsKey("armor")) {
                Material armorType = Material.valueOf(properties.get("armor"));
                horse.getInventory().setArmor(new ItemStack(armorType));
            }
        }

        // Llama
        else if (entity instanceof Llama) {
            Llama llama = (Llama) entity;
            if (properties.containsKey("color")) {
                llama.setColor(Llama.Color.valueOf(properties.get("color")));
            }
            if (properties.containsKey("strength")) {
                llama.setStrength(Integer.parseInt(properties.get("strength")));
            }
            if (properties.containsKey("tamed")) {
                llama.setTamed(Boolean.parseBoolean(properties.get("tamed")));
            }
        }

        // Papağan
        else if (entity instanceof Parrot) {
            Parrot parrot = (Parrot) entity;
            if (properties.containsKey("variant")) {
                parrot.setVariant(Parrot.Variant.valueOf(properties.get("variant")));
            }
            if (properties.containsKey("tamed")) {
                parrot.setTamed(Boolean.parseBoolean(properties.get("tamed")));
            }
        }

        // Axolotl
        else if (entity instanceof Axolotl) {
            Axolotl axolotl = (Axolotl) entity;
            if (properties.containsKey("variant")) {
                axolotl.setVariant(Axolotl.Variant.valueOf(properties.get("variant")));
            }
        }

        // Fox
        else if (entity instanceof Fox) {
            Fox fox = (Fox) entity;
            if (properties.containsKey("foxType")) {
                fox.setFoxType(Fox.Type.valueOf(properties.get("foxType")));
            }
            if (properties.containsKey("sleeping")) {
                fox.setSleeping(Boolean.parseBoolean(properties.get("sleeping")));
            }
            if (properties.containsKey("crouching")) {
                fox.setCrouching(Boolean.parseBoolean(properties.get("crouching")));
            }
        }

        // Panda
        else if (entity instanceof Panda) {
            Panda panda = (Panda) entity;
            if (properties.containsKey("mainGene")) {
                panda.setMainGene(Panda.Gene.valueOf(properties.get("mainGene")));
            }
            if (properties.containsKey("hiddenGene")) {
                panda.setHiddenGene(Panda.Gene.valueOf(properties.get("hiddenGene")));
            }
        }

        // Villager
        else if (entity instanceof Villager) {
            Villager villager = (Villager) entity;
            if (properties.containsKey("profession")) {
                villager.setProfession(Villager.Profession.valueOf(properties.get("profession")));
            }
            if (properties.containsKey("villagerType")) {
                villager.setVillagerType(Villager.Type.valueOf(properties.get("villagerType")));
            }
            if (properties.containsKey("level")) {
                villager.setVillagerLevel(Integer.parseInt(properties.get("level")));
            }
        }

        // Zombie Villager
        else if (entity instanceof ZombieVillager) {
            ZombieVillager zombieVillager = (ZombieVillager) entity;
            if (properties.containsKey("profession")) {
                zombieVillager.setVillagerProfession(Villager.Profession.valueOf(properties.get("profession")));
            }
        }

        // Tropical Fish
        else if (entity instanceof TropicalFish) {
            TropicalFish fish = (TropicalFish) entity;
            if (properties.containsKey("pattern")) {
                fish.setPattern(TropicalFish.Pattern.valueOf(properties.get("pattern")));
            }
            if (properties.containsKey("bodyColor")) {
                fish.setBodyColor(org.bukkit.DyeColor.valueOf(properties.get("bodyColor")));
            }
            if (properties.containsKey("patternColor")) {
                fish.setPatternColor(org.bukkit.DyeColor.valueOf(properties.get("patternColor")));
            }
        }

        // Slime
        else if (entity instanceof Slime) {
            Slime slime = (Slime) entity;
            if (properties.containsKey("size")) {
                slime.setSize(Integer.parseInt(properties.get("size")));
            }
        }

        // Creeper
        else if (entity instanceof Creeper) {
            Creeper creeper = (Creeper) entity;
            if (properties.containsKey("powered")) {
                creeper.setPowered(Boolean.parseBoolean(properties.get("powered")));
            }
        }

        // Rabbit
        else if (entity instanceof Rabbit) {
            Rabbit rabbit = (Rabbit) entity;
            if (properties.containsKey("rabbitType")) {
                rabbit.setRabbitType(Rabbit.Type.valueOf(properties.get("rabbitType")));
            }
        }

        // Shulker
        else if (entity instanceof Shulker) {
            Shulker shulker = (Shulker) entity;
            if (properties.containsKey("color")) {
                shulker.setColor(org.bukkit.DyeColor.valueOf(properties.get("color")));
            }
        }

        // Frog
        else if (entity instanceof Frog) {
            Frog frog = (Frog) entity;
            if (properties.containsKey("variant")) {
                frog.setVariant(Frog.Variant.valueOf(properties.get("variant")));
            }
        }

        // Goat
        else if (entity instanceof Goat) {
            Goat goat = (Goat) entity;
            if (properties.containsKey("screaming")) {
                goat.setScreaming(Boolean.parseBoolean(properties.get("screaming")));
            }
        }
    }

    /**
     * EntityDye action'ını oynatır - Spawned entity'nin rengini değiştirir
     */
    private void playEntityDye(EntityDyeAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity entity = spawnedEntitiesMap.get(action.getEntityUuid());

            if (entity != null && entity.isValid()) {
                if (entity instanceof Sheep) {
                    Sheep sheep = (Sheep) entity;
                    org.bukkit.DyeColor newColor = org.bukkit.DyeColor.valueOf(action.getDyeColor());
                    sheep.setColor(newColor);

                    // Particle efekti
                    entity.getWorld().spawnParticle(Particle.ENTITY_EFFECT,
                            entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);

                    plugin.getLogger().info("[REPLAY-DEBUG] Sheep dyed to: " + newColor.name());
                }
            } else {
                // Entity bulunamadıysa, yakınlardaki gerçek entity'yi bul (fallback)
                Location loc = new Location(
                        replayPlayer.getLastLocation().getWorld(),
                        action.getX(),
                        action.getY(),
                        action.getZ()
                );

                for (Entity nearbyEntity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                    if (nearbyEntity instanceof Sheep) {
                        Sheep sheep = (Sheep) nearbyEntity;
                        org.bukkit.DyeColor newColor = org.bukkit.DyeColor.valueOf(action.getDyeColor());
                        sheep.setColor(newColor);

                        plugin.getLogger().info("[REPLAY-DEBUG] Sheep dyed (fallback): " + newColor.name());
                        break;
                    }
                }
            }
        });
    }

    /**
     * Item action'ını oynatır (drop/pickup)
     */
    private void playItem(ItemAction action) {
        plugin.getLogger().info("[REPLAY-DEBUG] Playing item action: " + action.getActionType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location playerLoc = replayPlayer.getLastLocation();
            if (playerLoc == null) return;

            Location itemLoc = new Location(
                    playerLoc.getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            org.bukkit.World world = itemLoc.getWorld();

            try {
                ItemStack item = ItemSerializer.deserializeItemStack(action.getItemData());
                if (item == null || item.getType() == Material.AIR) return;

                item.setAmount(action.getAmount());

                switch (action.getActionType()) {
                    case DROP:
                        // Item drop efekti
                        world.playSound(itemLoc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);

                        // Item entity spawn et
                        org.bukkit.entity.Item droppedItem = world.dropItem(playerLoc.clone().add(0, 1.2, 0), item);
                        droppedItem.setVelocity(playerLoc.getDirection().multiply(0.3));
                        droppedItem.setPickupDelay(20); // 1 saniye pickup delay

                        // 5 saniye sonra kaldır
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (droppedItem.isValid()) {
                                droppedItem.remove();
                            }
                        }, 100L);
                        break;

                    case PICKUP:
                        // Item pickup efekti
                        world.playSound(itemLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

                        // Particle efekti
                        world.spawnParticle(Particle.ITEM,
                                itemLoc,
                                10,
                                0.2, 0.2, 0.2,
                                0.05,
                                item
                        );

                        // Viewer'lara title göster
                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.sendActionBar("§e+" + action.getAmount() + " " + item.getType().name());
                        }
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to play item action: " + e.getMessage());
            }
        });
    }

    /**
     * Tüm aktif FishHook'ları temizle
     */
    private void cleanupFishHooks() {
        for (FishHook hook : activeFishHooks) {
            if (hook.isValid()) {
                hook.remove();
            }
        }
        activeFishHooks.clear();
    }

    /**
     * Replay bittiğinde tüm entity'leri ve blokları temizle
     */
    public void cleanup() {
        plugin.getLogger().info("[REPLAY-DEBUG] Cleaning up ReplayActionPlayer resources");

        // FishHook'ları temizle
        cleanupFishHooks();

        // Mount edilmiş entity'leri temizle
        for (Entity entity : mountedEntities.values()) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        mountedEntities.clear();

        // Spawn edilen entity'leri temizle
        int entityCount = spawnedEntities.size();
        plugin.getLogger().info("[REPLAY-DEBUG] Attempting to clean up " + entityCount + " spawned entities");

        for (Entity entity : spawnedEntities) {
            if (entity != null && entity.isValid()) {
                entity.remove();
                plugin.getLogger().info("[REPLAY-DEBUG] Removed spawned entity: " + entity.getType().name() + " at " + entity.getLocation());
            } else {
                plugin.getLogger().warning("[REPLAY-DEBUG] Entity already invalid or null");
            }
        }
        spawnedEntities.clear();
        plugin.getLogger().info("[REPLAY-DEBUG] Cleaned up " + entityCount + " spawned entities");

        // Blokları restore et
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!originalBlocks.isEmpty() && !replayPlayer.getViewers().isEmpty()) {
                // İlk viewer'ı al (world bilgisi için)
                Player firstViewer = replayPlayer.getViewers().iterator().next();

                for (Map.Entry<String, Material> entry : originalBlocks.entrySet()) {
                    String[] coords = entry.getKey().split(",");
                    if (coords.length == 3) {
                        Location loc = new Location(firstViewer.getWorld(),
                                Integer.parseInt(coords[0]),
                                Integer.parseInt(coords[1]),
                                Integer.parseInt(coords[2])
                        );

                        org.bukkit.block.Block realBlock = loc.getBlock();
                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.sendBlockChange(loc, realBlock.getBlockData());
                        }
                    }
                }

                plugin.getLogger().info("[REPLAY-DEBUG] " + originalBlocks.size() + " blok restore edildi.");
                originalBlocks.clear();
            }
        });
    }

    /**
     * Hanging action'ını oynatır (Painting/Item Frame)
     */
    private void playHanging(HangingAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            if (action.getActionType() == HangingAction.ActionType.PLACE) {
                try {
                    org.bukkit.block.BlockFace facing = org.bukkit.block.BlockFace.valueOf(action.getFacing());

                    Hanging hanging = null;
                    switch (action.getHangingType()) {
                        case PAINTING:
                            hanging = loc.getWorld().spawn(loc, Painting.class, painting -> {
                                painting.setFacingDirection(facing);
                                // Painting art tipini set et
                                if (action.getPaintingArt() != null) {
                                    try {
                                        org.bukkit.Art art = org.bukkit.Art.valueOf(action.getPaintingArt());
                                        painting.setArt(art);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[REPLAY-DEBUG] Failed to set painting art: " + action.getPaintingArt() + " - " + e.getMessage());
                                    }
                                }
                            });
                            break;

                        case ITEM_FRAME:
                            hanging = loc.getWorld().spawn(loc, ItemFrame.class, frame -> {
                                frame.setFacingDirection(facing);
                                // Item Frame içindeki item
                                if (action.getItemData() != null) {
                                    try {
                                        ItemStack item = ItemSerializer.deserializeItemStack(action.getItemData());
                                        frame.setItem(item);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[REPLAY-DEBUG] Failed to set item frame item: " + e.getMessage());
                                    }
                                }
                            });
                            break;

                        case GLOW_ITEM_FRAME:
                            hanging = loc.getWorld().spawn(loc, GlowItemFrame.class, frame -> {
                                frame.setFacingDirection(facing);
                                if (action.getItemData() != null) {
                                    try {
                                        ItemStack item = ItemSerializer.deserializeItemStack(action.getItemData());
                                        frame.setItem(item);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("[REPLAY-DEBUG] Failed to set glow item frame item: " + e.getMessage());
                                    }
                                }
                            });
                            break;
                    }

                    if (hanging != null) {
                        spawnedEntities.add(hanging);
                        plugin.getLogger().info("[REPLAY-DEBUG] Hanging placed: " + action.getHangingType());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[REPLAY-DEBUG] Failed to place hanging: " + e.getMessage());
                }
            } else {
                // BREAK - Yakındaki hanging'i bul ve kaldır
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                    if (entity instanceof Hanging) {
                        entity.remove();
                        plugin.getLogger().info("[REPLAY-DEBUG] Hanging removed");
                        break;
                    }
                }
            }
        });
    }

    /**
     * FallingBlock action'ını oynatır
     */
    private void playFallingBlock(FallingBlockAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            try {
                Material material = Material.valueOf(action.getBlockType());
                FallingBlock fallingBlock = loc.getWorld().spawnFallingBlock(loc, material.createBlockData());

                spawnedEntities.add(fallingBlock);

                plugin.getLogger().info("[REPLAY-DEBUG] Falling block spawned: " + material);
            } catch (Exception e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Failed to spawn falling block: " + e.getMessage());
            }
        });
    }

    /**
     * Breed action'ını oynatır (baby entity spawn)
     */
    private void playBreed(BreedAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            try {
                EntityType entityType = EntityType.valueOf(action.getBabyEntityType());
                Entity baby = loc.getWorld().spawnEntity(loc, entityType);

                // Baby olarak ayarla
                if (baby instanceof Ageable) {
                    ((Ageable) baby).setBaby();
                }

                spawnedEntities.add(baby);

                // Kalp parçacıkları
                loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);

                plugin.getLogger().info("[REPLAY-DEBUG] Baby entity spawned: " + entityType);
            } catch (Exception e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Failed to spawn baby entity: " + e.getMessage());
            }
        });
    }

    /**
     * Bucket action'ını oynatır
     */
    private void playBucket(BucketAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location blockLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getBlockX(),
                    action.getBlockY(),
                    action.getBlockZ()
            );

            org.bukkit.block.Block block = blockLoc.getBlock();

            if (action.getActionType() == BucketAction.ActionType.EMPTY) {
                // Kova boşaltma - su/lav yerleştir
                Material material = null;
                switch (action.getBucketType()) {
                    case WATER:
                        material = Material.WATER;
                        break;
                    case LAVA:
                        material = Material.LAVA;
                        break;
                    case POWDER_SNOW:
                        material = Material.POWDER_SNOW;
                        break;
                    case MILK:
                    case FISH:
                        // Süt ve balık kovası için görsel efekt yok
                        return;
                }

                if (material != null) {
                    // Viewer'lara blok değişikliğini gönder
                    for (Player viewer : replayPlayer.getViewers()) {
                        viewer.sendBlockChange(blockLoc, material.createBlockData());
                    }

                    // Orijinal bloğu kaydet
                    String blockKey = action.getBlockX() + "," + action.getBlockY() + "," + action.getBlockZ();
                    if (!originalBlocks.containsKey(blockKey)) {
                        originalBlocks.put(blockKey, block.getType());
                    }

                    plugin.getLogger().info("[REPLAY-DEBUG] Bucket emptied: " + action.getBucketType());
                }
            } else {
                // FILL - Bloğu kaldır
                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                }

                plugin.getLogger().info("[REPLAY-DEBUG] Bucket filled: " + action.getBucketType());
            }
        });
    }

    /**
     * NoteBlock action'ını oynatır
     */
    private void playNoteBlock(NoteBlockAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location blockLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getBlockX(),
                    action.getBlockY(),
                    action.getBlockZ()
            );

            try {
                org.bukkit.Instrument instrument = org.bukkit.Instrument.valueOf(action.getInstrument());
                org.bukkit.Note note = new org.bukkit.Note(action.getNote());

                // Tüm viewer'lara ses çal
                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.playNote(blockLoc, instrument, note);
                }

                plugin.getLogger().info("[REPLAY-DEBUG] Note played: " + instrument + " note " + action.getNote());
            } catch (Exception e) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Failed to play note: " + e.getMessage());
            }
        });
    }

    /**
     * Sign action'ını oynatır
     */
    private void playSign(com.reportsystem.common.replay.actions.SignAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location signLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            Material signMaterial = Material.getMaterial(action.getSignType());
            if (signMaterial != null && signMaterial.name().contains("SIGN")) {
                // Bloğu sign olarak göster
                org.bukkit.block.data.BlockData signData = signMaterial.createBlockData();

                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.sendBlockChange(signLoc, signData);

                    // Sign text'i chat'te göster
                    viewer.sendMessage("§7[Sign] " + String.join(" | ", action.getLines()));
                }

                plugin.getLogger().info("[REPLAY-DEBUG] Sign placed: " + String.join(" | ", action.getLines()));
            }
        });
    }

    /**
     * Anvil action'ını oynatır (chat'te göster)
     */
    private void playAnvil(com.reportsystem.common.replay.actions.AnvilAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Anvil] " + action.getActionType().name() + " (cost: " + action.getExpCost() + ")");
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Anvil action: " + action.getActionType());
        });
    }

    /**
     * Brew action'ını oynatır (chat'te göster)
     */
    private void playBrew(com.reportsystem.common.replay.actions.BrewAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Brewing] Brewed potion with " + action.getIngredient());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Brewing: " + action.getIngredient());
        });
    }

    /**
     * Craft action'ını oynatır (chat'te göster)
     */
    private void playCraft(com.reportsystem.common.replay.actions.CraftAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (action.getCraftedItem() != null) {
                try {
                    org.bukkit.inventory.ItemStack item = com.reportsystem.spigot.utils.ItemSerializer.deserializeItemStack(action.getCraftedItem());
                    for (Player viewer : replayPlayer.getViewers()) {
                        viewer.sendMessage("§7[Crafting] Crafted " + item.getType().name() + " x" + action.getAmount());
                    }
                    plugin.getLogger().info("[REPLAY-DEBUG] Crafted: " + item.getType().name() + " x" + action.getAmount());
                } catch (Exception e) {
                    plugin.getLogger().warning("[REPLAY-DEBUG] Failed to deserialize crafted item: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Enchant action'ını oynatır (chat'te göster)
     */
    private void playEnchant(com.reportsystem.common.replay.actions.EnchantAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (action.getItemAfter() != null) {
                try {
                    org.bukkit.inventory.ItemStack item = com.reportsystem.spigot.utils.ItemSerializer.deserializeItemStack(action.getItemAfter());
                    for (Player viewer : replayPlayer.getViewers()) {
                        viewer.sendMessage("§7[Enchanting] Enchanted " + item.getType().name() + " (cost: " + action.getExpLevelCost() + " levels)");
                    }
                    plugin.getLogger().info("[REPLAY-DEBUG] Enchanted: " + item.getType().name());
                } catch (Exception e) {
                    plugin.getLogger().warning("[REPLAY-DEBUG] Failed to deserialize enchanted item: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Utility block action'ını oynatır
     */
    private void playUtilityBlock(com.reportsystem.common.replay.actions.UtilityBlockAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[" + action.getUtilityType().name() + "] Block used");
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Utility block: " + action.getUtilityType().name());
        });
    }

    /**
     * Book edit action'ını oynatır
     */
    private void playBookEdit(com.reportsystem.common.replay.actions.BookEditAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Book] " + (action.isSigned() ? "Signed: " + action.getTitle() : "Edited") +
                        " (" + action.getPages().size() + " pages)");
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Book edited: " + action.getTitle());
        });
    }

    /**
     * Portal action'ını oynatır
     */
    private void playPortal(com.reportsystem.common.replay.actions.PortalAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Portal] " + action.getPortalType().name() +
                        " from " + action.getFromWorld() + " to " + action.getToWorld());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Portal: " + action.getPortalType().name());
        });
    }

    /**
     * Farming action'ını oynatır
     */
    private void playFarming(com.reportsystem.common.replay.actions.FarmingAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location farmLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            // Particle efektleri
            switch (action.getFarmingType()) {
                case BONE_MEAL:
                    replayPlayer.getLastLocation().getWorld().spawnParticle(
                            org.bukkit.Particle.HAPPY_VILLAGER, farmLoc, 10);
                    break;
                case FARMLAND_TRAMPLE:
                    replayPlayer.getLastLocation().getWorld().playSound(
                            farmLoc, org.bukkit.Sound.BLOCK_GRASS_BREAK, 1.0f, 1.0f);
                    break;
            }

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Farming] " + action.getFarmingType().name());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Farming: " + action.getFarmingType().name());
        });
    }

    /**
     * Player state action'ını oynatır
     */
    private void playPlayerState(com.reportsystem.common.replay.actions.PlayerStateAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Pose değişikliklerini PacketEvents ile gönder
            String stateMsg = action.getStateType().name() + ": " + (action.isEnabled() ? "ON" : "OFF");
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[State] " + stateMsg);
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Player state: " + stateMsg);
        });
    }

    /**
     * Decoration action'ını oynatır
     */
    private void playDecoration(com.reportsystem.common.replay.actions.DecorationAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location decorLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            // Ses efektleri
            switch (action.getDecorationType()) {
                case JUKEBOX_INSERT:
                case JUKEBOX_EJECT:
                    replayPlayer.getLastLocation().getWorld().playSound(
                            decorLoc, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    break;
            }

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Decoration] " + action.getDecorationType().name());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Decoration: " + action.getDecorationType().name());
        });
    }

    /**
     * Redstone action'ını oynatır
     */
    private void playRedstone(com.reportsystem.common.replay.actions.RedstoneAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Redstone] " + action.getRedstoneType().name() +
                        (action.getDelay() > 0 ? " delay=" + action.getDelay() : "") +
                        (action.getMode() != null ? " mode=" + action.getMode() : ""));
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Redstone: " + action.getRedstoneType().name());
        });
    }

    /**
     * Entity command action'ını oynatır
     */
    private void playEntityCommand(com.reportsystem.common.replay.actions.EntityCommandAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Entity Cmd] " + action.getCommandType().name() +
                        " on " + action.getEntityType());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Entity command: " + action.getCommandType().name());
        });
    }

    /**
     * Container action'ını oynatır
     */
    private void playContainer(com.reportsystem.common.replay.actions.ContainerAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location containerLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            // Konteyner açma/kapama sesleri
            org.bukkit.Sound sound = action.isOpened() ?
                    org.bukkit.Sound.BLOCK_CHEST_OPEN : org.bukkit.Sound.BLOCK_CHEST_CLOSE;

            if (action.getContainerType() == com.reportsystem.common.replay.actions.ContainerAction.ContainerType.ENDER_CHEST) {
                sound = action.isOpened() ?
                        org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN : org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE;
            }

            replayPlayer.getLastLocation().getWorld().playSound(containerLoc, sound, 1.0f, 1.0f);

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Container] " + action.getContainerType().name() +
                        " " + (action.isOpened() ? "opened" : "closed"));
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Container: " + action.getContainerType().name());
        });
    }

    /**
     * Block ignite action'ını oynatır
     */
    private void playBlockIgnite(com.reportsystem.common.replay.actions.BlockIgniteAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location igniteLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            // Ateş efektleri
            replayPlayer.getLastLocation().getWorld().spawnParticle(
                    org.bukkit.Particle.FLAME, igniteLoc, 20, 0.5, 0.5, 0.5, 0.02);
            replayPlayer.getLastLocation().getWorld().playSound(
                    igniteLoc, org.bukkit.Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Fire] Ignited with " + action.getIgniteType().name());
            }
            plugin.getLogger().info("[REPLAY-DEBUG] Block ignite: " + action.getIgniteType().name());
        });
    }

    /**
     * Nearby player action'ını oynatır
     * Bu action bir nearby player'a ait, ana NPC değil
     */
    private void playNearbyPlayerAction(ReplayAction action) {
        UUID ownerUUID = action.getOwnerUUID();
        if (ownerUUID == null) {
            plugin.getLogger().warning("[REPLAY-ERROR] Nearby player action has null ownerUUID!");
            return;
        }

        // Bu nearby player'ın entity ID'sini bul
        Integer nearbyEntityId = replayPlayer.getNpcManager().getNearbyPlayerEntityId(ownerUUID);
        if (nearbyEntityId == null) {
            plugin.getLogger().fine("[REPLAY-DEBUG] Nearby player " + ownerUUID +
                    " not spawned yet, skipping action: " + action.getClass().getSimpleName());
            return;
        }

        plugin.getLogger().info("[REPLAY-DEBUG] Playing nearby player action: " +
                action.getClass().getSimpleName() + " for entity " + nearbyEntityId);

        // Action tipine göre handle et
        if (action instanceof HealthAction) {
            playNearbyHealth((HealthAction) action, nearbyEntityId);
        } else if (action instanceof AnimationAction) {
            playNearbyAnimation((AnimationAction) action, nearbyEntityId);
        } else if (action instanceof DeathAction) {
            playNearbyDeath((DeathAction) action, nearbyEntityId);
        } else if (action instanceof PoseAction) {
            playNearbyPose((PoseAction) action, nearbyEntityId);
        } else if (action instanceof VehicleAction) {
            playNearbyVehicle((VehicleAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof EquipmentAction) {
            playNearbyEquipment((EquipmentAction) action, nearbyEntityId);
        } else if (action instanceof BlockAction) {
            playNearbyBlock((BlockAction) action);
        } else if (action instanceof ItemAction) {
            playNearbyItem((ItemAction) action);
        } else if (action instanceof PotionEffectAction) {
            playNearbyPotionEffect((PotionEffectAction) action, nearbyEntityId);
        } else if (action instanceof ChatAction) {
            playNearbyChat((ChatAction) action);
        } else if (action instanceof UseItemAction) {
            playNearbyUseItem((UseItemAction) action, nearbyEntityId);
        } else {
            // Diğer action type'ları için henüz destek yok
            plugin.getLogger().fine("[REPLAY-DEBUG] Unsupported nearby player action: " +
                    action.getClass().getSimpleName());
        }
    }

    /**
     * Nearby player için health action oynatır
     */
    private void playNearbyHealth(HealthAction action, int entityId) {
        replayPlayer.getNpcManager().sendNearbyPlayerHealth(entityId, action.getHealth(), action.getMaxHealth());
    }

    /**
     * Nearby player için animation oynatır
     */
    private void playNearbyAnimation(AnimationAction action, int entityId) {
        WrapperPlayServerEntityAnimation.EntityAnimationType animationType;
        switch (action.getAnimationType()) {
            case SWING_MAIN_HAND:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM;
                break;
            case SWING_OFF_HAND:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND;
                break;
            case TAKE_DAMAGE:
                animationType = WrapperPlayServerEntityAnimation.EntityAnimationType.HURT;
                break;
            default:
                return;
        }

        WrapperPlayServerEntityAnimation animPacket = new WrapperPlayServerEntityAnimation(
                entityId, animationType);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, animPacket);
        }
    }

    /**
     * Nearby player için death action oynatır
     */
    private void playNearbyDeath(DeathAction action, int entityId) {
        // Death animation - status 3
        WrapperPlayServerEntityStatus statusPacket = new WrapperPlayServerEntityStatus(
                entityId, (byte) 3);

        // Death pose
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.DYING));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, statusPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);

            // Death message
            if (action.getDeathMessage() != null && !action.getDeathMessage().isEmpty()) {
                viewer.sendMessage("§c" + action.getDeathMessage());
            }
        }
    }

    /**
     * Nearby player için pose action oynatır
     */
    private void playNearbyPose(PoseAction action, int entityId) {
        EntityPose pose;
        switch (action.getPoseType()) {
            case SNEAKING:
                pose = EntityPose.CROUCHING;
                break;
            case SWIMMING:
                pose = EntityPose.SWIMMING;
                break;
            case DYING:
                pose = EntityPose.DYING;
                break;
            case SLEEPING:
                pose = EntityPose.SLEEPING;
                break;
            case SPIN_ATTACK:
                pose = EntityPose.SPIN_ATTACK;
                break;
            case STANDING:
            default:
                pose = EntityPose.STANDING;
                break;
        }

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, pose));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }
    }

    /**
     * Nearby player için vehicle action oynatır
     */
    private void playNearbyVehicle(VehicleAction action, int entityId, UUID ownerUUID) {
        // Vehicle mount/dismount - bu daha kompleks, şimdilik basit implement
        plugin.getLogger().info("[REPLAY-DEBUG] Nearby player vehicle action: " +
                action.getActionType() + " - " + action.getVehicleType());

        // TODO: Implement vehicle mounting for nearby players
    }

    /**
     * Nearby player için equipment action oynatır
     */
    private void playNearbyEquipment(EquipmentAction action, int entityId) {
        // Tüm viewer'lara equipment paketi gönder
        for (Player viewer : replayPlayer.getViewers()) {
            replayPlayer.getNpcManager().sendNearbyPlayerEquipment(
                    viewer,
                    entityId,
                    java.util.Collections.singletonMap(action.getSlot(), action.getItemData())
            );
        }
    }

    /**
     * Nearby player için block action oynatır
     */
    private void playNearbyBlock(BlockAction action) {
        // Block action'ı world'de oynat (viewer'lar zaten görüyor)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (replayPlayer.getLastLocation() != null && replayPlayer.getLastLocation().getWorld() != null) {
                org.bukkit.Location blockLoc = new org.bukkit.Location(
                        replayPlayer.getLastLocation().getWorld(),
                        action.getX(),
                        action.getY(),
                        action.getZ()
                );

                if (action.getActionType() == BlockAction.BlockActionType.PLACE_BLOCK) {
                    Material blockMaterial = Material.getMaterial(action.getBlockType());
                    if (blockMaterial != null) {
                        blockLoc.getBlock().setType(blockMaterial);
                    }
                } else if (action.getActionType() == BlockAction.BlockActionType.STOP_BREAKING) {
                    blockLoc.getBlock().setType(Material.AIR);
                }

                plugin.getLogger().info("[REPLAY-DEBUG] Nearby player block action: " +
                        action.getActionType() + " at " + blockLoc);
            }
        });
    }

    /**
     * Nearby player için item action oynatır
     */
    private void playNearbyItem(ItemAction action) {
        // Item drop/pickup - world'de item entity spawn et
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (replayPlayer.getLastLocation() != null && replayPlayer.getLastLocation().getWorld() != null) {
                org.bukkit.Location itemLoc = new org.bukkit.Location(
                        replayPlayer.getLastLocation().getWorld(),
                        action.getX(),
                        action.getY(),
                        action.getZ()
                );

                if (action.getActionType() == ItemAction.ItemActionType.DROP) {
                    try {
                        byte[] itemBytes = java.util.Base64.getDecoder().decode(action.getItemData());
                        org.bukkit.inventory.ItemStack itemStack =
                                com.reportsystem.spigot.utils.ItemSerializer.itemStackFromBytes(itemBytes);

                        if (itemStack != null) {
                            itemLoc.getWorld().dropItem(itemLoc, itemStack);
                            plugin.getLogger().info("[REPLAY-DEBUG] Nearby player item drop: " +
                                    itemStack.getType());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[REPLAY-DEBUG] Failed to deserialize item: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Nearby player için potion effect oynatır
     */
    private void playNearbyPotionEffect(PotionEffectAction action, int entityId) {
        // Potion effect metadata gönder
        // TODO: PacketEvents ile potion effect metadata implement et
        plugin.getLogger().info("[REPLAY-DEBUG] Nearby player potion effect: " +
                action.getEffectType() + " (Level " + action.getAmplifier() + ")");
    }

    /**
     * Nearby player için chat action oynatır
     */
    private void playNearbyChat(ChatAction action) {
        // Chat mesajını viewer'lara göster
        String chatMessage = "§7[Nearby] §f" + action.getMessage();

        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendMessage(chatMessage);
        }

        plugin.getLogger().info("[REPLAY-DEBUG] Nearby player chat: " + action.getMessage());
    }

    /**
     * Nearby player için use item action oynatır
     */
    private void playNearbyUseItem(UseItemAction action, int entityId) {
        // Item kullanma animasyonu/efekti
        // TODO: Eat/drink animation için entity metadata gönder
        plugin.getLogger().info("[REPLAY-DEBUG] Nearby player use item: " +
                action.getUseType() + " - Duration: " + action.getDuration());
    }

    /**
     * ItemData'yı PacketEvents ItemStack'e çevirir
     */
    public com.github.retrooper.packetevents.protocol.item.ItemStack convertItemData(
            EquipmentAction.ItemData itemData) {
        if (itemData == null || itemData.getItemStackData() == null) {
            return null;
        }

        try {
            // ItemData'dan Bukkit ItemStack'i deserialize et
            org.bukkit.inventory.ItemStack bukkitItem =
                    com.reportsystem.spigot.utils.ItemSerializer.itemStackFromBytes(itemData.getItemStackData());

            if (bukkitItem == null || bukkitItem.getType() == Material.AIR) {
                return null;
            }

            // Bukkit ItemStack'i PacketEvents ItemStack'e çevir
            String materialName = bukkitItem.getType().name().toLowerCase();
            com.github.retrooper.packetevents.protocol.item.type.ItemType itemType =
                    com.github.retrooper.packetevents.protocol.item.type.ItemTypes.getByName(
                            "minecraft:" + materialName
                    );

            // Bulunamazsa özel mapping kullan
            if (itemType == null) {
                itemType = getMappedItemType(bukkitItem.getType());
            }

            // Hala bulunamazsa varsayılan kullan
            if (itemType == null) {
                plugin.getLogger().warning("[REPLAY-DEBUG] Unknown item type for nearby player: " +
                        bukkitItem.getType().name() + ", using STONE as fallback");
                itemType = com.github.retrooper.packetevents.protocol.item.type.ItemTypes.STONE;
            }

            return com.github.retrooper.packetevents.protocol.item.ItemStack.builder()
                    .type(itemType)
                    .amount(bukkitItem.getAmount())
                    .build();
        } catch (Exception e) {
            plugin.getLogger().warning("[REPLAY-DEBUG] Failed to convert ItemData: " + e.getMessage());
            return null;
        }
    }

    // Getter'lar
    public byte getCurrentEntityFlags() { return currentEntityFlags; }
    public Map<Integer, Entity> getMountedEntities() { return mountedEntities; }
}