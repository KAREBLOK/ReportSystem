package com.reportsystem.spigot.overwatch;

import com.reportsystem.common.database.OverwatchDAO;
import com.reportsystem.common.models.overwatch.OverwatchNPC;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.utils.MetadataIndices;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NPCManager {

    private static final String OVERWATCH_HOLOGRAM_TAG = "overwatch_hologram";
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(2000000);

    private static final String DEFAULT_SKIN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTg2YzI1YTljNDI3YjU3YjU5YzY0MjA2YjBlNjgzYTUyMjUxNTZhZjE0YTFiNTEwMjAyMzM1NjYyNDJmZTYifX19";

    private final ReportSystemSpigot plugin;
    private final OverwatchDAO overwatchDAO;
    private final Map<String, NPCData> activeNPCs = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityIdToNpcId = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedNPC = new ConcurrentHashMap<>();

    public NPCManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.overwatchDAO = new OverwatchDAO(plugin.getDatabaseManager());
    }

    // ============= Lifecycle =============

    public void loadNPCs() {
        try {
            plugin.debug("[OVERWATCH-NPC] Starting to load NPCs for server: " + plugin.getServerName());
            clearAllNPCs();

            List<OverwatchNPC> npcs = overwatchDAO.getAllNPCs(plugin.getServerName());
            plugin.debug("[OVERWATCH-NPC] Found " + npcs.size() + " NPCs in database");

            for (OverwatchNPC npc : npcs) {
                Location loc = new Location(
                        Bukkit.getWorld(npc.getWorld()),
                        npc.getX(), npc.getY(), npc.getZ(),
                        npc.getYaw(), npc.getPitch());

                if (loc.getWorld() != null) {
                    loc.getChunk().load();
                    registerNPC(npc.getId(), loc, npc.getDisplayName(), npc.getSkinTexture(), npc.getSkinSignature());
                } else {
                    plugin.getLogger().warning("[OVERWATCH-NPC] World '" + npc.getWorld()
                            + "' not found! Skipping NPC: " + npc.getDisplayName());
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                showAllNPCsToPlayer(player);
            }

            plugin.debug("[OVERWATCH-NPC] Loaded " + npcs.size() + " NPCs");
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to load NPCs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearAllNPCs() {
        for (NPCData npcData : activeNPCs.values()) {
            despawnNPCFromAll(npcData);
            removeHolograms(npcData);
        }
        entityIdToNpcId.clear();
        activeNPCs.clear();
    }

    public void shutdown() {
        clearAllNPCs();
        plugin.debug("[OVERWATCH-NPC] All NPCs cleaned up");
    }

    // ============= NPC CRUD =============

    public String createNPC(Player creator, Location location, String customName) {
        String npcId = UUID.randomUUID().toString();

        try {
            String displayName = customName;
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "OW-" + (activeNPCs.size() + 1);
            }

            OverwatchNPC npc = new OverwatchNPC(
                    npcId, plugin.getServerName(), location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    System.currentTimeMillis(), creator.getUniqueId().toString(),
                    displayName, null, null);

            overwatchDAO.createNPC(npc);
            registerNPC(npcId, location, displayName, null, null);

            for (Player player : Bukkit.getOnlinePlayers()) {
                showNPCToPlayer(player, activeNPCs.get(npcId));
            }

            return npcId;
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to create NPC: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteNPC(String npcId) {
        try {
            NPCData npcData = activeNPCs.remove(npcId);
            if (npcData != null) {
                entityIdToNpcId.remove(npcData.entityId);
                despawnNPCFromAll(npcData);
                removeHolograms(npcData);
            }

            overwatchDAO.deleteNPC(npcId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to delete NPC: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ============= NPC Management Commands =============

    public void setSkin(String npcId, String texture, String signature) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null)
            return;

        npcData.skinTexture = texture;
        npcData.skinSignature = signature;

        // Save to DB async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                overwatchDAO.updateNPCSkin(npcId, texture, signature);
            } catch (SQLException e) {
                plugin.getLogger().severe("[OVERWATCH-NPC] Failed to save skin: " + e.getMessage());
            }
        });

        respawnNPCForAll(npcId);
    }

    public void moveNPC(String npcId, Location newLocation) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null)
            return;

        npcData.location = newLocation.clone();

        // Save to DB async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                overwatchDAO.updateNPCPosition(npcId, newLocation.getWorld().getName(),
                        newLocation.getX(), newLocation.getY(), newLocation.getZ(),
                        newLocation.getYaw(), newLocation.getPitch());
            } catch (SQLException e) {
                plugin.getLogger().severe("[OVERWATCH-NPC] Failed to save position: " + e.getMessage());
            }
        });

        // Remove old holograms, respawn everything
        removeHolograms(npcData);
        spawnHolograms(npcData);
        respawnNPCForAll(npcId);
        updateHologram(npcId);
    }

    public void lookNPC(String npcId, Location targetLocation) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null)
            return;

        Location npcLoc = npcData.location;
        double dx = targetLocation.getX() - npcLoc.getX();
        double dz = targetLocation.getZ() - npcLoc.getZ();
        double dy = targetLocation.getY() - (npcLoc.getY() + 1.62); // NPC eye height
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, dist));

        npcData.location.setYaw(yaw);
        npcData.location.setPitch(pitch);

        // Save to DB async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                overwatchDAO.updateNPCRotation(npcId, yaw, pitch);
            } catch (SQLException e) {
                plugin.getLogger().severe("[OVERWATCH-NPC] Failed to save rotation: " + e.getMessage());
            }
        });

        // Send rotation packets (no full respawn needed)
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                npcData.entityId,
                new Vector3d(npcLoc.getX(), npcLoc.getY(), npcLoc.getZ()),
                yaw, pitch, true);
        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                npcData.entityId, yaw);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, teleportPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLookPacket);
        }
    }

    public void renameNPC(String npcId, String newName) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null)
            return;

        npcData.displayName = newName;

        // Save to DB async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                overwatchDAO.updateNPCDisplayName(npcId, newName);
            } catch (SQLException e) {
                plugin.getLogger().severe("[OVERWATCH-NPC] Failed to save display name: " + e.getMessage());
            }
        });
    }

    // ============= NPC Selection =============

    public void selectNPC(UUID playerUuid, String npcId) {
        selectedNPC.put(playerUuid, npcId);
    }

    public String getSelectedNPC(UUID playerUuid) {
        return selectedNPC.get(playerUuid);
    }

    public void clearSelection(UUID playerUuid) {
        selectedNPC.remove(playerUuid);
    }

    // ============= Internal Helpers =============

    private void registerNPC(String npcId, Location location, String displayName, String skinTexture,
            String skinSignature) {
        int entityId = ENTITY_ID_COUNTER.getAndIncrement();
        UUID npcUuid = UUID.randomUUID();
        String npcName = displayName != null ? displayName : "OVERWATCH";
        String shortName = npcName.length() > 14 ? npcName.substring(0, 14) : npcName;
        String profileName = shortName + "\u00A7r";
        String teamName = "ow_npc_" + entityId;

        NPCData npcData = new NPCData(npcId, entityId, npcUuid, profileName, teamName, location, displayName);
        npcData.skinTexture = skinTexture;
        npcData.skinSignature = skinSignature;

        spawnHolograms(npcData);

        activeNPCs.put(npcId, npcData);
        entityIdToNpcId.put(entityId, npcId);

        updateHologram(npcId);
    }

    private void spawnHolograms(NPCData npcData) {
        List<ArmorStand> hologramLines = new ArrayList<>();
        Location hologramLoc = npcData.location.clone().add(0, 3.2, 0);

        String titleMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.title");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(titleMsg)));

        hologramLoc.subtract(0, 0.25, 0);
        String loadingMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.loading");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        hologramLoc.subtract(0, 0.25, 0);
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        hologramLoc.subtract(0, 0.25, 0);
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        hologramLoc.subtract(0, 0.25, 0);
        String clickMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.click-prompt");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(clickMsg)));

        npcData.hologramLines = hologramLines;
    }

    private void removeHolograms(NPCData npcData) {
        if (npcData.hologramLines != null) {
            for (ArmorStand hologram : npcData.hologramLines) {
                if (hologram != null && !hologram.isDead()) {
                    hologram.remove();
                }
            }
            npcData.hologramLines = null;
        }
    }

    private void showNPCToPlayer(Player player, NPCData npcData) {
        if (npcData == null)
            return;

        Location loc = npcData.location;

        // Skin: use per-NPC skin or fall back to default
        String texture = npcData.skinTexture != null ? npcData.skinTexture : DEFAULT_SKIN_TEXTURE;
        String signature = npcData.skinSignature;

        // 1. PlayerInfo
        UserProfile profile = new UserProfile(npcData.npcUuid, npcData.profileName);
        profile.setTextureProperties(Arrays.asList(new TextureProperty("textures", texture, signature)));

        WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, false, 20, GameMode.SURVIVAL, null, null));

        // 2. Spawn
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                npcData.entityId, Optional.of(npcData.npcUuid), EntityTypes.PLAYER,
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getPitch(), loc.getYaw(), loc.getYaw(), 0,
                Optional.of(new Vector3d(0, 0, 0)));

        // 3. Metadata (skin layers)
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0));
        MetadataIndices.addSkinParts(metadata, (byte) 0xFF);

        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(npcData.entityId,
                metadata);

        // 4. Head rotation
        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(npcData.entityId,
                loc.getYaw());

        // 5. Team - name HIDDEN
        WrapperPlayServerTeams teamPacket = createTeamPacket(npcData.teamName, npcData.profileName);

        // Send packets
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, infoPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metadataPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, headLookPacket);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, teamPacket);

        // Remove from TAB after skin loads
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline())
                return;
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerPlayerInfoRemove(Arrays.asList(npcData.npcUuid)));
        }, 40L);
    }

    public void showAllNPCsToPlayer(Player player) {
        for (NPCData npcData : activeNPCs.values()) {
            showNPCToPlayer(player, npcData);
        }
    }

    private void despawnNPCFromAll(NPCData npcData) {
        if (npcData == null)
            return;

        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(npcData.entityId);
        WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(
                Arrays.asList(npcData.npcUuid));

        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroyPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
        }
    }

    private void respawnNPCForAll(String npcId) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null)
            return;

        despawnNPCFromAll(npcData);

        for (Player player : Bukkit.getOnlinePlayers()) {
            showNPCToPlayer(player, npcData);
        }
    }

    private ArmorStand createHologramLine(Location location, String text) {
        ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        hologram.setPersistent(false); // Prevents saving to world files (duplicate bug fix)
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomName(plugin.getMessageManager().colorize(text));
        hologram.setCustomNameVisible(true);
        hologram.setInvulnerable(true);
        hologram.setMarker(true);
        hologram.addScoreboardTag(OVERWATCH_HOLOGRAM_TAG);
        return hologram;
    }

    private WrapperPlayServerTeams createTeamPacket(String teamName, String playerName) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.text(teamName),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                NamedTextColor.GOLD,
                WrapperPlayServerTeams.OptionData.NONE);

        return new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                Arrays.asList(playerName));
    }

    // ============= Hologram Stats =============

    public void updateHologram(String npcId) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null || npcData.hologramLines == null)
            return;

        int weeklyReviewed = plugin.getOverwatchManager().getWeeklyReviewedCount();
        int totalPunished = plugin.getOverwatchManager().getTotalPunishedCount();
        int pendingQueue = plugin.getOverwatchManager().getPendingQueueCount();

        List<ArmorStand> lines = npcData.hologramLines;
        if (lines.size() >= 5) {
            lines.get(1).setCustomName(plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.npc.hologram.weekly-reviewed")
                            .replace("%count%", String.valueOf(weeklyReviewed))));
            lines.get(2).setCustomName(plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.npc.hologram.total-punished")
                            .replace("%count%", String.valueOf(totalPunished))));
            lines.get(3).setCustomName(plugin.getMessageManager().colorize(
                    plugin.getMessageManager().getMessage("overwatch.npc.hologram.pending-queue")
                            .replace("%count%", String.valueOf(pendingQueue))));
        }
    }

    public void updateAllHolograms() {
        for (String npcId : activeNPCs.keySet()) {
            updateHologram(npcId);
        }
    }

    public void startHologramUpdateTask() {
        int interval = plugin.getConfig().getInt("overwatch.npc.hologram-update-interval", 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllHolograms, 20L * interval, 20L * interval);
    }

    // ============= Lookup =============

    public String getNPCByEntityId(int entityId) {
        return entityIdToNpcId.get(entityId);
    }

    public Map<String, NPCData> getActiveNPCs() {
        return activeNPCs;
    }

    public String findNPCId(String nameOrId) {
        if (activeNPCs.containsKey(nameOrId))
            return nameOrId;

        for (Map.Entry<String, NPCData> entry : activeNPCs.entrySet()) {
            NPCData data = entry.getValue();
            if (data.displayName != null && data.displayName.equalsIgnoreCase(nameOrId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ============= NPCData =============

    public static class NPCData {
        private final String id;
        private final int entityId;
        private final UUID npcUuid;
        private final String profileName;
        private final String teamName;
        private Location location;
        private String displayName;
        private String skinTexture;
        private String skinSignature;
        private List<ArmorStand> hologramLines;

        public NPCData(String id, int entityId, UUID npcUuid, String profileName, String teamName,
                Location location, String displayName) {
            this.id = id;
            this.entityId = entityId;
            this.npcUuid = npcUuid;
            this.profileName = profileName;
            this.teamName = teamName;
            this.location = location;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public int getEntityId() {
            return entityId;
        }

        public UUID getNpcUuid() {
            return npcUuid;
        }

        public String getProfileName() {
            return profileName;
        }

        public String getTeamName() {
            return teamName;
        }

        public Location getLocation() {
            return location;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSkinTexture() {
            return skinTexture;
        }

        public String getSkinSignature() {
            return skinSignature;
        }

        public List<ArmorStand> getHologramLines() {
            return hologramLines;
        }
    }
}
