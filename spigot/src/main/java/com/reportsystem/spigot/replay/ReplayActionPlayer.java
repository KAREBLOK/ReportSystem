package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.utils.ItemSerializer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

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

    // Packet tabanlı fishing bobber entity ID'si (replay olta ipi için)
    private int fishingBobberEntityId = -1;

    // Civar oyuncuların fishing bobber entity ID'leri (ownerUUID -> bobberEntityId)
    private final Map<UUID, Integer> nearbyFishingBobbers = new HashMap<>();

    // Spawn edilen entity'leri takip etmek için (replay bitince silinecek)
    private final List<Entity> spawnedEntities = new ArrayList<>();
    // UUID -> Entity mapping (entity movement için)
    private final Map<UUID, Entity> spawnedEntitiesMap = new HashMap<>();

    public ReplayActionPlayer(JavaPlugin plugin, ReplayPlayer replayPlayer) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
    }

    /**
     * Replay başlarken kayıt öncesinde zaten var olan blokları gösterir.
     * Her pozisyondaki İLK BlockAction'a bakarak karar verir:
     * - İlk action START_BREAKING ise → blok kayıt başlamadan önce vardı → göster
     * - İlk action başka herhangi bir şey ise → kayıt sırasında oluştu/değişti → gösterme
     * Bu şekilde su, lav, kapı, piston, blok koyma vs. ne olursa olsun doğru çalışır.
     */
    public void showInitialBlocks() {
        // Her pozisyondaki ilk BlockAction'ı bul (kronolojik sırada)
        Map<String, BlockAction> firstActionAtPosition = new LinkedHashMap<>();

        for (ReplayAction action : replayPlayer.getActions()) {
            if (!(action instanceof BlockAction)) continue;
            BlockAction blockAction = (BlockAction) action;
            String blockKey = blockAction.getX() + "," + blockAction.getY() + "," + blockAction.getZ();

            // Sadece ilk action'ı kaydet - pozisyondaki ilk etkileşim ne olduğunu belirler
            firstActionAtPosition.putIfAbsent(blockKey, blockAction);
        }

        // İlk action'ı START_BREAKING olan pozisyonları göster
        int showedCount = 0;
        for (Map.Entry<String, BlockAction> entry : firstActionAtPosition.entrySet()) {
            BlockAction blockAction = entry.getValue();

            // İlk action START_BREAKING değilse → kayıt sırasında oluştu/değişti → atla
            if (blockAction.getActionType() != BlockAction.BlockActionType.START_BREAKING) continue;
            if (blockAction.getBlockType() == null) continue;

            Material blockMaterial = Material.getMaterial(blockAction.getBlockType());
            if (blockMaterial == null || blockMaterial == Material.AIR) continue;

            String blockKey = entry.getKey();
            Location blockLoc = new Location(
                    replayPlayer.getLastLocation().getWorld(),
                    blockAction.getX(), blockAction.getY(), blockAction.getZ()
            );

            if (!originalBlocks.containsKey(blockKey)) {
                originalBlocks.put(blockKey, blockLoc.getBlock().getType());
            }

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendBlockChange(blockLoc, blockMaterial.createBlockData());
            }
            showedCount++;
        }

        if (showedCount > 0) {
            ReportSystemSpigot.getInstance().debug("[REPLAY] Pre-showed " + showedCount + " pre-existing blocks");
        }
    }

    /**
     * Seek/rewind/forward sonrası hedef index'e kadar blok değişikliklerini uygular.
     * Sesler ve efektler olmadan sadece görsel durumu günceller.
     * Bu sayede seek sonrası blok durumu doğru görünür (kırılmış bloklar kırık, konmuş bloklar konmuş).
     */
    public void replayBlockStateUpTo(int targetIndex) {
        List<ReplayAction> actions = replayPlayer.getActions();
        if (actions.isEmpty()) return;

        org.bukkit.World world = replayPlayer.getLastLocation() != null ? replayPlayer.getLastLocation().getWorld() : null;
        if (world == null) return;

        // Her pozisyondaki son blok durumunu takip et
        Map<String, String> blockStateAtTarget = new LinkedHashMap<>();

        for (int i = 0; i <= targetIndex && i < actions.size(); i++) {
            ReplayAction action = actions.get(i);
            if (!(action instanceof BlockAction)) continue;
            BlockAction blockAction = (BlockAction) action;
            String blockKey = blockAction.getX() + "," + blockAction.getY() + "," + blockAction.getZ();

            switch (blockAction.getActionType()) {
                case STOP_BREAKING:
                    if (blockAction.getStage() == 10) {
                        blockStateAtTarget.put(blockKey, null); // Kırıldı = AIR
                    }
                    break;
                case PLACE_BLOCK:
                    if (blockAction.getBlockType() != null) {
                        blockStateAtTarget.put(blockKey, blockAction.getBlockType());
                    }
                    break;
                default:
                    break;
            }
        }

        // Blok durumlarını uygula
        int appliedCount = 0;
        for (Map.Entry<String, String> entry : blockStateAtTarget.entrySet()) {
            String[] coords = entry.getKey().split(",");
            if (coords.length != 3) continue;

            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                Location loc = new Location(world, x, y, z);

                // originalBlocks'a kaydet (gelecekteki restore için)
                if (!originalBlocks.containsKey(entry.getKey())) {
                    originalBlocks.put(entry.getKey(), loc.getBlock().getType());
                }

                if (entry.getValue() == null) {
                    // Blok kırılmış - AIR göster
                    for (Player viewer : replayPlayer.getViewers()) {
                        viewer.sendBlockChange(loc, Material.AIR.createBlockData());
                    }
                } else {
                    // Blok konmuş - bloğu göster
                    try {
                        String[] parts = entry.getValue().split(":");
                        Material material = Material.valueOf(parts[0]);
                        if (material != Material.AIR) {
                            org.bukkit.block.data.BlockData blockData = material.createBlockData();
                            for (Player viewer : replayPlayer.getViewers()) {
                                viewer.sendBlockChange(loc, blockData);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // Bilinmeyen material - atla
                    }
                }
                appliedCount++;
            } catch (NumberFormatException e) {
                // Geçersiz koordinat - atla
            }
        }

        if (appliedCount > 0) {
            ReportSystemSpigot.getInstance().debug("[REPLAY] Applied " + appliedCount + " block states up to index " + targetIndex);
        }
    }

    /**
     * Bir action'ı oynatır
     */
    public void playAction(ReplayAction action) {
        // Debug log
        if (plugin.getConfig().getBoolean("debug", false)) {
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing action: " + action.getClass().getSimpleName());
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
            // Entity henüz spawn edilmediyse (PLAYER_APPEAR gelmeden LocationAction geldi) sessizce atla
            Integer nearbyEntityId = replayPlayer.getNpcManager().getNearbyPlayerEntityId(action.getOwnerUUID());
            if (nearbyEntityId == null) {
                return; // Entity yok - PLAYER_APPEAR gelince spawn olacak
            }

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

        // Araçta ise araç teleportu kullan, değilse her zaman absolute teleport kullan
        // Relative move (delta bazlı) client-side pozisyon kaymasına neden olabiliyor,
        // özellikle seek/rewind/forward sonrası NPC'yi eski konumuna çekiyor.
        // Absolute teleport her zaman doğru koordinatı gönderir - desync riski yok.
        if (!mountedEntities.isEmpty()) {
            replayPlayer.getNpcManager().teleportNPC(newLocation, mountedEntities);
        } else {
            replayPlayer.getNpcManager().absoluteTeleportNPC(newLocation);
        }
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Entity state: " + action.getStateType() +
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player entity ID not found for UUID: " + action.getEntityUUID());
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

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player state updated: EntityID=" + nearbyEntityId +
                " | State=" + action.getStateType() + " | Enabled=" + action.isEnabled());
    }

    /**
     * Equipment action'ını oynatır
     */
    private void playEquipment(EquipmentAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing equipment: " + action.getSlot() +
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
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to create item: " + e.getMessage());
            }
        }

        if (slot != null) {
            List<Equipment> equipmentList = new ArrayList<>();
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem = null;

            if (item != null && item.getType() != Material.AIR) {
                try {
                    packetItem = SpigotConversionUtil.fromBukkitItemStack(item);

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Created packet item: " +
                                item.getType().name() + " x" + item.getAmount());
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
     * Bukkit ItemStack'i PacketEvents ItemStack'e çevirir
     */
    private com.github.retrooper.packetevents.protocol.item.ItemStack convertBukkitItem(ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType() == Material.AIR) {
            return null;
        }
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }

    /**
     * Blok aksiyonlarını oynatır
     */
    private void playBlockAction(BlockAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Block action: " + action.getActionType() +
                " at " + action.getX() + "," + action.getY() + "," + action.getZ());

        Location blockLoc = new Location(
                replayPlayer.getLastLocation().getWorld(),
                action.getX(), action.getY(), action.getZ()
        );

        switch (action.getActionType()) {
            case START_BREAKING:
                // Blok artık gerçek dünyada yok olabilir, kaydedilen blok tipini göster
                if (action.getBlockType() != null) {
                    Material blockMaterial = Material.getMaterial(action.getBlockType());
                    if (blockMaterial != null && blockMaterial != Material.AIR) {
                        String blockKey = action.getX() + "," + action.getY() + "," + action.getZ();
                        if (!originalBlocks.containsKey(blockKey)) {
                            originalBlocks.put(blockKey, blockLoc.getBlock().getType());
                        }
                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.sendBlockChange(blockLoc, blockMaterial.createBlockData());
                        }
                    }
                }
                // Sadece başlangıç animasyonunu gönder (stage 0)
                // Gerçek ilerleme BREAK_PROGRESS action'ları ile gelecek (doğru zamanlamalarla)
                replayPlayer.getNpcManager().sendBlockBreakAnimation(
                        action.getX(), action.getY(), action.getZ(), 0
                );
                break;

            case BREAK_PROGRESS:
                replayPlayer.getNpcManager().sendBlockBreakAnimation(
                        action.getX(), action.getY(), action.getZ(), action.getStage()
                );
                break;

            case STOP_BREAKING:
                // Animasyonu sıfırla
                replayPlayer.getNpcManager().sendBlockBreakAnimation(
                        action.getX(), action.getY(), action.getZ(), -1
                );

                // stage == 10: BlockBreakEvent'ten geliyor → blok gerçekten kırıldı
                // stage == 0: CANCELLED_DIGGING veya FINISHED_DIGGING paketi → sadece animasyonu sıfırla
                if (action.getStage() == 10) {
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
                }
                break;

            case PLACE_BLOCK:
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Block place at " +
                        action.getX() + "," + action.getY() + "," + action.getZ() +
                        " type: " + action.getBlockType());

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
                            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Skipping complex block: " + material.name());
                            break;
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
                                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to send bed block change to " + viewer.getName() + ": " + e.getMessage());
                                    }
                                }

                                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Bed placed: " + part + " at " + blockLoc +
                                        ", other half: " + otherPart + " at " + otherBlock.getLocation());

                                break; // Skip normal block placement
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
                                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to send block change to " + viewer.getName() + ": " + e.getMessage());
                            }
                        }

                        // Fizik blokları (su, lav, hava) için ses çalma
                        if (!isPhysicsBlock(material)) {
                            playBlockPlaceSound(blockLoc, material);
                        }
                    } catch (Exception e) {
                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Invalid block type: " + action.getBlockType());
                        e.printStackTrace();
                    }
                }
                break;

            case INTERACT_BLOCK:
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Block interact: " + action.getBlockType() +
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

                                    playSoundToViewers(blockLoc, doorSound, 1.0f, 1.0f);

                                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Door state changed: " +
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

                                    playSoundToViewers(blockLoc, trapdoorSound, 1.0f, 1.0f);
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
                                    playSoundToViewers(blockLoc, gateSound, 1.0f, 1.0f);
                                }
                            } else if (interactionType.contains("BUTTON")) {
                                playSoundToViewers(blockLoc, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.3f, 0.6f);
                                // Particle efekti
                                spawnParticleForViewers(Particle.CRIT,
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

                                    playSoundToViewers(blockLoc, Sound.BLOCK_LEVER_CLICK, 0.3f, 0.6f);
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

        playSoundToViewers(location.add(0.5, 0.5, 0.5), breakSound, 1.0f, 1.0f);

        spawnParticleForViewers(
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

        playSoundToViewers(location.add(0.5, 0.5, 0.5), placeSound, 1.0f, 1.0f);
    }

    /**
     * Fizik/akışkan blokları kontrol eder - bunlar için ses efekti çalınmaz
     */
    private boolean isPhysicsBlock(Material material) {
        return material == Material.WATER
                || material == Material.LAVA
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    /**
     * Hasar animasyonunu oynatır
     */
    private void playDamageAnimation(DamageAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Damage animation: " + action.getDamageType() +
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

                    playSoundToViewers(loc, damageSound, 1.0f, 1.0f);
                }
            });
        }
    }

    /**
     * Velocity'yi oynatır
     */
    private void playVelocity(VelocityAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing velocity: " +
                String.format("%.2f, %.2f, %.2f", action.getVelocityX(), action.getVelocityY(), action.getVelocityZ()));

        replayPlayer.getNpcManager().sendVelocity(
                action.getVelocityX(), action.getVelocityY(), action.getVelocityZ()
        );
    }

    /**
     * UseItem action'ını oynatır
     */
    private void playUseItem(UseItemAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing use item: " + action.getUseType() +
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
                    playSoundToViewers(loc, Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
                }
            });
        }
    }

    /**
     * Projectile action'ını oynatır
     */
    private void playProjectile(ProjectileAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing projectile: " + action.getType());

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
                playSoundToViewers(spawnLoc, getSoundForProjectile(action.getType()), 1.0f, 1.0f);
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
                arrow.setDamage(0);
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow.setSilent(true);
                arrow.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                hideFromNonViewers(arrow);
                scheduleRemove(arrow, 100L);
                break;
            case SNOWBALL:
                Snowball snowball = world.spawn(location, Snowball.class);
                snowball.setVelocity(velocity);
                snowball.setSilent(true);
                snowball.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                hideFromNonViewers(snowball);
                scheduleRemove(snowball, 100L);
                break;
            case ENDER_PEARL:
                // Ender pearl teleport edebilir, sadece görsel efekt için ses çal
                playSoundToViewers(location, Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);
                // Gerçek pearl spawn etme - tehlikeli
                break;
            case EGG:
                Egg egg = world.spawn(location, Egg.class);
                egg.setVelocity(velocity);
                egg.setSilent(true);
                egg.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                hideFromNonViewers(egg);
                scheduleRemove(egg, 100L);
                break;
            case FISHING_HOOK:
                // FishHook Bukkit API ile spawn edilemez - sadece ses çal
                playSoundToViewers(location, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);
                break;
            case EXPERIENCE_BOTTLE:
                // XP bottle gerçek XP verebilir, sadece ses çal
                playSoundToViewers(location, Sound.ENTITY_EXPERIENCE_BOTTLE_THROW, 1.0f, 1.0f);
                break;
            case SPLASH_POTION:
            case LINGERING_POTION:
                // Potionlar efekt verebilir, sadece ses çal
                playSoundToViewers(location, Sound.ENTITY_SPLASH_POTION_THROW, 1.0f, 1.0f);
                break;
            case TRIDENT:
                Trident trident = world.spawn(location, Trident.class);
                trident.setVelocity(velocity);
                trident.setDamage(0);
                trident.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                trident.setSilent(true);
                trident.setMetadata(REPLAY_META, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                hideFromNonViewers(trident);
                scheduleRemove(trident, 100L);
                break;
            case FIREWORK_ROCKET:
                // Firework patlayabilir ve hasar verebilir, sadece ses çal
                playSoundToViewers(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing pose: " + action.getPoseType());

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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing death: " + action.getDeathMessage());

        // Önce canı 0 yap (client ölüm animasyonunu sadece can 0 iken oynatır)
        List<EntityData<?>> healthMeta = new ArrayList<>();
        healthMeta.add(new EntityData(9, EntityDataTypes.FLOAT, 0.0f));
        replayPlayer.getNpcManager().sendMetadata(healthMeta);

        // Ölüm animasyonu (entity status 3 = death)
        replayPlayer.getNpcManager().sendEntityStatus((byte) 3);

        // Ölüm efektleri
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!replayPlayer.getViewers().isEmpty() && replayPlayer.getLastLocation() != null) {
                Location deathLoc = new Location(replayPlayer.getLastLocation().getWorld(),
                        action.getDeathX(), action.getDeathY(), action.getDeathZ());

                playSoundToViewers(deathLoc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

                spawnParticleForViewers(
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing fire: " + action.isOnFire() +
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
                    playSoundToViewers(loc, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.7f, 1.0f);

                    spawnParticleForViewers(
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing vehicle: " + action.getActionType() +
                " type: " + action.getVehicleType());

        if (action.getActionType() == VehicleAction.ActionType.PLACE) {
            // Vehicle'ı sadece spawn et, mount etme
            final int vehGen = replayPlayer.getActionHandler().getSeekGeneration();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (vehGen != replayPlayer.getActionHandler().getSeekGeneration()) return;
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
                            vehicle.setSilent(true);
                            spawnedEntities.add(vehicle);
                            hideFromNonViewers(vehicle);

                            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Vehicle placed: " +
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing bed: " + action.getActionType() +
                " at " + action.getBedX() + "," + action.getBedY() + "," + action.getBedZ());

        if (action.getActionType() == BedAction.ActionType.ENTER_BED) {
            playPose(new PoseAction(PoseAction.PoseType.SLEEPING));

            Location bedLoc = new Location(replayPlayer.getLastLocation().getWorld(),
                    action.getBedX() + 0.5, action.getBedY() + 0.5625, action.getBedZ() + 0.5);

            replayPlayer.getNpcManager().absoluteTeleportNPC(bedLoc);
            replayPlayer.setLastLocation(bedLoc);
        } else {
            playPose(new PoseAction(PoseAction.PoseType.STANDING));
        }
    }

    /**
     * Chat action'ını oynatır
     */
    private void playChat(ChatAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing chat: " + action.getMessage());

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
     * Fishing action'ını oynatır - Packet tabanlı FishHook ile gerçek olta ipi
     */
    private void playFishing(FishingAction action) {
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
                    // Önceki bobber varsa kaldır
                    despawnFishingBobber();

                    // Olta atma sesi
                    playSoundToViewers(playerLoc, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);

                    int npcEntityId = replayPlayer.getNpcManager().getEntityId();
                    fishingBobberEntityId = ReplayNPCManager.generateEntityId();

                    // NPC'nin göz seviyesinde, baktığı yönde hafif önde spawn et
                    double yawRad = Math.toRadians(playerLoc.getYaw());

                    double spawnX = playerLoc.getX() - Math.sin(yawRad) * 0.3;
                    double spawnY = playerLoc.getY() + 1.62; // Göz seviyesi
                    double spawnZ = playerLoc.getZ() + Math.cos(yawRad) * 0.3;

                    // Velocity belirle: yeni kayıtlarda hookX/Y/Z gerçek velocity,
                    // eski kayıtlarda hookX/Y/Z konum (büyük değerler) → fallback
                    double velX, velY, velZ;
                    boolean isVelocityData = Math.abs(action.getHookX()) < 5 &&
                                             Math.abs(action.getHookY()) < 5 &&
                                             Math.abs(action.getHookZ()) < 5;

                    if (isVelocityData) {
                        // Yeni kayıt: gerçek hook velocity
                        velX = action.getHookX();
                        velY = action.getHookY();
                        velZ = action.getHookZ();
                    } else {
                        // Eski kayıt: NPC'nin baktığı yöne göre hesapla
                        double pitchRad = Math.toRadians(playerLoc.getPitch());
                        double throwPower = 1.5;
                        velX = -Math.sin(yawRad) * Math.cos(pitchRad) * throwPower;
                        velY = -Math.sin(pitchRad) * throwPower + 0.2;
                        velZ = Math.cos(yawRad) * Math.cos(pitchRad) * throwPower;
                    }

                    ReportSystemSpigot.getInstance().debug("[REPLAY-FISHING] Bobber spawn | velocity=" +
                            String.format("%.3f, %.3f, %.3f", velX, velY, velZ) +
                            " | real=" + isVelocityData +
                            " | entityId=" + fishingBobberEntityId +
                            " | owner=" + npcEntityId);

                    WrapperPlayServerSpawnEntity spawnBobber = new WrapperPlayServerSpawnEntity(
                            fishingBobberEntityId,
                            Optional.of(UUID.randomUUID()),
                            EntityTypes.FISHING_BOBBER,
                            new Vector3d(spawnX, spawnY, spawnZ),
                            (float) playerLoc.getPitch(),
                            (float) playerLoc.getYaw(),
                            0f,
                            npcEntityId, // data = owner entity ID
                            Optional.of(new Vector3d(velX, velY, velZ))
                    );

                    for (Player viewer : replayPlayer.getViewers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnBobber);
                    }

                    // Velocity paketini de gönder — client kesinlikle uygulasın
                    WrapperPlayServerEntityVelocity velPacket = new WrapperPlayServerEntityVelocity(
                            fishingBobberEntityId,
                            new Vector3d(velX, velY, velZ)
                    );
                    for (Player viewer : replayPlayer.getViewers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, velPacket);
                    }
                    break;

                case CAUGHT:
                    // Bobber'ı kaldır
                    despawnFishingBobber();

                    playSoundToViewers(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                    spawnParticleForViewers(Particle.SPLASH, hookLoc, 20, 0.3, 0.3, 0.3, 0.1);
                    break;

                case REEL_IN:
                    // Bobber'ı kaldır
                    despawnFishingBobber();

                    playSoundToViewers(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.5f, 1.5f);
                    spawnParticleForViewers(Particle.BUBBLE_POP, hookLoc, 10, 0.2, 0.2, 0.2, 0.05);
                    break;

                case FAILED:
                    // Bobber'ı kaldır
                    despawnFishingBobber();

                    playSoundToViewers(hookLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
                    break;
            }
        });
    }

    /**
     * Packet tabanlı fishing bobber entity'sini kaldırır
     */
    private void despawnFishingBobber() {
        if (fishingBobberEntityId != -1) {
            WrapperPlayServerDestroyEntities destroy =
                    new WrapperPlayServerDestroyEntities(fishingBobberEntityId);
            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
            }
            fishingBobberEntityId = -1;
        }
    }

    /**
     * InteractEntity action'ını oynatır
     */
    private void playInteractEntity(InteractEntityAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing entity interaction: " + action.getType());

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
                        spawnParticleForViewers(Particle.DRIPPING_WATER, nearbyEntity.getLocation(), 10, 0.3, 0.3, 0.3, 0.1);

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
                        spawnParticleForViewers(Particle.HAPPY_VILLAGER, nearbyEntity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);

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
                        spawnParticleForViewers(Particle.ITEM,
                                loc.clone().add(0, 0.5, 0),
                                20,
                                0.3, 0.3, 0.3,
                                0.05,
                                new ItemStack(woolMaterial)
                        );

                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Shearing effect with wool color: " + woolColor.name());

                        // Replay sırasında spawn edilen koyunu kırpılmış olarak göster
                        // Gerçek dünya entity'lerini değil, sadece spawnedEntities listesindeki entity'leri etkile
                        for (Entity entity : spawnedEntities) {
                            if (entity instanceof org.bukkit.entity.Sheep && entity.isValid()) {
                                // Lokasyon yakınlığı kontrolü (2 blok yarıçap)
                                if (entity.getLocation().distance(loc) < 2.0) {
                                    org.bukkit.entity.Sheep sheep = (org.bukkit.entity.Sheep) entity;
                                    if (!sheep.isSheared()) {
                                        sheep.setSheared(true);
                                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Spawned sheep sheared at " +
                                            sheep.getLocation() + ", color: " + sheep.getColor().name());
                                        break; // Sadece en yakın koyunu kırp
                                    }
                                }
                            }
                        }
                        break;

                    case FEED:
                        spawnParticleForViewers(Particle.HEART, loc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
                        break;

                    case TAME:
                        spawnParticleForViewers(Particle.HEART, loc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0);
                        viewer.playSound(loc, Sound.ENTITY_WOLF_WHINE, 1.0f, 1.0f);
                        break;

                    case BREED:
                        spawnParticleForViewers(Particle.HEART, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
                        viewer.playSound(loc, Sound.ENTITY_DONKEY_ANGRY, 1.0f, 1.0f);
                        break;

                    case MILK:
                        viewer.playSound(loc, Sound.ENTITY_COW_MILK, 1.0f, 1.0f);
                        break;

                    case TRADE:
                        viewer.playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                        break;

                    case LEFT_CLICK:
                        spawnParticleForViewers(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
                        break;
                }
            }
        });
    }

    /**
     * Teleport action'ını oynatır
     */
    private void playTeleport(TeleportAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing teleport: " + action.getCause());

        // Seek generation'ı kaydet - eski deferred teleport'lar seek sonrası çalışmasın
        final int currentGen = replayPlayer.getActionHandler().getSeekGeneration();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Seek yapıldıysa bu eski teleport'u iptal et
            if (currentGen != replayPlayer.getActionHandler().getSeekGeneration()) return;
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
                viewer.spawnParticle(Particle.PORTAL, fromLoc.clone().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
                viewer.playSound(fromLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

                // Bitiş noktası efektleri
                viewer.spawnParticle(Particle.PORTAL, toLoc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);

                // Eğer farklı bir dünyaya TP yapılıyorsa izleyiciyi de TP ettir
                boolean crossWorldTeleport = !viewer.getWorld().equals(toWorld);

                if (crossWorldTeleport) {
                    // Dünya değişince client tüm entity'leri siler
                    // teleportAsync chunk yükleme ve göndermeyi düzgün yönetir
                    viewer.teleportAsync(toLoc);

                    // NPC respawn + flight/gamemode restore (5 tick sonra dünya değişimi kesinlikle tamamlanmış olur)
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (currentGen != replayPlayer.getActionHandler().getSeekGeneration()) return;
                        if (!viewer.isOnline()) return;

                        // Dünya değişiminde Paper gamemode/flight sıfırlayabilir, zorla geri koy
                        viewer.setGameMode(org.bukkit.GameMode.ADVENTURE);
                        viewer.setAllowFlight(true);
                        viewer.setFlying(true);
                        viewer.setFallDistance(0f);

                        replayPlayer.getNpcManager().respawnForViewer(viewer, toLoc);
                        ReportSystemSpigot.getInstance().debug("[REPLAY] NPC respawned + flight restored in new world for " + viewer.getName());
                    }, 5L);

                    com.reportsystem.spigot.ReportSystemSpigot spigotPlugin = (com.reportsystem.spigot.ReportSystemSpigot) plugin;
                    viewer.sendMessage(spigotPlugin.getMessageManager().colorize(spigotPlugin.getMessageManager().getMessage("replay.world-change").replace("%world%", toWorld.getName())));
                    ReportSystemSpigot.getInstance().debug("[REPLAY] Teleporting viewer " + viewer.getName() +
                            " to world: " + toWorld.getName());
                } else {
                    // Aynı dünya - sadece NPC'yi teleport et
                    replayPlayer.getNpcManager().absoluteTeleportNPC(toLoc);
                }

                replayPlayer.setLastLocation(toLoc);

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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing health: " + action.getHealth() + "/" + action.getMaxHealth() +
                " absorption: " + action.getAbsorption());

        // 1. NPC entity metadata güncelle (index 9 = health) - hasar kırmızı flash animasyonu için
        // + scoreboard below-name güncelle
        replayPlayer.getNpcManager().updateMainNPCHealth(
                action.getHealth(), action.getMaxHealth(), action.getAbsorption());

        for (Player viewer : replayPlayer.getViewers()) {
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing potion effect: " + action.getActionType() + " - " + action.getEffectType());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getLastLocation();

            for (Player viewer : replayPlayer.getViewers()) {
                switch (action.getActionType()) {
                    case ADD:
                        // ENTITY_EFFECT particle'ı Color parametresi gerektiriyor
                        spawnParticleForViewers(Particle.ENTITY_EFFECT,
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
                        spawnParticleForViewers(Particle.CLOUD, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing gamemode: " + action.getGameMode());

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
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
    }

    /**
     * Weather action'ını oynatır
     */
    private void playWeather(WeatherAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing weather: " + action.getWeatherType());

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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing sound: " + action.getSoundName());

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
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Unknown sound: " + action.getSoundName());
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
     * @param viewers blokları restore edilecek oyuncu listesi
     */
    public void restoreBlocks(Set<Player> viewers) {
        if (originalBlocks.isEmpty() || viewers.isEmpty()) return;

        try {
            for (Map.Entry<String, Material> entry : originalBlocks.entrySet()) {
                try {
                    String[] coords = entry.getKey().split(",");
                    if (coords.length == 3) {
                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);
                        int z = Integer.parseInt(coords[2]);

                        for (Player viewer : viewers) {
                            if (viewer != null && viewer.isOnline()) {
                                Location loc = new Location(viewer.getWorld(), x, y, z);
                                viewer.sendBlockChange(loc, loc.getBlock().getBlockData());
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // skip invalid
                }
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] " + originalBlocks.size() + " blok restore edildi.");
        } finally {
            originalBlocks.clear();
        }
    }

    /**
     * Explosion action'ını oynatır
     */
    private void playExplosion(ExplosionAction action) {
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing explosion: " + action.getExplosionType());

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
            spawnParticleForViewers(Particle.EXPLOSION_EMITTER, explosionLoc, 1);
            spawnParticleForViewers(Particle.EXPLOSION, explosionLoc, 10,
                    action.getPower() / 2.0, action.getPower() / 2.0, action.getPower() / 2.0, 0.1);

            // Explosion tipi için özel ses ve particle efektleri
            switch (action.getExplosionType()) {
                case CREEPER:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                    spawnParticleForViewers(Particle.EXPLOSION, explosionLoc, 5, 1, 1, 1, 0.1);
                    break;

                case TNT:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
                    spawnParticleForViewers(Particle.EXPLOSION_EMITTER, explosionLoc, 1);
                    break;

                case BED:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);
                    spawnParticleForViewers(Particle.LAVA, explosionLoc, 20, 2, 2, 2, 0.1);
                    break;

                case END_CRYSTAL:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.2f);
                    spawnParticleForViewers(Particle.DRAGON_BREATH, explosionLoc, 50, 2, 2, 2, 0.1);
                    break;

                case FIREBALL:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
                    spawnParticleForViewers(Particle.FLAME, explosionLoc, 30, 1.5, 1.5, 1.5, 0.1);
                    break;

                case WITHER:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 1.0f);
                    spawnParticleForViewers(Particle.SMOKE, explosionLoc, 40, 2, 2, 2, 0.1);
                    break;

                default:
                    playSoundToViewers(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                    spawnParticleForViewers(Particle.EXPLOSION, explosionLoc, 3, 1, 1, 1, 0.05);
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

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Entity updated: " + action.getEntityType() +
                        " to " + newLoc.toVector());
            } else {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Entity not found for update: UUID " +
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
                spawnedEntity.setSilent(true);
                spawnedEntities.add(spawnedEntity);
                hideFromNonViewers(spawnedEntity);
                spawnedEntitiesMap.put(action.getEntityUuid(), spawnedEntity);

                // Spawn efektleri
                spawnParticleForViewers(Particle.CLOUD, loc, 10, 0.3, 0.3, 0.3, 0.05);

                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.playSound(loc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
                }

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Spawned entity with properties: " +
                        action.getEntityType() + " - " + action.getProperties());

            } catch (Exception e) {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to spawn entity: " +
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
                    spawnParticleForViewers(Particle.ENTITY_EFFECT,
                            entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);

                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sheep dyed to: " + newColor.name());
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

                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sheep dyed (fallback): " + newColor.name());
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing item action: " + action.getActionType());

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
                        // NPC'nin göz seviyesinde, baktığı yönde hafif önde spawn et
                        double yawRad = Math.toRadians(playerLoc.getYaw());
                        Location dropSpawn = playerLoc.clone().add(
                                -Math.sin(yawRad) * 0.3,
                                1.3,
                                Math.cos(yawRad) * 0.3
                        );

                        playSoundToViewers(dropSpawn, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);

                        org.bukkit.entity.Item droppedItem = world.dropItem(dropSpawn, item);

                        // Velocity: yeni kayıtlarda gerçek velocity, eski kayıtlarda fallback
                        boolean isVelData = Math.abs(action.getX()) < 5 &&
                                            Math.abs(action.getY()) < 5 &&
                                            Math.abs(action.getZ()) < 5;

                        if (isVelData) {
                            // Yeni kayıt: gerçek drop velocity
                            droppedItem.setVelocity(new Vector(action.getX(), action.getY(), action.getZ()));
                        } else {
                            // Eski kayıt: NPC'nin baktığı yöne doğru at
                            droppedItem.setVelocity(playerLoc.getDirection().multiply(0.3));
                        }

                        droppedItem.setPickupDelay(Integer.MAX_VALUE);
                        droppedItem.setSilent(true);
                        spawnedEntities.add(droppedItem);
                        hideFromNonViewers(droppedItem);

                        // 5 saniye sonra kaldır
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (droppedItem.isValid()) {
                                droppedItem.remove();
                            }
                        }, 100L);
                        break;

                    case PICKUP:
                        // Item pickup efekti
                        playSoundToViewers(itemLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

                        // Particle efekti
                        spawnParticleForViewers(Particle.ITEM,
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
        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Cleaning up ReplayActionPlayer resources");

        try {
            // Packet tabanlı fishing bobber'ı temizle (ana NPC)
            despawnFishingBobber();

            // Civar oyuncuların fishing bobber'larını temizle
            for (UUID uuid : new HashSet<>(nearbyFishingBobbers.keySet())) {
                despawnNearbyFishingBobber(uuid);
            }

            // FishHook'ları temizle
            cleanupFishHooks();

            // Mount edilmiş entity'leri temizle
            for (Entity entity : mountedEntities.values()) {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }

            // Spawn edilen entity'leri temizle
            int entityCount = spawnedEntities.size();
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Attempting to clean up " + entityCount + " spawned entities");

            for (Entity entity : spawnedEntities) {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Removed spawned entity: " + entity.getType().name() + " at " + entity.getLocation());
                } else {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Entity already invalid or null");
                }
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Cleaned up " + entityCount + " spawned entities");
        } finally {
            mountedEntities.clear();
            spawnedEntities.clear();
        }

        // NOT: Blok restore işlemi artık restoreBlocks(viewers) metodu ile yapılıyor.
        // stop() ve finish() bu metodu çağırıyor.
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
                                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to set painting art: " + action.getPaintingArt() + " - " + e.getMessage());
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
                                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to set item frame item: " + e.getMessage());
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
                                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to set glow item frame item: " + e.getMessage());
                                    }
                                }
                            });
                            break;
                    }

                    if (hanging != null) {
                        spawnedEntities.add(hanging);
                        hideFromNonViewers(hanging);
                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Hanging placed: " + action.getHangingType());
                    }
                } catch (Exception e) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to place hanging: " + e.getMessage());
                }
            } else {
                // BREAK - Yakındaki hanging'i bul ve kaldır
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                    if (entity instanceof Hanging) {
                        entity.remove();
                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Hanging removed");
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
                fallingBlock.setSilent(true);

                spawnedEntities.add(fallingBlock);
                hideFromNonViewers(fallingBlock);

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Falling block spawned: " + material);
            } catch (Exception e) {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to spawn falling block: " + e.getMessage());
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
                baby.setSilent(true);

                // Baby olarak ayarla
                if (baby instanceof Ageable) {
                    ((Ageable) baby).setBaby();
                }

                spawnedEntities.add(baby);
                hideFromNonViewers(baby);

                // Kalp parçacıkları
                spawnParticleForViewers(Particle.HEART, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Baby entity spawned: " + entityType);
            } catch (Exception e) {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to spawn baby entity: " + e.getMessage());
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

                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Bucket emptied: " + action.getBucketType());
                }
            } else {
                // FILL - Bloğu kaldır
                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                }

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Bucket filled: " + action.getBucketType());
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

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Note played: " + instrument + " note " + action.getNote());
            } catch (Exception e) {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to play note: " + e.getMessage());
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

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sign placed: " + String.join(" | ", action.getLines()));
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Anvil action: " + action.getActionType());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Brewing: " + action.getIngredient());
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
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Crafted: " + item.getType().name() + " x" + action.getAmount());
                } catch (Exception e) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to deserialize crafted item: " + e.getMessage());
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
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Enchanted: " + item.getType().name());
                } catch (Exception e) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to deserialize enchanted item: " + e.getMessage());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Utility block: " + action.getUtilityType().name());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Book edited: " + action.getTitle());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Portal: " + action.getPortalType().name());
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
                    spawnParticleForViewers(org.bukkit.Particle.HAPPY_VILLAGER, farmLoc, 10);
                    break;
                case FARMLAND_TRAMPLE:
                    playSoundToViewers(farmLoc, org.bukkit.Sound.BLOCK_GRASS_BREAK, 1.0f, 1.0f);
                    break;
            }

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Farming] " + action.getFarmingType().name());
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Farming: " + action.getFarmingType().name());
        });
    }

    /**
     * Player state action'ını oynatır
     */
    private void playPlayerState(com.reportsystem.common.replay.actions.PlayerStateAction action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Pose değişikliklerini PacketEvents ile gönder
            String stateMsg = action.getStateType().name() + ": " + (action.isEnabled() ? "ON" : "OFF");
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Player state: " + stateMsg);
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
                    playSoundToViewers(decorLoc, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    break;
            }

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Decoration] " + action.getDecorationType().name());
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Decoration: " + action.getDecorationType().name());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Redstone: " + action.getRedstoneType().name());
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Entity command: " + action.getCommandType().name());
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

            playSoundToViewers(containerLoc, sound, 1.0f, 1.0f);

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Container] " + action.getContainerType().name() +
                        " " + (action.isOpened() ? "opened" : "closed"));
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Container: " + action.getContainerType().name());
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
            spawnParticleForViewers(org.bukkit.Particle.FLAME, igniteLoc, 20, 0.5, 0.5, 0.5, 0.02);
            playSoundToViewers(igniteLoc, org.bukkit.Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);

            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Fire] Ignited with " + action.getIgniteType().name());
            }
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Block ignite: " + action.getIgniteType().name());
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

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Playing nearby player action: " +
                action.getClass().getSimpleName() + " for entity " + nearbyEntityId);

        // Action tipine göre handle et
        if (action instanceof LocationAction) {
            playNearbyLocation((LocationAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof HealthAction) {
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
            playNearbyBlock((BlockAction) action, ownerUUID);
        } else if (action instanceof ItemAction) {
            playNearbyItem((ItemAction) action, ownerUUID);
        } else if (action instanceof PotionEffectAction) {
            playNearbyPotionEffect((PotionEffectAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof ChatAction) {
            playNearbyChat((ChatAction) action);
        } else if (action instanceof UseItemAction) {
            playNearbyUseItem((UseItemAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof EntityStateAction) {
            playNearbyEntityState((EntityStateAction) action);
        } else if (action instanceof ProjectileAction) {
            playNearbyProjectile((ProjectileAction) action, ownerUUID);
        } else if (action instanceof FishingAction) {
            playNearbyFishing((FishingAction) action, ownerUUID);
        } else if (action instanceof FireAction) {
            playNearbyFire((FireAction) action, nearbyEntityId);
        } else if (action instanceof TeleportAction) {
            playNearbyTeleport((TeleportAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof BucketAction) {
            playNearbyBucket((BucketAction) action, ownerUUID);
        } else if (action instanceof ContainerAction) {
            playNearbyContainer((ContainerAction) action, ownerUUID);
        } else if (action instanceof DamageAction) {
            playNearbyDamage((DamageAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof InteractEntityAction) {
            playNearbyInteractEntity((InteractEntityAction) action, nearbyEntityId);
        } else if (action instanceof BreedAction) {
            playNearbyBreed((BreedAction) action, ownerUUID);
        } else if (action instanceof BlockIgniteAction) {
            playNearbyBlockIgnite((BlockIgniteAction) action, ownerUUID);
        } else if (action instanceof HangingAction) {
            playNearbyHanging((HangingAction) action, ownerUUID);
        } else if (action instanceof SignAction) {
            playNearbySign((SignAction) action, ownerUUID);
        } else if (action instanceof BedAction) {
            playNearbyBed((BedAction) action, nearbyEntityId);
        } else if (action instanceof CraftAction) {
            playNearbyCraft((CraftAction) action);
        } else if (action instanceof GameModeAction) {
            playNearbyGameMode((GameModeAction) action);
        } else if (action instanceof BookEditAction) {
            playNearbyBookEdit((BookEditAction) action);
        } else if (action instanceof EnchantAction) {
            playNearbyEnchant((EnchantAction) action, ownerUUID);
        } else if (action instanceof EntityDyeAction) {
            playNearbyEntityDye((EntityDyeAction) action, ownerUUID);
        } else if (action instanceof PortalAction) {
            playNearbyPortal((PortalAction) action, nearbyEntityId, ownerUUID);
        } else if (action instanceof FarmingAction) {
            playNearbyFarming((FarmingAction) action, ownerUUID);
        } else if (action instanceof ExplosionAction) {
            playExplosion((ExplosionAction) action); // Ana oyuncuyla aynı handler
        } else if (action instanceof FallingBlockAction) {
            playFallingBlock((FallingBlockAction) action); // Ana oyuncuyla aynı handler
        } else if (action instanceof NoteBlockAction) {
            playNoteBlock((NoteBlockAction) action); // Ana oyuncuyla aynı handler
        } else {
            plugin.getLogger().fine("[REPLAY-DEBUG] Unsupported nearby player action: " +
                    action.getClass().getSimpleName());
        }
    }

    /**
     * Nearby player için location action oynatır
     * LocationAction ile gelen nearby player hareketlerini NPC'ye aktarır
     */
    private void playNearbyLocation(LocationAction action, int entityId, UUID ownerUUID) {
        Location newLoc = new Location(
                replayPlayer.getLastLocation() != null ? replayPlayer.getLastLocation().getWorld() : null,
                action.getX(), action.getY(), action.getZ(),
                action.getYaw(), action.getPitch()
        );

        // Konum takibini güncelle
        replayPlayer.getNpcManager().updateNearbyPlayerLocation(ownerUUID, newLoc);

        // NPC'yi teleport et
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(action.getX(), action.getY(), action.getZ()),
                action.getYaw(), action.getPitch(), action.isOnGround()
        );

        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                entityId, action.getYaw()
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLookPacket);
        }
    }

    /**
     * Nearby player için health action oynatır
     */
    private void playNearbyHealth(HealthAction action, int entityId) {
        replayPlayer.getNpcManager().sendNearbyPlayerHealth(
                entityId, action.getHealth(), action.getMaxHealth(), action.getAbsorption());
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
     * Vehicle spawn eder ve nearby player NPC'sini mount/dismount eder
     */
    private void playNearbyVehicle(VehicleAction action, int entityId, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location vehicleLoc = new Location(loc.getWorld(),
                    action.getVehicleX(), action.getVehicleY(), action.getVehicleZ());

            if (action.getActionType() == VehicleAction.ActionType.MOUNT) {
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
                        vehicle.setSilent(true);
                        spawnedEntities.add(vehicle);
                        hideFromNonViewers(vehicle);
                        int vehicleEntityId = vehicle.getEntityId();
                        mountedEntities.put(vehicleEntityId, vehicle);

                        // Nearby player NPC'sini vehicle'a mount et
                        WrapperPlayServerSetPassengers mountPacket = new WrapperPlayServerSetPassengers(
                                vehicleEntityId,
                                new int[]{entityId}
                        );
                        for (Player viewer : replayPlayer.getViewers()) {
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, mountPacket);
                        }

                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player mounted vehicle: " +
                                action.getVehicleType());
                    }
                } catch (Exception e) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Error mounting nearby player vehicle: " + e.getMessage());
                }
            } else if (action.getActionType() == VehicleAction.ActionType.DISMOUNT) {
                // Mount edilmiş vehicle'ı bul ve dismount et
                Iterator<Map.Entry<Integer, Entity>> it = mountedEntities.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Entity> entry = it.next();
                    Entity vehicle = entry.getValue();
                    if (vehicle != null && vehicle.isValid()) {
                        // Boş passengers ile dismount
                        WrapperPlayServerSetPassengers dismountPacket = new WrapperPlayServerSetPassengers(
                                entry.getKey(),
                                new int[]{}
                        );
                        for (Player viewer : replayPlayer.getViewers()) {
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, dismountPacket);
                        }

                        // Vehicle'ı 3 saniye sonra kaldır
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (vehicle.isValid()) {
                                vehicle.remove();
                            }
                        }, 60L);

                        it.remove();
                        break;
                    }
                }

                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player dismounted vehicle: " +
                        action.getVehicleType());
            }
        });
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
     * Nearby player için block action oynatır.
     * Ana oyuncu gibi sadece client-side (sendBlockChange) kullanır - gerçek dünyayı DEĞİŞTİRMEZ.
     */
    private void playNearbyBlock(BlockAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;
            if (replayPlayer.getViewers().isEmpty()) return;

            Location blockLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            String blockKey = action.getX() + "," + action.getY() + "," + action.getZ();

            switch (action.getActionType()) {
                case PLACE_BLOCK:
                    if (action.getBlockType() != null) {
                        if (!originalBlocks.containsKey(blockKey)) {
                            originalBlocks.put(blockKey, blockLoc.getBlock().getType());
                        }

                        try {
                            String[] parts = action.getBlockType().split(":");
                            Material material = Material.valueOf(parts[0]);
                            org.bukkit.block.data.BlockData blockData = material.createBlockData();

                            // Özel blok tipleri için property'leri uygula
                            if (parts.length > 1) {
                                if (blockData instanceof org.bukkit.block.data.type.Door && parts.length >= 4) {
                                    org.bukkit.block.data.type.Door door = (org.bukkit.block.data.type.Door) blockData;
                                    door.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                    door.setHalf(org.bukkit.block.data.Bisected.Half.valueOf(parts[2]));
                                    door.setHinge(org.bukkit.block.data.type.Door.Hinge.valueOf(parts[3]));
                                } else if (blockData instanceof org.bukkit.block.data.type.Stairs && parts.length >= 3) {
                                    org.bukkit.block.data.type.Stairs stairs = (org.bukkit.block.data.type.Stairs) blockData;
                                    stairs.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                    stairs.setHalf(org.bukkit.block.data.Bisected.Half.valueOf(parts[2]));
                                } else if (blockData instanceof org.bukkit.block.data.Directional && parts.length >= 2) {
                                    org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) blockData;
                                    directional.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1]));
                                }
                            }

                            for (Player viewer : replayPlayer.getViewers()) {
                                viewer.sendBlockChange(blockLoc, blockData);
                            }

                            if (!isPhysicsBlock(material)) {
                                playBlockPlaceSound(blockLoc, material);
                            }
                        } catch (Exception e) {
                            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby block place error: " + e.getMessage());
                        }
                    }
                    break;

                case STOP_BREAKING:
                    if (action.getStage() == 10) {
                        if (!originalBlocks.containsKey(blockKey)) {
                            originalBlocks.put(blockKey, blockLoc.getBlock().getType());
                        }

                        for (Player viewer : replayPlayer.getViewers()) {
                            viewer.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                        }
                        playBlockBreakEffects(blockLoc);
                    }
                    break;

                case INTERACT_BLOCK:
                    if (action.getBlockType() != null) {
                        String interactionType = action.getBlockType();
                        org.bukkit.block.Block block = blockLoc.getBlock();
                        org.bukkit.block.data.BlockData blockData = block.getBlockData();

                        if (interactionType.contains("DOOR")) {
                            if (blockData instanceof org.bukkit.block.data.Openable) {
                                org.bukkit.block.data.Openable openable =
                                    (org.bukkit.block.data.Openable) blockData.clone();
                                boolean shouldBeOpen = interactionType.equals("DOOR_OPEN");
                                openable.setOpen(shouldBeOpen);

                                for (Player viewer : replayPlayer.getViewers()) {
                                    viewer.sendBlockChange(blockLoc, openable);
                                }

                                Sound doorSound = shouldBeOpen ?
                                    Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
                                if (block.getType().name().contains("IRON")) {
                                    doorSound = shouldBeOpen ?
                                        Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
                                }
                                playSoundToViewers(blockLoc, doorSound, 1.0f, 1.0f);
                            }
                        } else if (interactionType.contains("TRAPDOOR")) {
                            if (blockData instanceof org.bukkit.block.data.Openable) {
                                org.bukkit.block.data.Openable openable =
                                    (org.bukkit.block.data.Openable) blockData.clone();
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
                                playSoundToViewers(blockLoc, trapdoorSound, 1.0f, 1.0f);
                            }
                        } else if (interactionType.contains("GATE")) {
                            if (blockData instanceof org.bukkit.block.data.Openable) {
                                org.bukkit.block.data.Openable openable =
                                    (org.bukkit.block.data.Openable) blockData.clone();
                                boolean shouldBeOpen = interactionType.equals("GATE_OPEN");
                                openable.setOpen(shouldBeOpen);

                                for (Player viewer : replayPlayer.getViewers()) {
                                    viewer.sendBlockChange(blockLoc, openable);
                                }

                                Sound gateSound = shouldBeOpen ?
                                    Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
                                playSoundToViewers(blockLoc, gateSound, 1.0f, 1.0f);
                            }
                        } else if (interactionType.contains("BUTTON")) {
                            playSoundToViewers(blockLoc, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.3f, 0.6f);
                            spawnParticleForViewers(Particle.CRIT,
                                blockLoc.clone().add(0.5, 0.5, 0.5), 3, 0.1, 0.1, 0.1, 0);
                        } else if (interactionType.contains("LEVER")) {
                            if (blockData instanceof org.bukkit.block.data.Powerable) {
                                org.bukkit.block.data.Powerable lever =
                                    (org.bukkit.block.data.Powerable) blockData.clone();
                                lever.setPowered(!lever.isPowered());

                                for (Player viewer : replayPlayer.getViewers()) {
                                    viewer.sendBlockChange(blockLoc, lever);
                                }

                                playSoundToViewers(blockLoc, Sound.BLOCK_LEVER_CLICK, 0.3f, 0.6f);
                            }
                        }
                    }
                    break;

                default:
                    break;
            }
        });
    }

    /**
     * Nearby player için item action oynatır
     */
    private void playNearbyItem(ItemAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location itemLoc = new Location(
                    loc.getWorld(),
                    action.getX(),
                    action.getY(),
                    action.getZ()
            );

            try {
                ItemStack itemStack = ItemSerializer.deserializeItemStack(action.getItemData());
                if (itemStack == null || itemStack.getType() == Material.AIR) return;
                itemStack.setAmount(action.getAmount());

                switch (action.getActionType()) {
                    case DROP:
                        // Civar NPC'nin göz seviyesinde, baktığı yönde hafif önde spawn et
                        double nYawRad = Math.toRadians(loc.getYaw());
                        Location nearbyDropSpawn = loc.clone().add(
                                -Math.sin(nYawRad) * 0.3,
                                1.3,
                                Math.cos(nYawRad) * 0.3
                        );

                        playSoundToViewers(nearbyDropSpawn, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);

                        Item droppedItem = nearbyDropSpawn.getWorld().dropItem(nearbyDropSpawn, itemStack);

                        // Velocity: yeni kayıtlarda gerçek velocity, eski kayıtlarda fallback
                        boolean isNearbyVelData = Math.abs(action.getX()) < 5 &&
                                                  Math.abs(action.getY()) < 5 &&
                                                  Math.abs(action.getZ()) < 5;

                        if (isNearbyVelData) {
                            droppedItem.setVelocity(new Vector(action.getX(), action.getY(), action.getZ()));
                        } else {
                            droppedItem.setVelocity(loc.getDirection().multiply(0.3));
                        }

                        droppedItem.setPickupDelay(Integer.MAX_VALUE);
                        droppedItem.setSilent(true);
                        spawnedEntities.add(droppedItem);
                        hideFromNonViewers(droppedItem);

                        // 5 saniye sonra kaldır
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (droppedItem.isValid()) {
                                droppedItem.remove();
                            }
                        }, 100L);

                        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby item drop: " +
                                itemStack.getType() + " | realVel=" + isNearbyVelData);
                        break;

                    case PICKUP:
                        playSoundToViewers(itemLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                        spawnParticleForViewers(Particle.ITEM,
                                itemLoc, 10, 0.2, 0.2, 0.2, 0.05, itemStack);
                        break;
                }
            } catch (Exception e) {
                ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to play nearby item: " + e.getMessage());
            }
        });
    }

    /**
     * Nearby player için potion effect oynatır
     * Particle efekti ve ses ile gösterir
     */
    private void playNearbyPotionEffect(PotionEffectAction action, int entityId, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Nearby NPC'nin konumunu kullan, ana NPC'nin değil
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            for (Player viewer : replayPlayer.getViewers()) {
                switch (action.getActionType()) {
                    case ADD:
                        spawnParticleForViewers(Particle.ENTITY_EFFECT,
                                loc.clone().add(0, 1, 0),
                                20, 0.5, 0.5, 0.5, 0,
                                org.bukkit.Color.fromRGB(255, 0, 255));
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
                        break;

                    case REMOVE:
                        break;

                    case CLEAR_ALL:
                        spawnParticleForViewers(Particle.CLOUD,
                                loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                        viewer.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.0f);
                        break;
                }
            }
        });

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player potion effect: " +
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

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player chat: " + action.getMessage());
    }

    /**
     * Nearby player için use item action oynatır
     * Eating/drinking animasyonunu entity metadata ile gönderir
     */
    private void playNearbyUseItem(UseItemAction action, int entityId, UUID ownerUUID) {
        List<EntityData<?>> metadata = new ArrayList<>();

        if (action.isStarted()) {
            // Using item flag (0x10) + hand active flag
            byte entityFlags = 0x10; // Hand active
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, entityFlags));
            metadata.add(new EntityData(8, EntityDataTypes.BYTE,
                    (byte)(action.isMainHand() ? 0x01 : 0x02)));
        } else {
            // Kullanım bitti
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));

            // Yeme tamamlandıysa ses efekti — nearby NPC'nin konumunda çal
            if (action.getUseType() == UseItemAction.UseType.FOOD_EAT) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
                    if (loc == null) loc = replayPlayer.getLastLocation();
                    if (loc != null && loc.getWorld() != null) {
                        playSoundToViewers(loc, Sound.ENTITY_PLAYER_BURP, 0.5f, 1.0f);
                    }
                });
            }
        }

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player use item: " +
                action.getUseType() + " started=" + action.isStarted());
    }

    /**
     * Nearby player için projectile action oynatır
     */
    private void playNearbyProjectile(ProjectileAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Nearby player NPC'sinin son konumunu bul
            Location npcLoc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (npcLoc == null) {
                npcLoc = replayPlayer.getLastLocation();
            }
            if (npcLoc == null || npcLoc.getWorld() == null) return;

            Location spawnLoc = npcLoc.clone().add(0, 1.5, 0);
            spawnLoc.setYaw(action.getYaw());
            spawnLoc.setPitch(action.getPitch());

            Vector velocity = new Vector(
                    action.getVelocityX(),
                    action.getVelocityY(),
                    action.getVelocityZ()
            );

            spawnProjectile(npcLoc.getWorld(), spawnLoc, velocity, action.getType(), action.getPotionData());
            playSoundToViewers(spawnLoc, getSoundForProjectile(action.getType()), 1.0f, 1.0f);

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player projectile: " + action.getType());
        });
    }

    /**
     * Nearby player için fishing action oynatır
     */
    private void playNearbyFishing(FishingAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location hookLoc = new Location(loc.getWorld(),
                    action.getHookX(), action.getHookY(), action.getHookZ());

            Integer nearbyEntityId = replayPlayer.getNpcManager().getNearbyPlayerEntityId(ownerUUID);

            switch (action.getState()) {
                case CAST:
                    // Önceki bobber varsa kaldır
                    despawnNearbyFishingBobber(ownerUUID);

                    playSoundToViewers(loc, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);

                    if (nearbyEntityId == null) break;

                    int bobberId = ReplayNPCManager.generateEntityId();
                    nearbyFishingBobbers.put(ownerUUID, bobberId);

                    // Civar NPC'nin göz seviyesinde, baktığı yönde hafif önde spawn et
                    double yawRad = Math.toRadians(loc.getYaw());

                    double spawnX = loc.getX() - Math.sin(yawRad) * 0.3;
                    double spawnY = loc.getY() + 1.62;
                    double spawnZ = loc.getZ() + Math.cos(yawRad) * 0.3;

                    // Velocity: yeni kayıtlarda gerçek velocity, eski kayıtlarda fallback
                    double velX, velY, velZ;
                    boolean isVelData = Math.abs(action.getHookX()) < 5 &&
                                        Math.abs(action.getHookY()) < 5 &&
                                        Math.abs(action.getHookZ()) < 5;

                    if (isVelData) {
                        velX = action.getHookX();
                        velY = action.getHookY();
                        velZ = action.getHookZ();
                    } else {
                        double pitchRad = Math.toRadians(loc.getPitch());
                        double throwPower = 1.5;
                        velX = -Math.sin(yawRad) * Math.cos(pitchRad) * throwPower;
                        velY = -Math.sin(pitchRad) * throwPower + 0.2;
                        velZ = Math.cos(yawRad) * Math.cos(pitchRad) * throwPower;
                    }

                    WrapperPlayServerSpawnEntity spawnBobber = new WrapperPlayServerSpawnEntity(
                            bobberId,
                            Optional.of(UUID.randomUUID()),
                            EntityTypes.FISHING_BOBBER,
                            new Vector3d(spawnX, spawnY, spawnZ),
                            (float) loc.getPitch(),
                            (float) loc.getYaw(),
                            0f,
                            nearbyEntityId, // data = civar NPC entity ID
                            Optional.of(new Vector3d(velX, velY, velZ))
                    );

                    for (Player viewer : replayPlayer.getViewers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnBobber);
                    }

                    // Velocity paketi
                    WrapperPlayServerEntityVelocity velPacket = new WrapperPlayServerEntityVelocity(
                            bobberId, new Vector3d(velX, velY, velZ));
                    for (Player viewer : replayPlayer.getViewers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, velPacket);
                    }

                    ReportSystemSpigot.getInstance().debug("[REPLAY-FISHING] Nearby bobber | real=" + isVelData +
                            " | vel=" + String.format("%.3f, %.3f, %.3f", velX, velY, velZ) +
                            " | bobberId=" + bobberId + " | owner=" + nearbyEntityId);
                    break;

                case CAUGHT:
                    despawnNearbyFishingBobber(ownerUUID);
                    playSoundToViewers(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                    spawnParticleForViewers(Particle.SPLASH, hookLoc, 20, 0.3, 0.1, 0.3, 0.05);
                    break;

                case REEL_IN:
                    despawnNearbyFishingBobber(ownerUUID);
                    playSoundToViewers(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                    break;

                case FAILED:
                    despawnNearbyFishingBobber(ownerUUID);
                    break;
            }
        });
    }

    /**
     * Civar oyuncunun fishing bobber'ını kaldırır
     */
    private void despawnNearbyFishingBobber(UUID ownerUUID) {
        Integer bobberId = nearbyFishingBobbers.remove(ownerUUID);
        if (bobberId != null) {
            WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(bobberId);
            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroy);
            }
        }
    }

    /**
     * Nearby player için fire action oynatır
     */
    private void playNearbyFire(FireAction action, int entityId) {
        byte entityFlags = 0;
        if (action.isOnFire()) {
            entityFlags |= 0x01; // On fire flag
        }

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, entityFlags));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player fire: " + action.isOnFire());
    }

    /**
     * Nearby player için teleport action oynatır
     */
    private void playNearbyTeleport(TeleportAction action, int nearbyEntityId, UUID ownerUUID) {
        Location newLoc = new Location(
                replayPlayer.getLastLocation().getWorld(),
                action.getToX(), action.getToY(), action.getToZ()
        );

        // NPC'yi yeni konuma teleport et
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                nearbyEntityId,
                new Vector3d(newLoc.getX(), newLoc.getY(), newLoc.getZ()),
                0, 0, false
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
        }

        // Particle efekti
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (newLoc.getWorld() != null) {
                spawnParticleForViewers(Particle.PORTAL, newLoc.clone().add(0, 1, 0),
                        30, 0.3, 0.5, 0.3, 0.1);
                playSoundToViewers(newLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
        });

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player teleport to: " +
                action.getToX() + "," + action.getToY() + "," + action.getToZ());
    }

    /**
     * Nearby player için bucket action oynatır
     */
    private void playNearbyBucket(BucketAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;
            if (replayPlayer.getViewers().isEmpty()) return;

            Location blockLoc = new Location(
                    loc.getWorld(),
                    action.getBlockX(), action.getBlockY(), action.getBlockZ()
            );

            String blockKey = action.getBlockX() + "," + action.getBlockY() + "," + action.getBlockZ();

            if (!originalBlocks.containsKey(blockKey)) {
                originalBlocks.put(blockKey, blockLoc.getBlock().getType());
            }

            if (action.getActionType() == BucketAction.ActionType.EMPTY) {
                // Kova boşaltma - su/lav bloğu koy
                Material fluidMaterial;
                Sound bucketSound;
                switch (action.getBucketType()) {
                    case LAVA:
                        fluidMaterial = Material.LAVA;
                        bucketSound = Sound.ITEM_BUCKET_EMPTY_LAVA;
                        break;
                    case POWDER_SNOW:
                        fluidMaterial = Material.POWDER_SNOW;
                        bucketSound = Sound.ITEM_BUCKET_EMPTY_POWDER_SNOW;
                        break;
                    default:
                        fluidMaterial = Material.WATER;
                        bucketSound = Sound.ITEM_BUCKET_EMPTY;
                        break;
                }

                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.sendBlockChange(blockLoc, fluidMaterial.createBlockData());
                }
                playSoundToViewers(blockLoc, bucketSound, 1.0f, 1.0f);
            } else {
                // Kova doldurma - bloğu kaldır
                for (Player viewer : replayPlayer.getViewers()) {
                    viewer.sendBlockChange(blockLoc, Material.AIR.createBlockData());
                }

                Sound fillSound = action.getBucketType() == BucketAction.BucketType.LAVA ?
                        Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL;
                playSoundToViewers(blockLoc, fillSound, 1.0f, 1.0f);
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player bucket: " +
                    action.getActionType() + " " + action.getBucketType());
        });
    }

    /**
     * Nearby player için container action oynatır
     */
    private void playNearbyContainer(ContainerAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location containerLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            Sound sound = action.isOpened() ?
                    Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_CHEST_CLOSE;

            if (action.getContainerType() == ContainerAction.ContainerType.ENDER_CHEST) {
                sound = action.isOpened() ?
                        Sound.BLOCK_ENDER_CHEST_OPEN : Sound.BLOCK_ENDER_CHEST_CLOSE;
            } else if (action.getContainerType() == ContainerAction.ContainerType.BARREL) {
                sound = action.isOpened() ?
                        Sound.BLOCK_BARREL_OPEN : Sound.BLOCK_BARREL_CLOSE;
            } else if (action.getContainerType() == ContainerAction.ContainerType.SHULKER_BOX) {
                sound = action.isOpened() ?
                        Sound.BLOCK_SHULKER_BOX_OPEN : Sound.BLOCK_SHULKER_BOX_CLOSE;
            }

            playSoundToViewers(containerLoc, sound, 1.0f, 1.0f);

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player container: " +
                    action.getContainerType() + " " + (action.isOpened() ? "OPEN" : "CLOSE"));
        });
    }

    /**
     * Nearby player için damage action oynatır (hasar animasyonu)
     */
    private void playNearbyDamage(DamageAction action, int entityId, UUID ownerUUID) {
        // Hurt animasyonu gönder
        WrapperPlayServerEntityAnimation animPacket = new WrapperPlayServerEntityAnimation(
                entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, animPacket);
        }

        // Hasar sesi
        if (action.shouldPlaySound()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
                if (loc == null) loc = replayPlayer.getLastLocation();
                if (loc != null && loc.getWorld() != null) {
                    Sound damageSound;
                    switch (action.getDamageType()) {
                        case FALL: damageSound = Sound.ENTITY_PLAYER_HURT; break;
                        case FIRE: damageSound = Sound.ENTITY_PLAYER_HURT_ON_FIRE; break;
                        case DROWNING: damageSound = Sound.ENTITY_PLAYER_HURT_DROWN; break;
                        default: damageSound = Sound.ENTITY_PLAYER_HURT; break;
                    }
                    playSoundToViewers(loc, damageSound, 1.0f, 1.0f);
                }
            });
        }
    }

    /**
     * Nearby player için entity interaction oynatır
     */
    private void playNearbyInteractEntity(InteractEntityAction action, int entityId) {
        // Swing animasyonu
        WrapperPlayServerEntityAnimation animPacket = new WrapperPlayServerEntityAnimation(
                entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, animPacket);
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player interact entity: " +
                action.getType() + " -> " + action.getTargetEntityType());
    }

    /**
     * Nearby player için breed action oynatır
     */
    private void playNearbyBreed(BreedAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location breedLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            spawnParticleForViewers(Particle.HEART, breedLoc.clone().add(0, 1, 0),
                    10, 0.5, 0.3, 0.5, 0);
            playSoundToViewers(breedLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player breed: " + action.getBabyEntityType());
        });
    }

    /**
     * Nearby player için block ignite action oynatır
     */
    private void playNearbyBlockIgnite(BlockIgniteAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location igniteLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            spawnParticleForViewers(Particle.FLAME, igniteLoc, 20, 0.5, 0.5, 0.5, 0.02);
            playSoundToViewers(igniteLoc, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);

            // Ateş bloğunu göster
            String blockKey = action.getX() + "," + action.getY() + "," + action.getZ();
            if (!originalBlocks.containsKey(blockKey)) {
                originalBlocks.put(blockKey, igniteLoc.getBlock().getType());
            }
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendBlockChange(igniteLoc, Material.FIRE.createBlockData());
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player block ignite: " + action.getIgniteType());
        });
    }

    /**
     * Nearby player için hanging action oynatır (resim, item frame)
     */
    private void playNearbyHanging(HangingAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location npcLoc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (npcLoc == null) npcLoc = replayPlayer.getLastLocation();
            if (npcLoc == null || npcLoc.getWorld() == null) return;

            Location loc = new Location(
                    npcLoc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            if (action.getActionType() == HangingAction.ActionType.PLACE) {
                try {
                    org.bukkit.block.BlockFace facing = org.bukkit.block.BlockFace.valueOf(action.getFacing());

                    Hanging hanging = null;
                    switch (action.getHangingType()) {
                        case PAINTING:
                            hanging = loc.getWorld().spawn(loc, Painting.class, painting -> {
                                painting.setFacingDirection(facing);
                                if (action.getPaintingArt() != null) {
                                    try {
                                        org.bukkit.Art art = org.bukkit.Art.valueOf(action.getPaintingArt());
                                        painting.setArt(art);
                                    } catch (Exception e) {
                                        // Ignore
                                    }
                                }
                            });
                            break;
                        case ITEM_FRAME:
                            hanging = loc.getWorld().spawn(loc, ItemFrame.class, frame -> {
                                frame.setFacingDirection(facing);
                            });
                            break;
                        case GLOW_ITEM_FRAME:
                            hanging = loc.getWorld().spawn(loc, GlowItemFrame.class, frame -> {
                                frame.setFacingDirection(facing);
                            });
                            break;
                    }

                    if (hanging != null) {
                        spawnedEntities.add(hanging);
                        hideFromNonViewers(hanging);
                    }
                } catch (Exception e) {
                    ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby hanging place error: " + e.getMessage());
                }
            } else {
                // BREAK - yakındaki hanging'i bul ve kaldır
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
                    if (entity instanceof Hanging) {
                        entity.remove();
                        break;
                    }
                }
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player hanging: " +
                    action.getActionType() + " " + action.getHangingType());
        });
    }

    /**
     * Nearby player için bed action oynatır
     */
    private void playNearbyBed(BedAction action, int entityId) {
        if (action.getActionType() == BedAction.ActionType.ENTER_BED) {
            // Uyuma pozu
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.SLEEPING));

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                    entityId, metadata);

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            }
        } else {
            // Kalktı - normal poza dön
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.STANDING));

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                    entityId, metadata);

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            }
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player bed: " + action.getActionType());
    }

    /**
     * Nearby player için craft action oynatır
     */
    private void playNearbyCraft(CraftAction action) {
        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendMessage("§7[Craft] §f" + action.getCraftedItem() + " x" + action.getAmount());
        }
    }

    /**
     * Nearby player için game mode action oynatır
     */
    private void playNearbyGameMode(GameModeAction action) {
        String modeName;
        switch (action.getGameMode()) {
            case CREATIVE: modeName = "§dCreative"; break;
            case ADVENTURE: modeName = "§eAdventure"; break;
            case SPECTATOR: modeName = "§7Spectator"; break;
            default: modeName = "§aSurvival"; break;
        }

        for (Player viewer : replayPlayer.getViewers()) {
            viewer.sendMessage("§7[GameMode] §f" + modeName);
        }
    }

    /**
     * Nearby player için book edit action oynatır
     */
    private void playNearbyBookEdit(BookEditAction action) {
        if (action.isSigned()) {
            for (Player viewer : replayPlayer.getViewers()) {
                viewer.sendMessage("§7[Book] §f" + action.getAuthor() + " signed: " + action.getTitle());
            }
        }
    }

    /**
     * Nearby player için enchant action oynatır
     */
    private void playNearbyEnchant(EnchantAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc != null && loc.getWorld() != null) {
                spawnParticleForViewers(Particle.ENCHANT,
                        loc.clone().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 1.0);
                playSoundToViewers(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            }
        });
    }

    /**
     * Nearby player için entity dye action oynatır
     */
    private void playNearbyEntityDye(EntityDyeAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location dyeLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            spawnParticleForViewers(Particle.ITEM,
                    dyeLoc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            playSoundToViewers(dyeLoc, Sound.ITEM_DYE_USE, 1.0f, 1.0f);
        });
    }

    /**
     * Nearby player için portal action oynatır
     */
    private void playNearbyPortal(PortalAction action, int entityId, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            spawnParticleForViewers(Particle.PORTAL, loc.clone().add(0, 1, 0),
                    50, 0.5, 1.0, 0.5, 0.5);
            playSoundToViewers(loc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.0f);
        });
    }

    /**
     * Nearby player için farming action oynatır
     */
    private void playNearbyFarming(FarmingAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location farmLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            switch (action.getFarmingType()) {
                case BONE_MEAL:
                    spawnParticleForViewers(Particle.HAPPY_VILLAGER,
                            farmLoc.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0);
                    playSoundToViewers(farmLoc, Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
                    break;
                case CROP_HARVEST:
                    playSoundToViewers(farmLoc, Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);
                    break;
                case HOE_TILL:
                    playSoundToViewers(farmLoc, Sound.ITEM_HOE_TILL, 1.0f, 1.0f);
                    break;
                case FARMLAND_TRAMPLE:
                    playSoundToViewers(farmLoc, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.8f);
                    break;
            }
        });
    }

    /**
     * Nearby player için sign action oynatır
     */
    private void playNearbySign(SignAction action, UUID ownerUUID) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = replayPlayer.getNpcManager().getNearbyPlayerLocation(ownerUUID);
            if (loc == null) loc = replayPlayer.getLastLocation();
            if (loc == null || loc.getWorld() == null) return;

            Location signLoc = new Location(
                    loc.getWorld(),
                    action.getX(), action.getY(), action.getZ()
            );

            // Sign text'ini viewer'lara göster
            for (Player viewer : replayPlayer.getViewers()) {
                String[] lines = action.getLines();
                if (lines != null && lines.length > 0) {
                    StringBuilder signText = new StringBuilder("§7[Sign] ");
                    for (String line : lines) {
                        if (line != null && !line.isEmpty()) {
                            signText.append(line).append(" | ");
                        }
                    }
                    viewer.sendMessage(signText.toString());
                }
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Nearby player sign change");
        });
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
            org.bukkit.inventory.ItemStack bukkitItem =
                    com.reportsystem.spigot.utils.ItemSerializer.itemStackFromBytes(itemData.getItemStackData());

            if (bukkitItem == null || bukkitItem.getType() == Material.AIR) {
                return null;
            }

            return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
        } catch (Exception e) {
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Failed to convert ItemData: " + e.getMessage());
            return null;
        }
    }

    /**
     * Entity flags'i sıfırlar (seek/rewind sonrası ateş, sneaking vb. kaldırılır)
     */
    public void resetEntityFlags() {
        currentEntityFlags = 0;
    }

    /**
     * Sadece replay viewer'lara ses caldirir (yakin da ki diger oyuncular duymaz)
     */
    private void playSoundToViewers(Location location, Sound sound, float volume, float pitch) {
        for (Player viewer : replayPlayer.getViewers()) {
            viewer.playSound(location, sound, volume, pitch);
        }
    }

    /**
     * Sadece replay viewer'lara partikul gosterir
     */
    private void spawnParticleForViewers(Particle particle, Location location, int count) {
        for (Player viewer : replayPlayer.getViewers()) {
            viewer.spawnParticle(particle, location, count);
        }
    }

    private void spawnParticleForViewers(Particle particle, Location location, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra) {
        for (Player viewer : replayPlayer.getViewers()) {
            viewer.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    private <T> void spawnParticleForViewers(Particle particle, Location location, int count,
                                              double offsetX, double offsetY, double offsetZ, double extra, T data) {
        for (Player viewer : replayPlayer.getViewers()) {
            viewer.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
        }
    }

    /**
     * Spawn edilen entity'yi sadece replay viewer'lara gosterir, diger oyunculardan gizler
     */
    private void hideFromNonViewers(Entity entity) {
        Set<Player> viewers = new HashSet<>(replayPlayer.getViewers());
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!viewers.contains(online)) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    // Getter'lar
    public byte getCurrentEntityFlags() { return currentEntityFlags; }
    public Map<Integer, Entity> getMountedEntities() { return mountedEntities; }
}