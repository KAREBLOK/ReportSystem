package com.reportsystem.spigot.replay;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.common.replay.actions.PlayerInfoAction;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayNPCManager {

    // Global entity ID counter - tüm ReplayNPCManager instance'ları için paylaşımlı
    private static final AtomicInteger GLOBAL_ENTITY_ID_COUNTER = new AtomicInteger(1000000);
    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random RANDOM = new Random();

    private final JavaPlugin plugin;
    private final ReplayPlayer replayPlayer;

    private final int entityId;
    private final UUID npcUuid;
    private final String npcRandomName; // Random name to avoid conflicts

    private String skinTexture = "";
    private String skinSignature = "";

    // Yakındaki oyuncular için
    private final Map<UUID, Integer> nearbyPlayerEntities = new HashMap<>();

    public ReplayNPCManager(JavaPlugin plugin, ReplayPlayer replayPlayer) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
        // Global counter'dan benzersiz entity ID al
        this.entityId = GLOBAL_ENTITY_ID_COUNTER.getAndIncrement();
        this.npcUuid = UUID.randomUUID();
        this.npcRandomName = generateRandomName(8);
        plugin.getLogger().info("[REPLAY-NPC] Created NPC Manager with unique entity ID: " + this.entityId +
                " and random name: " + this.npcRandomName);
    }

    /**
     * Generates a random string for NPC name to avoid conflicts
     */
    private static String generateRandomName(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * NPC'yi spawn eder
     */
    public void spawnNPC(Location location) {
        // Ana NPC için PREFIX + KALIN KIRMIZI display name - report edilen oyuncu
        String recordedPlayerName = replayPlayer.getReplay().getRecordedPlayer();

        // MessageManager'dan dil destekli prefix al
        com.reportsystem.spigot.ReportSystemSpigot spigotPlugin =
                (com.reportsystem.spigot.ReportSystemSpigot) plugin;
        String prefix = spigotPlugin.getMessageManager().getMessage("replay.nametag-recorded-player");

        // Prefix + Kalın Kırmızı isim
        String displayName = prefix + "§c§l" + recordedPlayerName;

        // Player Info Update paketi - random name to avoid conflicts
        UserProfile profile = new UserProfile(npcUuid, npcRandomName);

        // Skin bilgisi varsa ekle
        if (skinTexture != null && !skinTexture.isEmpty()) {
            profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", skinTexture, skinSignature)));
        } else {
            profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", "", null)));
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile,
                        true, // Listed
                        20, // Ping
                        GameMode.SURVIVAL,
                        Component.text(displayName),
                        null // Chat session
                );

        WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                playerInfo
        );

        // Spawn Entity paketi
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(npcUuid),
                EntityTypes.PLAYER,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getPitch(),
                location.getYaw(),
                location.getYaw(), // Head yaw
                0, // Data
                Optional.of(new Vector3d(0, 0, 0)) // Velocity
        );

        // Entity Metadata (skin layers vs.)
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0)); // Entity flags
        metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0xFF)); // Skin parts

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                metadata
        );

        // Paketleri gönder
        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, infoPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }
    }

    /**
     * NPC'yi teleport eder
     */
    public void teleportNPC(Location location, Map<Integer, Entity> mountedEntities) {
        // Mount durumunu kontrol et
        boolean isMounted = !mountedEntities.isEmpty();

        if (isMounted) {
            // Eğer bir araca binmişse, aracı hareket ettir
            for (Map.Entry<Integer, Entity> entry : mountedEntities.entrySet()) {
                Entity vehicle = entry.getValue();
                if (vehicle != null && vehicle.isValid()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        vehicle.teleport(location);
                    });
                }
            }
        } else {
            // Normal hareket
            Location lastLocation = replayPlayer.getLastLocation();
            if (lastLocation != null && lastLocation.distance(location) < 8) {
                double deltaX = location.getX() - lastLocation.getX();
                double deltaY = location.getY() - lastLocation.getY();
                double deltaZ = location.getZ() - lastLocation.getZ();

                // Relative move packet
                WrapperPlayServerEntityRelativeMoveAndRotation movePacket =
                        new WrapperPlayServerEntityRelativeMoveAndRotation(
                                entityId,
                                deltaX,
                                deltaY,
                                deltaZ,
                                location.getYaw(),
                                location.getPitch(),
                                true // on ground
                        );

                for (Player viewer : replayPlayer.getViewers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, movePacket);
                }
            } else {
                // Uzak mesafe için teleport kullan
                WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                        entityId,
                        new Vector3d(location.getX(), location.getY(), location.getZ()),
                        location.getYaw(),
                        location.getPitch(),
                        false
                );

                for (Player viewer : replayPlayer.getViewers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
                }
            }

            // Head rotation her zaman gönder
            WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                    entityId,
                    location.getYaw()
            );

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLookPacket);
            }
        }
    }

    /**
     * NPC'yi despawn eder
     */
    public void despawnNPC() {
        // Destroy entity
        WrapperPlayServerDestroyEntities destroyPacket =
                new WrapperPlayServerDestroyEntities(entityId);

        // Remove from player list
        WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(
                Arrays.asList(npcUuid)
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
        }
    }

    /**
     * Tüm NPC'leri kaldırır - GÜNCELLENEN METOD
     */
    public void despawnAll() {
        plugin.getLogger().info("[REPLAY-NPC] Despawning all NPCs - Main NPC and " + nearbyPlayerEntities.size() + " nearby players");

        // Ana NPC'yi kaldır
        despawnNPC();

        // Yakındaki oyuncuları da kaldır
        for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
            WrapperPlayServerDestroyEntities destroyNearby =
                    new WrapperPlayServerDestroyEntities(entry.getValue());

            WrapperPlayServerPlayerInfoRemove removeNearby =
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(entry.getKey()));

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyNearby);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeNearby);
            }
        }
        nearbyPlayerEntities.clear();

        // YENİ: Viewer'ları da force update et
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : replayPlayer.getViewers()) {
                if (viewer != null && viewer.isOnline()) {
                    // Player listesini force refresh
                    viewer.updateCommands();
                    plugin.getLogger().info("[REPLAY-NPC] Force updated player: " + viewer.getName());
                }
            }
            plugin.getLogger().info("[REPLAY-NPC] All NPCs despawned and viewers updated");
        }, 5L);
    }

    /**
     * Animasyon gönderir
     */
    public void sendAnimation(WrapperPlayServerEntityAnimation.EntityAnimationType animationType) {
        WrapperPlayServerEntityAnimation animationPacket =
                new WrapperPlayServerEntityAnimation(entityId, animationType);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, animationPacket);
        }
    }

    /**
     * Entity metadata gönderir
     */
    public void sendMetadata(List<EntityData<?>> metadata) {
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                metadata
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }
    }

    /**
     * Entity flag'lerini günceller
     */
    public void updateEntityFlags(byte flags) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, flags));
        metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0xFF)); // Skin parts

        sendMetadata(metadata);
    }

    /**
     * Equipment gönderir
     */
    public void sendEquipment(List<Equipment> equipment) {
        try {
            WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(
                    entityId,
                    equipment
            );

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, equipmentPacket);
            }

            plugin.getLogger().info("[REPLAY-DEBUG] Sent equipment packet");
        } catch (Exception e) {
            plugin.getLogger().severe("[REPLAY-DEBUG] Error sending equipment packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Velocity gönderir
     */
    public void sendVelocity(double x, double y, double z) {
        WrapperPlayServerEntityVelocity velocityPacket = new WrapperPlayServerEntityVelocity(
                entityId,
                new Vector3d(x, y, z)
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, velocityPacket);
        }
    }

    /**
     * Entity status gönderir
     */
    public void sendEntityStatus(byte status) {
        WrapperPlayServerEntityStatus statusPacket = new WrapperPlayServerEntityStatus(
                entityId,
                status
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, statusPacket);
        }
    }

    /**
     * Mount paketi gönderir
     */
    public void sendMountPacket(int vehicleEntityId) {
        WrapperPlayServerSetPassengers mountPacket = new WrapperPlayServerSetPassengers(
                vehicleEntityId,
                new int[]{entityId}
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, mountPacket);
        }
    }

    /**
     * Blok kırma animasyonu gönderir
     */
    public void sendBlockBreakAnimation(int x, int y, int z, int stage) {
        WrapperPlayServerBlockBreakAnimation breakAnim = new WrapperPlayServerBlockBreakAnimation(
                entityId,
                new Vector3i(x, y, z),
                (byte) stage
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, breakAnim);
        }
    }

    /**
     * Player info günceller
     */
    public void updatePlayerInfo(PlayerInfoAction action) {
        this.skinTexture = action.getSkinTexture();
        this.skinSignature = action.getSkinSignature();
    }

    /**
     * Yakındaki oyuncu aksiyonlarını oynatır
     */
    public void playNearbyPlayerAction(NearbyPlayerAction action) {
        switch (action.getActionType()) {
            case PLAYER_APPEAR:
                spawnNearbyPlayer(action);
                break;
            case PLAYER_MOVE:
                moveNearbyPlayer(action);
                break;
            case PLAYER_DISAPPEAR:
                despawnNearbyPlayer(action);
                break;
        }
    }

    /**
     * Yakındaki oyuncuyu spawn eder
     */
    private void spawnNearbyPlayer(NearbyPlayerAction action) {
        // ÖNCE: Eğer bu UUID zaten varsa, eski entity'yi despawn et (duplicate spawn önleme)
        Integer oldEntityId = nearbyPlayerEntities.get(action.getPlayerUuid());
        if (oldEntityId != null) {
            plugin.getLogger().warning("[REPLAY-DEBUG] Duplicate spawn detected for " + action.getPlayerName() +
                    " | Old EntityID: " + oldEntityId + " will be removed");

            // Eski entity'yi despawn et
            WrapperPlayServerDestroyEntities destroyOld = new WrapperPlayServerDestroyEntities(oldEntityId);
            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyOld);
            }
        }

        // Global counter'dan benzersiz entity ID al
        int nearbyEntityId = GLOBAL_ENTITY_ID_COUNTER.getAndIncrement();
        nearbyPlayerEntities.put(action.getPlayerUuid(), nearbyEntityId);

        // Random isim oluştur - conflict önlemek için
        String randomName = generateRandomName(8);
        String realPlayerName = action.getPlayerName();

        plugin.getLogger().info("[REPLAY-DEBUG] Spawning nearby player: " + realPlayerName +
                " with unique entity ID: " + nearbyEntityId +
                " and random name: " + randomName +
                " at " + action.getX() + ", " + action.getY() + ", " + action.getZ());

        // Player Info paketi - random name to avoid conflicts
        UserProfile nearbyProfile = new UserProfile(action.getPlayerUuid(), randomName);
        if (action.getSkinTexture() != null && !action.getSkinTexture().isEmpty()) {
            nearbyProfile.setTextureProperties(Arrays.asList(
                    new TextureProperty("textures", action.getSkinTexture(), action.getSkinSignature())
            ));
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo nearbyInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        nearbyProfile,
                        true,
                        20,
                        GameMode.SURVIVAL,
                        Component.text("§7" + realPlayerName), // Gray color for nearby players
                        null
                );

        WrapperPlayServerPlayerInfoUpdate nearbyInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                nearbyInfo
        );

        // Entity spawn paketi
        WrapperPlayServerSpawnEntity nearbySpawnPacket = new WrapperPlayServerSpawnEntity(
                nearbyEntityId,
                Optional.of(action.getPlayerUuid()),
                EntityTypes.PLAYER,
                new Vector3d(action.getX(), action.getY(), action.getZ()),
                action.getPitch(),
                action.getYaw(),
                action.getYaw(),
                0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        // Metadata - görünür ve tüm skin parçaları aktif
        List<EntityData<?>> nearbyMetadata = new ArrayList<>();
        nearbyMetadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0)); // Entity flags - görünür
        nearbyMetadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0xFF)); // Tüm skin parçaları

        WrapperPlayServerEntityMetadata nearbyMetadataPacket = new WrapperPlayServerEntityMetadata(
                nearbyEntityId,
                nearbyMetadata
        );

        // Paketleri sırayla gönder - önce info, sonra spawn, sonra metadata, sonra equipment
        for (Player viewer : replayPlayer.getViewers()) {
            // Önce player info
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyInfoPacket);

            // Biraz bekle ve spawn et
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbySpawnPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyMetadataPacket);

                plugin.getLogger().info("[REPLAY-DEBUG] Nearby player spawned for viewer: " + viewer.getName());

                // Equipment paketini gönder
                if (action.getEquipment() != null && !action.getEquipment().isEmpty()) {
                    sendNearbyPlayerEquipment(viewer, nearbyEntityId, action.getEquipment());
                }
            }, 2L);
        }
    }

    /**
     * Nearby player'a equipment gönderir
     */
    public void sendNearbyPlayerEquipment(Player viewer, int entityId,
                                           Map<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> equipment) {
        List<Equipment> equipmentList = new ArrayList<>();

        for (Map.Entry<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> entry : equipment.entrySet()) {
            EquipmentSlot slot = convertToPacketSlot(entry.getKey());
            if (slot != null && entry.getValue() != null) {
                // ItemData'yı PacketEvents ItemStack'e çevir
                com.github.retrooper.packetevents.protocol.item.ItemStack itemStack =
                    replayPlayer.getActionPlayer().convertItemData(entry.getValue());

                if (itemStack != null) {
                    equipmentList.add(new Equipment(slot, itemStack));
                }
            }
        }

        if (!equipmentList.isEmpty()) {
            WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(
                entityId,
                equipmentList
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, equipmentPacket);

            plugin.getLogger().info("[REPLAY-DEBUG] Sent " + equipmentList.size() +
                    " equipment items for nearby player entity " + entityId);
        }
    }

    /**
     * EquipmentAction.EquipmentSlot'u PacketEvents EquipmentSlot'a çevirir
     */
    private EquipmentSlot convertToPacketSlot(EquipmentAction.EquipmentSlot slot) {
        switch (slot) {
            case MAIN_HAND:
                return EquipmentSlot.MAIN_HAND;
            case OFF_HAND:
                return EquipmentSlot.OFF_HAND;
            case HELMET:
                return EquipmentSlot.HELMET;
            case CHESTPLATE:
                return EquipmentSlot.CHEST_PLATE;
            case LEGGINGS:
                return EquipmentSlot.LEGGINGS;
            case BOOTS:
                return EquipmentSlot.BOOTS;
            default:
                return null;
        }
    }

    /**
     * Yakındaki oyuncuyu hareket ettirir
     */
    private void moveNearbyPlayer(NearbyPlayerAction action) {
        Integer moveEntityId = nearbyPlayerEntities.get(action.getPlayerUuid());

        // CRITICAL DEBUG
        plugin.getLogger().warning("[NEARBY-MOVE-DEBUG] Attempting to move nearby player UUID: " + action.getPlayerUuid() +
                " | Entity ID: " + moveEntityId +
                " | Location: " + String.format("%.1f, %.1f, %.1f", action.getX(), action.getY(), action.getZ()) +
                " | Total nearby entities: " + nearbyPlayerEntities.size());

        if (moveEntityId != null) {
            WrapperPlayServerEntityTeleport nearbyTeleport = new WrapperPlayServerEntityTeleport(
                    moveEntityId,
                    new Vector3d(action.getX(), action.getY(), action.getZ()),
                    action.getYaw(),
                    action.getPitch(),
                    false
            );

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyTeleport);
                plugin.getLogger().warning("[NEARBY-MOVE-DEBUG] Sent teleport packet to viewer: " + viewer.getName());
            }
        } else {
            plugin.getLogger().severe("[NEARBY-MOVE-ERROR] Entity ID NOT FOUND for UUID: " + action.getPlayerUuid() +
                    " | Available UUIDs: " + nearbyPlayerEntities.keySet());
        }
    }

    /**
     * Yakındaki oyuncuyu kaldırır
     */
    private void despawnNearbyPlayer(NearbyPlayerAction action) {
        Integer removeEntityId = nearbyPlayerEntities.remove(action.getPlayerUuid());
        if (removeEntityId != null) {
            WrapperPlayServerDestroyEntities nearbyDestroy =
                    new WrapperPlayServerDestroyEntities(removeEntityId);

            WrapperPlayServerPlayerInfoRemove nearbyRemove =
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(action.getPlayerUuid()));

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyDestroy);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyRemove);
            }
        }
    }

    /**
     * Yeni viewer'a NPC'yi gösterir
     */
    public void showNPCToViewer(Player viewer) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Ana NPC için PREFIX + KALIN KIRMIZI display name
            String recordedPlayerName = replayPlayer.getReplay().getRecordedPlayer();

            // MessageManager'dan dil destekli prefix al
            com.reportsystem.spigot.ReportSystemSpigot spigotPlugin =
                    (com.reportsystem.spigot.ReportSystemSpigot) plugin;
            String prefix = spigotPlugin.getMessageManager().getMessage("replay.nametag-recorded-player");

            // Prefix + Kalın Kırmızı isim
            String displayName = prefix + "§c§l" + recordedPlayerName;

            // Player Info paketi - random name to avoid conflicts
            UserProfile profile = new UserProfile(npcUuid, npcRandomName);
            if (skinTexture != null && !skinTexture.isEmpty()) {
                profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", skinTexture, skinSignature)));
            }

            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            profile,
                            true,
                            20,
                            GameMode.SURVIVAL,
                            Component.text(displayName),
                            null
                    );

            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                    playerInfo
            );

            // Entity spawn paketi
            Location loc = replayPlayer.getLastLocation();
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    entityId,
                    Optional.of(npcUuid),
                    EntityTypes.PLAYER,
                    new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                    loc.getPitch(),
                    loc.getYaw(),
                    loc.getYaw(),
                    0,
                    Optional.of(new Vector3d(0, 0, 0))
            );

            // Metadata
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
            metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0xFF));

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                    entityId,
                    metadata
            );

            // Yeni viewer'a gönder
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, infoPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);

            // Yakındaki oyuncuları da göster
            for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
                showNearbyPlayerToViewer(viewer, entry.getKey(), entry.getValue());
            }
        }, 2L);
    }

    /**
     * Yakındaki bir oyuncuyu belirli bir viewer'a gösterir
     */
    private void showNearbyPlayerToViewer(Player viewer, UUID nearbyUuid, int nearbyEntityId) {
        // Action listesinden bilgi bul
        for (com.reportsystem.common.replay.actions.ReplayAction action : replayPlayer.getActions()) {
            if (action instanceof NearbyPlayerAction) {
                NearbyPlayerAction nearbyAction = (NearbyPlayerAction) action;
                if (nearbyAction.getPlayerUuid().equals(nearbyUuid) &&
                        nearbyAction.getActionType() == NearbyPlayerAction.ActionType.PLAYER_APPEAR) {

                    // Player info - random name to avoid conflicts
                    String randomName = generateRandomName(8);
                    String realPlayerName = nearbyAction.getPlayerName();
                    UserProfile nearbyProfile = new UserProfile(nearbyUuid, randomName);
                    if (nearbyAction.getSkinTexture() != null && !nearbyAction.getSkinTexture().isEmpty()) {
                        nearbyProfile.setTextureProperties(Arrays.asList(
                                new TextureProperty("textures", nearbyAction.getSkinTexture(), nearbyAction.getSkinSignature())
                        ));
                    }

                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo nearbyInfo =
                            new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                    nearbyProfile,
                                    true,
                                    20,
                                    GameMode.SURVIVAL,
                                    Component.text("§7" + realPlayerName), // Gray color
                                    null
                            );

                    WrapperPlayServerPlayerInfoUpdate nearbyInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            nearbyInfo
                    );

                    // Spawn paketi
                    WrapperPlayServerSpawnEntity nearbySpawnPacket = new WrapperPlayServerSpawnEntity(
                            nearbyEntityId,
                            Optional.of(nearbyUuid),
                            EntityTypes.PLAYER,
                            new Vector3d(nearbyAction.getX(), nearbyAction.getY(), nearbyAction.getZ()),
                            nearbyAction.getPitch(),
                            nearbyAction.getYaw(),
                            nearbyAction.getYaw(),
                            0,
                            Optional.of(new Vector3d(0, 0, 0))
                    );

                    // Metadata
                    List<EntityData<?>> nearbyMetadata = new ArrayList<>();
                    nearbyMetadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
                    nearbyMetadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0xFF));

                    WrapperPlayServerEntityMetadata nearbyMetadataPacket = new WrapperPlayServerEntityMetadata(
                            nearbyEntityId,
                            nearbyMetadata
                    );

                    // Viewer'a gönder
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyInfoPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbySpawnPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyMetadataPacket);

                    // Equipment'ı da gönder
                    if (nearbyAction.getEquipment() != null && !nearbyAction.getEquipment().isEmpty()) {
                        sendNearbyPlayerEquipment(viewer, nearbyEntityId, nearbyAction.getEquipment());
                    }

                    break;
                }
            }
        }
    }

    /**
     * Nearby player'ın entity ID'sini döndürür
     */
    public Integer getNearbyPlayerEntityId(UUID playerUUID) {
        return nearbyPlayerEntities.get(playerUUID);
    }

    /**
     * Entity ID'den nearby player UUID'sini döndürür (reverse lookup)
     */
    public UUID getNearbyPlayerByEntityId(int entityId) {
        for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
            if (entry.getValue() == entityId) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Nearby player için health paketi gönderir
     */
    public void sendNearbyPlayerHealth(int entityId, double health, double maxHealth) {
        // Health metadata - index 9 = health (float)
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(9, EntityDataTypes.FLOAT, (float) health));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                metadata
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }

        plugin.getLogger().info("[REPLAY-DEBUG] Sent nearby player health: " + health + "/" + maxHealth +
                " for entity " + entityId);
    }

    /**
     * Nearby player entity flags'ini günceller
     */
    public void updateNearbyPlayerEntityFlags(int entityId, byte flags) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, flags));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                metadata
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }

        plugin.getLogger().info("[REPLAY-DEBUG] Updated nearby player entity flags: EntityID=" + entityId +
                " | Flags=" + flags);
    }

    // Getter'lar
    public int getEntityId() { return entityId; }
    public UUID getNpcUuid() { return npcUuid; }
}