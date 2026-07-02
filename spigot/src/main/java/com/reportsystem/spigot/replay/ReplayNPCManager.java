package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.utils.MetadataIndices;
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
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayNPCManager {

    // Global entity ID counter - tüm ReplayNPCManager instance'ları için paylaşımlı
    private static final AtomicInteger GLOBAL_ENTITY_ID_COUNTER = new AtomicInteger(1000000);

    // Scoreboard objective adı - below-name can gösterimi için
    private static final String HEALTH_OBJECTIVE_NAME = "rs_health";

    private final JavaPlugin plugin;
    private final ReplayPlayer replayPlayer;

    private final int entityId;
    private final UUID npcUuid;
    private final String npcProfileName; // Baş üstünde görünen isim
    private final String npcTeamName; // Scoreboard team adı

    private String skinTexture = "";
    private String skinSignature = "";

    // Glow toggle - viewer tarafından açılıp kapatılabilir
    private boolean glowEnabled = false;

    // 1. sahis kamera - viewer NPC'nin gozunden bakar
    private boolean firstPersonEnabled = false;

    // Son gonderilen el itemleri - 1. sahis gecisinde tekrar gondermek icin
    private List<Equipment> lastHandEquipment = new ArrayList<>();

    // Yakındaki oyuncular için
    private final Map<UUID, Integer> nearbyPlayerEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Location> nearbyPlayerLocations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> nearbyPlayerNpcUuids = new ConcurrentHashMap<>(); // Gerçek UUID -> NPC UUID mapping
    private final Map<UUID, String> nearbyPlayerTeamNames = new ConcurrentHashMap<>(); // NPC UUID -> team name
    private final Map<UUID, String> nearbyPlayerProfileNames = new ConcurrentHashMap<>(); // Gerçek UUID -> profil ismi

    public ReplayNPCManager(JavaPlugin plugin, ReplayPlayer replayPlayer) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
        // Global counter'dan benzersiz entity ID al
        this.entityId = GLOBAL_ENTITY_ID_COUNTER.getAndIncrement();
        // Çakışma olmaması için farklı UUID oluştur (gerçek oyuncununkinden farklı)
        this.npcUuid = UUID.randomUUID();
        // Gerçek oyuncu ismine §r (reset kodu) ekle - client bunu görünmez olarak işler
        // ama teknik olarak farklı string olduğu için gerçek oyuncunun team'ini bozmaz
        this.npcProfileName = replayPlayer.getReplay().getRecordedPlayer() + "\u00A7r";
        this.npcTeamName = "rs_main_" + entityId;
        ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] Created NPC Manager with unique entity ID: " + this.entityId +
                " and profile name: " + this.npcProfileName);
    }


    /**
     * NPC'yi spawn eder
     */
    public void spawnNPC(Location location) {
        // Gerçek ismi UserProfile'a koy - baş üstünde bu görünecek
        UserProfile profile = new UserProfile(npcUuid, npcProfileName);

        // Skin bilgisi varsa ekle
        if (skinTexture != null && !skinTexture.isEmpty()) {
            profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", skinTexture, skinSignature)));
        } else {
            profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", "", null)));
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile,
                        false, // TAB listesinde gizle
                        20, // Ping
                        GameMode.SURVIVAL,
                        null, // TAB'da görünmeyecek
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
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF); // 1.21.5+ icin guvenli, eski surumlerde index 17

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                metadata
        );

        // Team paketi - kırmızı renk ile isim gösterimi
        WrapperPlayServerTeams teamPacket = createTeamPacket(npcTeamName, npcProfileName, NamedTextColor.RED);

        // Paketleri gönder
        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, infoPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teamPacket);

            // Scoreboard health display - BELOW_NAME
            sendHealthScoreboardToViewer(viewer);
        }

        // Ana NPC için başlangıç can skoru (20 = full health)
        updateHealthScore(npcProfileName, 20.0, 0.0);
    }

    /**
     * NPC'yi teleport eder
     */
    public void teleportNPC(Location location, Map<Integer, Entity> mountedEntities) {
        // Mount durumunu kontrol et
        boolean isMounted = !mountedEntities.isEmpty();

        if (isMounted) {
            // Eğer bir araca binmişse, aracı hareket ettir
            final int mountGen = replayPlayer.getActionHandler().getSeekGeneration();
            for (Map.Entry<Integer, Entity> entry : mountedEntities.entrySet()) {
                Entity vehicle = entry.getValue();
                if (vehicle != null && vehicle.isValid()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (mountGen != replayPlayer.getActionHandler().getSeekGeneration()) return;
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
     * NPC'yi mutlaka absolute teleport ile taşır.
     * Seek/rewind/forward işlemlerinde kullanılır - relative move güvenilir değildir.
     */
    public void absoluteTeleportNPC(Location location) {
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getYaw(),
                location.getPitch(),
                false
        );

        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                entityId,
                location.getYaw()
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLookPacket);
        }
    }

    /**
     * NPC'yi despawn eder
     */
    public void despawnNPC() {
        // 1. sahis kamera aktifse sifirla
        if (firstPersonEnabled) {
            for (Player viewer : replayPlayer.getViewers()) {
                WrapperPlayServerCamera resetCam = new WrapperPlayServerCamera(viewer.getEntityId());
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, resetCam);
            }
            firstPersonEnabled = false;
        }

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
        ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] Despawning all NPCs - Main NPC and " + nearbyPlayerEntities.size() + " nearby players");

        // Scoreboard health display'i kaldır
        removeHealthScoreboard();

        // Ana NPC'yi kaldır
        despawnNPC();

        // Ana NPC team'ini kaldır
        for (Player viewer : replayPlayer.getViewers()) {
            WrapperPlayServerTeams removeMainTeam = new WrapperPlayServerTeams(
                    npcTeamName, WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeMainTeam);
        }

        // Yakındaki oyuncuları da kaldır
        for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
            WrapperPlayServerDestroyEntities destroyNearby =
                    new WrapperPlayServerDestroyEntities(entry.getValue());

            UUID npcUuidForNearby = nearbyPlayerNpcUuids.getOrDefault(entry.getKey(), entry.getKey());
            WrapperPlayServerPlayerInfoRemove removeNearby =
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(npcUuidForNearby));

            String teamToRemove = nearbyPlayerTeamNames.get(entry.getKey());

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyNearby);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeNearby);

                if (teamToRemove != null) {
                    WrapperPlayServerTeams removeTeam = new WrapperPlayServerTeams(
                            teamToRemove, WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeTeam);
                }
            }
        }
        nearbyPlayerEntities.clear();
        nearbyPlayerLocations.clear();
        nearbyPlayerNpcUuids.clear();
        nearbyPlayerTeamNames.clear();
        nearbyPlayerProfileNames.clear();

        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Player viewer : replayPlayer.getViewers()) {
                    if (viewer != null && viewer.isOnline()) {
                        viewer.updateCommands();
                    }
                }
                ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] All NPCs despawned and viewers updated");
            }, 5L);
        } else {
            ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] All NPCs despawned (plugin disabling)");
        }
    }

    /**
     * Animasyon gönderir
     */
    public void sendAnimation(WrapperPlayServerEntityAnimation.EntityAnimationType animationType) {
        WrapperPlayServerEntityAnimation animationPacket =
                new WrapperPlayServerEntityAnimation(entityId, animationType);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, animationPacket);

            // 1. sahis modunda viewer'in kendi entityId'si ile de gonder
            // Client sadece kendi entityId'sine ait kol animasyonlarini 1. sahista render eder
            if (firstPersonEnabled) {
                WrapperPlayServerEntityAnimation fpPacket =
                        new WrapperPlayServerEntityAnimation(viewer.getEntityId(), animationType);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, fpPacket);
            }
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
        // Glow toggle açıksa, her zaman 0x40 bitini ekle
        if (glowEnabled) {
            flags |= 0x40;
        }

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, flags));
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF); // 1.21.5+ icin guvenli, eski surumlerde index 17

        sendMetadata(metadata);
    }

    /**
     * Equipment gönderir
     */
    public void sendEquipment(List<Equipment> equipment) {
        try {
            // El itemlerini kaydet (1. sahis gecisinde tekrar gondermek icin)
            for (Equipment eq : equipment) {
                if (eq.getSlot() == EquipmentSlot.MAIN_HAND || eq.getSlot() == EquipmentSlot.OFF_HAND) {
                    lastHandEquipment.removeIf(e -> e.getSlot() == eq.getSlot());
                    lastHandEquipment.add(eq);
                }
            }

            WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(
                    entityId,
                    equipment
            );

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, equipmentPacket);

                // 1. sahis modunda el itemlerini viewer'in kendi entityId'si ile de gonder
                // Client sadece kendi entityId'sine ait el itemlerini 1. sahista render eder
                if (firstPersonEnabled) {
                    List<Equipment> handItems = new ArrayList<>();
                    for (Equipment eq : equipment) {
                        if (eq.getSlot() == EquipmentSlot.MAIN_HAND || eq.getSlot() == EquipmentSlot.OFF_HAND) {
                            handItems.add(eq);
                        }
                    }
                    if (!handItems.isEmpty()) {
                        WrapperPlayServerEntityEquipment fpPacket = new WrapperPlayServerEntityEquipment(
                                viewer.getEntityId(), handItems);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, fpPacket);
                    }
                }
            }

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sent equipment packet");
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
            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Duplicate spawn detected for " + action.getPlayerName() +
                    " | Old EntityID: " + oldEntityId + " will be removed");

            WrapperPlayServerDestroyEntities destroyOld = new WrapperPlayServerDestroyEntities(oldEntityId);
            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyOld);
            }
        }

        // Global counter'dan benzersiz entity ID al
        int nearbyEntityId = GLOBAL_ENTITY_ID_COUNTER.getAndIncrement();
        nearbyPlayerEntities.put(action.getPlayerUuid(), nearbyEntityId);

        // Çakışma olmaması için gerçek UUID'den farklı bir NPC UUID oluştur
        UUID nearbyNpcUuid = generateNpcUuid(action.getPlayerUuid());
        nearbyPlayerNpcUuids.put(action.getPlayerUuid(), nearbyNpcUuid);

        // §r ekle - gerçek oyuncunun team'ini bozmamak için
        String displayName = action.getPlayerName() + "\u00A7r";
        String teamName = "rs_near_" + nearbyEntityId;
        nearbyPlayerTeamNames.put(action.getPlayerUuid(), teamName);
        nearbyPlayerProfileNames.put(action.getPlayerUuid(), displayName);

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Spawning nearby player: " + displayName +
                " with entity ID: " + nearbyEntityId +
                " at " + action.getX() + ", " + action.getY() + ", " + action.getZ());

        // Player Info paketi
        UserProfile nearbyProfile = new UserProfile(nearbyNpcUuid, displayName);
        if (action.getSkinTexture() != null && !action.getSkinTexture().isEmpty()) {
            nearbyProfile.setTextureProperties(Arrays.asList(
                    new TextureProperty("textures", action.getSkinTexture(), action.getSkinSignature())
            ));
        }

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo nearbyInfo =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        nearbyProfile,
                        false, // TAB listesinde gizle
                        20,
                        GameMode.SURVIVAL,
                        null,
                        null
                );

        WrapperPlayServerPlayerInfoUpdate nearbyInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                nearbyInfo
        );

        // Entity spawn paketi - farklı UUID kullan
        WrapperPlayServerSpawnEntity nearbySpawnPacket = new WrapperPlayServerSpawnEntity(
                nearbyEntityId,
                Optional.of(nearbyNpcUuid),
                EntityTypes.PLAYER,
                new Vector3d(action.getX(), action.getY(), action.getZ()),
                action.getPitch(),
                action.getYaw(),
                action.getYaw(),
                0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        // Metadata
        List<EntityData<?>> nearbyMetadata = new ArrayList<>();
        nearbyMetadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
        MetadataIndices.addSkinParts(nearbyMetadata, (byte) 0xFF);

        WrapperPlayServerEntityMetadata nearbyMetadataPacket = new WrapperPlayServerEntityMetadata(
                nearbyEntityId,
                nearbyMetadata
        );

        // Team paketi - gri renk
        WrapperPlayServerTeams teamPacket = createTeamPacket(teamName, displayName, NamedTextColor.GRAY);

        // Paketleri sırayla gönder
        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyInfoPacket);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbySpawnPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyMetadataPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teamPacket);

                if (action.getEquipment() != null && !action.getEquipment().isEmpty()) {
                    sendNearbyPlayerEquipment(viewer, nearbyEntityId, action.getEquipment());
                }
            }, 2L);
        }

        // Nearby player için başlangıç can skoru
        updateHealthScore(displayName, 20.0, 0.0);
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

            ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sent " + equipmentList.size() +
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

        ReportSystemSpigot.getInstance().debug("[NEARBY-MOVE-DEBUG] Attempting to move nearby player UUID: " + action.getPlayerUuid() +
                " | Entity ID: " + moveEntityId +
                " | Location: " + String.format("%.1f, %.1f, %.1f", action.getX(), action.getY(), action.getZ()) +
                " | Total nearby entities: " + nearbyPlayerEntities.size());

        if (moveEntityId != null) {
            // Konum takibi
            nearbyPlayerLocations.put(action.getPlayerUuid(), new Location(
                    replayPlayer.getLastLocation() != null ? replayPlayer.getLastLocation().getWorld() : null,
                    action.getX(), action.getY(), action.getZ(),
                    action.getYaw(), action.getPitch()
            ));

            WrapperPlayServerEntityTeleport nearbyTeleport = new WrapperPlayServerEntityTeleport(
                    moveEntityId,
                    new Vector3d(action.getX(), action.getY(), action.getZ()),
                    action.getYaw(),
                    action.getPitch(),
                    false
            );

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyTeleport);
                ReportSystemSpigot.getInstance().debug("[NEARBY-MOVE-DEBUG] Sent teleport packet to viewer: " + viewer.getName());
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
        UUID npcUuidToRemove = nearbyPlayerNpcUuids.remove(action.getPlayerUuid());
        String teamToRemove = nearbyPlayerTeamNames.remove(action.getPlayerUuid());
        nearbyPlayerProfileNames.remove(action.getPlayerUuid());

        if (removeEntityId != null) {
            WrapperPlayServerDestroyEntities nearbyDestroy =
                    new WrapperPlayServerDestroyEntities(removeEntityId);

            UUID removeUuid = npcUuidToRemove != null ? npcUuidToRemove : action.getPlayerUuid();
            WrapperPlayServerPlayerInfoRemove nearbyRemove =
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(removeUuid));

            for (Player viewer : replayPlayer.getViewers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyDestroy);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyRemove);

                // Team'i kaldır
                if (teamToRemove != null) {
                    WrapperPlayServerTeams removeTeam = new WrapperPlayServerTeams(
                            teamToRemove, WrapperPlayServerTeams.TeamMode.REMOVE, (WrapperPlayServerTeams.ScoreBoardTeamInfo) null);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeTeam);
                }
            }
        }
    }

    /**
     * Yeni viewer'a NPC'yi gösterir
     */
    public void showNPCToViewer(Player viewer) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Gerçek isimle UserProfile oluştur
            UserProfile profile = new UserProfile(npcUuid, npcProfileName);
            if (skinTexture != null && !skinTexture.isEmpty()) {
                profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", skinTexture, skinSignature)));
            }

            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo =
                    new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            profile,
                            false, // TAB'da gizle
                            20,
                            GameMode.SURVIVAL,
                            null,
                            null
                    );

            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                    playerInfo
            );

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

            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
            MetadataIndices.addSkinParts(metadata, (byte) 0xFF);

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                    entityId,
                    metadata
            );

            // Team paketi - kırmızı renk
            WrapperPlayServerTeams teamPacket = createTeamPacket(npcTeamName, npcProfileName, NamedTextColor.RED);

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, infoPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teamPacket);

            // Scoreboard health display gönder
            sendHealthScoreboardToViewer(viewer);

            // Yakındaki oyuncuları da göster
            for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
                showNearbyPlayerToViewer(viewer, entry.getKey(), entry.getValue());
            }
        }, 2L);
    }

    /**
     * NPC'yi belirli bir viewer için yeniden spawn eder (dünya değişiminde kullanılır)
     * Client dünya değiştirince tüm entity'leri siler, bu yüzden NPC'yi tekrar göndermemiz lazım
     */
    public void respawnForViewer(Player viewer, Location location) {
        // Dünya değişiminde client'ın koruduğu/sildiği veriler:
        // - PlayerInfo listesi: KORUNUR → tekrar ADD_PLAYER göndermiyoruz
        // - Team verileri: KORUNUR → tekrar CREATE göndermiyoruz
        // - Entity'ler: SİLİNİR → tekrar SpawnEntity + Metadata göndermemiz lazım

        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(npcUuid),
                EntityTypes.PLAYER,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getPitch(),
                location.getYaw(),
                location.getYaw(),
                0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF);

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
    }

    /**
     * Yakındaki bir oyuncuyu belirli bir viewer'a gösterir
     */
    private void showNearbyPlayerToViewer(Player viewer, UUID nearbyUuid, int nearbyEntityId) {
        for (com.reportsystem.common.replay.actions.ReplayAction action : replayPlayer.getActions()) {
            if (action instanceof NearbyPlayerAction) {
                NearbyPlayerAction nearbyAction = (NearbyPlayerAction) action;
                if (nearbyAction.getPlayerUuid().equals(nearbyUuid) &&
                        nearbyAction.getActionType() == NearbyPlayerAction.ActionType.PLAYER_APPEAR) {

                    String displayName = nearbyAction.getPlayerName();
                    UUID nearbyNpcUuid = nearbyPlayerNpcUuids.getOrDefault(nearbyUuid, generateNpcUuid(nearbyUuid));
                    String teamName = nearbyPlayerTeamNames.get(nearbyUuid);

                    UserProfile nearbyProfile = new UserProfile(nearbyNpcUuid, displayName);
                    if (nearbyAction.getSkinTexture() != null && !nearbyAction.getSkinTexture().isEmpty()) {
                        nearbyProfile.setTextureProperties(Arrays.asList(
                                new TextureProperty("textures", nearbyAction.getSkinTexture(), nearbyAction.getSkinSignature())
                        ));
                    }

                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo nearbyInfo =
                            new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                                    nearbyProfile,
                                    false, // TAB'da gizle
                                    20,
                                    GameMode.SURVIVAL,
                                    null,
                                    null
                            );

                    WrapperPlayServerPlayerInfoUpdate nearbyInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            nearbyInfo
                    );

                    WrapperPlayServerSpawnEntity nearbySpawnPacket = new WrapperPlayServerSpawnEntity(
                            nearbyEntityId,
                            Optional.of(nearbyNpcUuid),
                            EntityTypes.PLAYER,
                            new Vector3d(nearbyAction.getX(), nearbyAction.getY(), nearbyAction.getZ()),
                            nearbyAction.getPitch(),
                            nearbyAction.getYaw(),
                            nearbyAction.getYaw(),
                            0,
                            Optional.of(new Vector3d(0, 0, 0))
                    );

                    List<EntityData<?>> nearbyMetadata = new ArrayList<>();
                    nearbyMetadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
                    MetadataIndices.addSkinParts(nearbyMetadata, (byte) 0xFF);

                    WrapperPlayServerEntityMetadata nearbyMetadataPacket = new WrapperPlayServerEntityMetadata(
                            nearbyEntityId,
                            nearbyMetadata
                    );

                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyInfoPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbySpawnPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, nearbyMetadataPacket);

                    // Team paketi - gri renk
                    if (teamName != null) {
                        WrapperPlayServerTeams teamPacket = createTeamPacket(teamName, displayName, NamedTextColor.GRAY);
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teamPacket);
                    }

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
     * Nearby player'ın son bilinen konumunu döndürür
     */
    public Location getNearbyPlayerLocation(UUID playerUUID) {
        return nearbyPlayerLocations.get(playerUUID);
    }

    /**
     * Nearby player'ın konumunu günceller
     */
    public void updateNearbyPlayerLocation(UUID playerUUID, Location location) {
        nearbyPlayerLocations.put(playerUUID, location);
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
     * Nearby player için health paketi gönderir ve scoreboard below-name günceller.
     * Eski API uyumluluğu - absorption 0.0 olarak kabul edilir.
     */
    public void sendNearbyPlayerHealth(int entityId, double health, double maxHealth) {
        sendNearbyPlayerHealth(entityId, health, maxHealth, 0.0);
    }

    /**
     * Nearby player için health paketi gönderir ve scoreboard below-name günceller
     */
    public void sendNearbyPlayerHealth(int entityId, double health, double maxHealth, double absorption) {
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

        // Scoreboard below-name güncelle
        UUID playerUuid = getNearbyPlayerByEntityId(entityId);
        if (playerUuid != null) {
            String profileName = nearbyPlayerProfileNames.get(playerUuid);
            if (profileName != null) {
                updateHealthScore(profileName, health, absorption);
            }
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Sent nearby player health: " + health + "/" + maxHealth +
                " absorption: " + absorption + " for entity " + entityId);
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

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Updated nearby player entity flags: EntityID=" + entityId +
                " | Flags=" + flags);
    }

    /**
     * Global counter'dan benzersiz entity ID üretir (fishing bobber vb. için)
     */
    public static int generateEntityId() {
        return GLOBAL_ENTITY_ID_COUNTER.getAndIncrement();
    }

    /**
     * Scoreboard team paketi oluşturur - NPC ismine renk vermek için
     * Kırmızı = report edilen oyuncu, Gri = civardaki oyuncular
     */
    private WrapperPlayServerTeams createTeamPacket(String teamName, String playerName, NamedTextColor color) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.text(teamName),
                Component.empty(), // Prefix yok - renk team color'dan gelecek
                Component.empty(), // Suffix yok
                WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                color, // Bu renk ismin rengini belirler
                WrapperPlayServerTeams.OptionData.NONE
        );

        return new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                Arrays.asList(playerName)
        );
    }

    /**
     * Gerçek UUID'den çakışmayan benzersiz NPC UUID'si oluşturur
     */
    private static UUID generateNpcUuid(UUID realUuid) {
        // Gerçek UUID'nin bit'lerini değiştirerek benzersiz ama deterministic bir UUID oluştur
        return new UUID(realUuid.getMostSignificantBits() ^ 0x7E91A700_7E91A700L,
                         realUuid.getLeastSignificantBits() ^ 0x4C700000_4C700000L);
    }

    /**
     * Tüm nearby player entity ID'lerini döndürür
     */
    public Map<UUID, Integer> getNearbyPlayerEntityIds() {
        return Collections.unmodifiableMap(nearbyPlayerEntities);
    }

    /**
     * Tum nearby player profil isimlerini dondurur (UUID -> isim)
     */
    public Map<UUID, String> getNearbyPlayerProfileNames() {
        return Collections.unmodifiableMap(nearbyPlayerProfileNames);
    }

    /**
     * Seek/rewind/forward sonrası tüm entity'lerin metadata durumlarını sıfırlar.
     * DYING pose, sneaking, swimming vb. durumları STANDING'e döndürür.
     * Bu sayede geri sar sonrası ölü pozisyonda kalmaz.
     */
    public void resetAllEntityStates() {
        // Ana NPC'yi respawn et (ölüm animasyonu client-side state'i bozuyor,
        // sadece metadata göndermek yetmiyor - Entity Status 3 sonrası client
        // "dead" flag'ini tutuyor ve EntityPose.STANDING'i yok sayıyor)
        respawnMainNPC();

        // Tüm nearby NPC'leri respawn et
        for (Map.Entry<UUID, Integer> entry : nearbyPlayerEntities.entrySet()) {
            respawnNearbyNPC(entry.getKey(), entry.getValue());
        }

        // ActionPlayer'daki entity flags'i de sıfırla
        replayPlayer.getActionPlayer().resetEntityFlags();

        ReportSystemSpigot.getInstance().debug("[REPLAY] Respawned all entities to reset states after seek");
    }

    /**
     * Ana NPC'yi destroy edip aynı yerde tekrar spawn eder.
     * Ölüm animasyonu gibi client-side state'leri tamamen sıfırlar.
     */
    private void respawnMainNPC() {
        Location loc = replayPlayer.getLastLocation();
        if (loc == null) return;

        // 1. Destroy
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);

        // 2. Re-spawn (aynı entityId ve UUID ile)
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

        // 3. Metadata (temiz state)
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE,
                com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.STANDING));
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF);

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }
    }

    /**
     * Nearby NPC'yi destroy edip aynı yerde tekrar spawn eder.
     */
    private void respawnNearbyNPC(UUID playerUuid, int nearbyEntityId) {
        Location loc = nearbyPlayerLocations.get(playerUuid);
        if (loc == null) return;

        UUID nearbyNpcUuid = nearbyPlayerNpcUuids.getOrDefault(playerUuid, playerUuid);

        // 1. Destroy
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(nearbyEntityId);

        // 2. Re-spawn
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                nearbyEntityId,
                Optional.of(nearbyNpcUuid),
                EntityTypes.PLAYER,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(),
                loc.getYaw(),
                loc.getYaw(),
                0,
                Optional.of(new Vector3d(0, 0, 0))
        );

        // 3. Metadata
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
        metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE,
                com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.STANDING));
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF);

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(nearbyEntityId, metadata);

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }
    }

    // ==================== SCOREBOARD HEALTH DISPLAY ====================

    /**
     * Viewer'a scoreboard health objective'ini gönderir (BELOW_NAME display slot).
     * Hypixel tarzı: NPC isimlerinin altında can değeri gösterilir.
     */
    private void sendHealthScoreboardToViewer(Player viewer) {
        // 1. Objective oluştur
        WrapperPlayServerScoreboardObjective objective = new WrapperPlayServerScoreboardObjective(
                HEALTH_OBJECTIVE_NAME,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.CREATE,
                Component.text("❤", NamedTextColor.RED),
                WrapperPlayServerScoreboardObjective.RenderType.HEARTS
        );

        // 2. BELOW_NAME display slot'una ata (position 2)
        WrapperPlayServerDisplayScoreboard display = new WrapperPlayServerDisplayScoreboard(
                2, HEALTH_OBJECTIVE_NAME
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, objective);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, display);

        ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] Health scoreboard sent to viewer: " + viewer.getName());
    }

    /**
     * Scoreboard'daki bir NPC'nin can skorunu günceller.
     * İsmin altında gösterilir: "20 ❤" gibi.
     * Absorption kalpleri toplam cana eklenir.
     */
    public void updateHealthScore(String playerName, double health, double absorption) {
        int score = (int) Math.ceil(health + absorption);
        if (score < 0) score = 0;

        WrapperPlayServerUpdateScore scorePacket = new WrapperPlayServerUpdateScore(
                playerName,
                WrapperPlayServerUpdateScore.Action.CREATE_OR_UPDATE_ITEM,
                HEALTH_OBJECTIVE_NAME,
                Optional.of(score)
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, scorePacket);
        }
    }

    /**
     * Ana NPC'nin can durumunu günceller.
     * Entity metadata (index 9 = health float) + scoreboard below-name.
     * Entity metadata sayesinde hasar alınca kırmızı flash animasyonu da gösterilir.
     */
    public void updateMainNPCHealth(double health, double maxHealth, double absorption) {
        // 1. Entity metadata - hasar animasyonu için
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(9, EntityDataTypes.FLOAT, (float) health));

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId, metadata
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
        }

        // 2. Scoreboard below-name güncelle
        updateHealthScore(npcProfileName, health, absorption);

        ReportSystemSpigot.getInstance().debug("[REPLAY-DEBUG] Main NPC health updated: " + health + "/" + maxHealth +
                " absorption: " + absorption);
    }

    /**
     * Scoreboard health objective'ini tüm viewer'lardan kaldırır.
     * Replay bittiğinde veya NPC'ler kaldırıldığında çağrılır.
     */
    private void removeHealthScoreboard() {
        WrapperPlayServerScoreboardObjective removeObjective = new WrapperPlayServerScoreboardObjective(
                HEALTH_OBJECTIVE_NAME,
                WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE,
                Component.empty(),
                WrapperPlayServerScoreboardObjective.RenderType.INTEGER
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removeObjective);
        }

        ReportSystemSpigot.getInstance().debug("[REPLAY-NPC] Health scoreboard removed from all viewers");
    }

    // ==================== GLOW TOGGLE ====================

    public boolean isGlowEnabled() { return glowEnabled; }

    /**
     * Şüphelinin glow efektini açar/kapatır.
     * Entity flag 0x40 = glowing outline (team renginde görünür).
     */
    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        byte flags = replayPlayer.getActionPlayer().getCurrentEntityFlags();
        if (enabled) {
            flags |= 0x40;
        } else {
            flags &= ~0x40;
        }
        updateEntityFlags(flags);
    }

    // ==================== 1. SAHIS KAMERA ====================

    public boolean isFirstPersonEnabled() { return firstPersonEnabled; }

    /**
     * 1. sahis gorunumu - viewer NPC'nin gozunden bakar.
     * WrapperPlayServerCamera paketi ile client kamerasini NPC'ye baglar.
     */
    public void setFirstPerson(boolean enabled) {
        this.firstPersonEnabled = enabled;
        for (Player viewer : replayPlayer.getViewers()) {
            int cameraEntityId = enabled ? entityId : viewer.getEntityId();
            WrapperPlayServerCamera cameraPacket = new WrapperPlayServerCamera(cameraEntityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, cameraPacket);

        }
    }

    /**
     * 1. sahis modunda viewer'a NPC'nin el itemlerini tekrar gonderir.
     * enterFirstPersonMode envanter temizledikten SONRA cagrilir.
     */
    public void resendFirstPersonEquipment(Player viewer) {
        if (firstPersonEnabled && !lastHandEquipment.isEmpty()) {
            WrapperPlayServerEntityEquipment fpPacket = new WrapperPlayServerEntityEquipment(
                    viewer.getEntityId(), new ArrayList<>(lastHandEquipment));
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, fpPacket);
        }
    }

    // ==================== GETTER'LAR ====================

    public int getEntityId() { return entityId; }
    public UUID getNpcUuid() { return npcUuid; }
    public String getNpcProfileName() { return npcProfileName; }
}