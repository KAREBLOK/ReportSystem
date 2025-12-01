package com.reportsystem.spigot.overwatch;

import com.reportsystem.common.database.OverwatchDAO;
import com.reportsystem.common.models.overwatch.OverwatchNPC;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.sql.SQLException;
import java.util.*;

/**
 * NPC Manager - Manages Overwatch NPCs with holograms
 * Uses armor stands with player heads as NPCs
 * BungeeCord compatible
 */
public class NPCManager {

    private final ReportSystemSpigot plugin;
    private final OverwatchDAO overwatchDAO;
    private final Map<String, NPCData> activeNPCs = new HashMap<>();

    public NPCManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.overwatchDAO = new OverwatchDAO(plugin.getDatabaseManager());
    }

    /**
     * Load all NPCs for this server
     */
    public void loadNPCs() {
        try {
            plugin.getLogger().info("[OVERWATCH-NPC] Starting to load NPCs for server: " + plugin.getServerName());

            // First, clear any existing NPCs (in case of reload)
            clearAllNPCs();

            // CRITICAL: Also remove any leftover armor stands from previous sessions
            removeAllOverwatchArmorStands();

            List<OverwatchNPC> npcs = overwatchDAO.getAllNPCs(plugin.getServerName());
            plugin.getLogger().info("[OVERWATCH-NPC] Found " + npcs.size() + " NPCs in database");

            for (OverwatchNPC npc : npcs) {
                plugin.getLogger().info("[OVERWATCH-NPC] Loading NPC: " + npc.getId() + " (" + npc.getDisplayName() + ") in world: " + npc.getWorld());

                Location loc = new Location(
                    Bukkit.getWorld(npc.getWorld()),
                    npc.getX(),
                    npc.getY(),
                    npc.getZ(),
                    npc.getYaw(),
                    npc.getPitch()
                );

                if (loc.getWorld() != null) {
                    // Ensure chunk is loaded before spawning
                    loc.getChunk().load();

                    // Remove any existing armor stands at this exact location first
                    removeArmorStandsNearLocation(loc, 1.0);

                    spawnNPC(npc.getId(), loc, npc.getDisplayName());
                    plugin.getLogger().info("[OVERWATCH-NPC] Successfully spawned NPC: " + npc.getDisplayName());
                } else {
                    plugin.getLogger().warning("[OVERWATCH-NPC] World '" + npc.getWorld() + "' not found! Skipping NPC: " + npc.getDisplayName());
                }
            }

            plugin.getLogger().info("[OVERWATCH-NPC] Loaded " + npcs.size() + " NPCs for " + plugin.getServerName());
            plugin.getLogger().info("[OVERWATCH-NPC] Active NPCs in memory: " + activeNPCs.size());

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to load NPCs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clear all NPCs from memory and world
     */
    private void clearAllNPCs() {
        for (NPCData npcData : activeNPCs.values()) {
            if (npcData.getNpcStand() != null && !npcData.getNpcStand().isDead()) {
                npcData.getNpcStand().remove();
            }
            if (npcData.getHologramLines() != null) {
                for (ArmorStand hologram : npcData.getHologramLines()) {
                    if (hologram != null && !hologram.isDead()) {
                        hologram.remove();
                    }
                }
            }
        }
        activeNPCs.clear();
    }

    /**
     * Remove armor stands near a specific location (for cleanup before respawn)
     */
    private void removeArmorStandsNearLocation(Location loc, double radius) {
        if (loc.getWorld() == null) return;

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, 5.0, radius)) {
            if (entity instanceof ArmorStand) {
                ArmorStand as = (ArmorStand) entity;
                String name = as.getCustomName();
                // Check if it's an Overwatch related armor stand
                if (name != null && (
                    name.contains("§6§l") ||
                    name.contains("OVERWATCH") ||
                    name.contains("Haftalık") ||
                    name.contains("Toplam") ||
                    name.contains("Bekleyen") ||
                    name.contains("Başlamak için")
                )) {
                    plugin.getLogger().info("[OVERWATCH-NPC] Removing old armor stand at spawn location: " + name);
                    as.remove();
                }
            }
        }
    }

    /**
     * Remove ALL Overwatch armor stands from ALL worlds
     * This cleans up any leftover armor stands from previous server sessions
     */
    private void removeAllOverwatchArmorStands() {
        int removedCount = 0;

        plugin.getLogger().info("[OVERWATCH-NPC] Scanning all worlds for leftover Overwatch armor stands...");

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof ArmorStand)) {
                    continue;
                }

                ArmorStand armorStand = (ArmorStand) entity;
                String customName = armorStand.getCustomName();

                // Check if it's an Overwatch armor stand (NPC or hologram)
                if (customName != null && (
                    customName.contains("§6§l") ||  // NPC name format
                    customName.contains("OVERWATCH") ||  // Hologram title
                    customName.contains("Haftalık") ||  // Hologram stats
                    customName.contains("Toplam") ||
                    customName.contains("Bekleyen") ||
                    customName.contains("Başlamak için")
                )) {
                    plugin.getLogger().info("[OVERWATCH-NPC] Removing leftover armor stand: " + customName +
                                          " at " + armorStand.getLocation());
                    armorStand.remove();
                    removedCount++;
                }
            }
        }

        if (removedCount > 0) {
            plugin.getLogger().info("[OVERWATCH-NPC] Removed " + removedCount + " leftover armor stands");
        } else {
            plugin.getLogger().info("[OVERWATCH-NPC] No leftover armor stands found");
        }
    }

    /**
     * Create and spawn NPC at location
     */
    public String createNPC(Player creator, Location location, String customName) {
        String npcId = UUID.randomUUID().toString();

        try {
            // If no custom name provided, generate one (OW-1, OW-2, etc.)
            String displayName = customName;
            if (displayName == null || displayName.trim().isEmpty()) {
                int npcCount = activeNPCs.size() + 1;
                displayName = "OW-" + npcCount;
                plugin.getLogger().info("[OVERWATCH-NPC] Auto-generated name: " + displayName);
            }

            // Save to database
            OverwatchNPC npc = new OverwatchNPC(
                npcId,
                plugin.getServerName(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                System.currentTimeMillis(),
                creator.getUniqueId().toString(),
                displayName
            );

            overwatchDAO.createNPC(npc);

            // Spawn in world
            spawnNPC(npcId, location, displayName);

            plugin.getLogger().info("[OVERWATCH-NPC] Created NPC " + npcId + " with name '" + displayName + "' at " + location);

            return npcId;

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to create NPC: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Spawn NPC in world
     */
    private void spawnNPC(String npcId, Location location, String displayName) {
        // Use custom display name or default
        String npcName = displayName != null ? displayName : "§6§lOVERWATCH";

        // Create armor stand for NPC
        ArmorStand npcStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        npcStand.setVisible(true); // Make armor stand visible to show body
        npcStand.setGravity(false);
        npcStand.setCustomName("§6§l" + npcName); // Bold Gold + custom name
        npcStand.setCustomNameVisible(false);
        npcStand.setInvulnerable(true);
        npcStand.setBasePlate(false);
        npcStand.setArms(true);
        npcStand.setCanPickupItems(false);
        npcStand.setCollidable(false);
        npcStand.setMarker(false); // Don't use marker mode for NPC itself
        npcStand.setAI(false);
        npcStand.setPersistent(true);

        // Lock all equipment slots to prevent stealing
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
            npcStand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        // Set pirate head (custom texture)
        ItemStack head = createCustomHead(
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTg2YzI1YTljNDI3YjU3YjU5YzY0MjA2YjBlNjgzYTUyMjUxNTZhZjE0YTFiNTEwMjAyMzM1NjYyNDJmZTYifX19"
        ); // Pirate captain head
        npcStand.setHelmet(head);

        // Add armor to make it look like a guard/judge
        npcStand.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        npcStand.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        npcStand.setBoots(new ItemStack(Material.IRON_BOOTS));
        // Optional: Add item in hand (book represents law/justice)
        npcStand.setItemInHand(new ItemStack(Material.BOOK));

        // Create hologram lines (above NPC)
        List<ArmorStand> hologramLines = new ArrayList<>();

        Location hologramLoc = location.clone().add(0, 2.5, 0);

        // Line 1: Title
        String titleMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.title");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(titleMsg)));

        // Line 2: Weekly reviewed
        hologramLoc.subtract(0, 0.25, 0);
        String loadingMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.loading");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        // Line 3: Total punished
        hologramLoc.subtract(0, 0.25, 0);
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        // Line 4: Pending queue
        hologramLoc.subtract(0, 0.25, 0);
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(loadingMsg)));

        // Line 5: Click to start
        hologramLoc.subtract(0, 0.25, 0);
        String clickMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.click-prompt");
        hologramLines.add(createHologramLine(hologramLoc, plugin.getMessageManager().colorize(clickMsg)));

        // Store NPC data
        NPCData npcData = new NPCData(npcId, npcStand, hologramLines, location, displayName);
        activeNPCs.put(npcId, npcData);

        // Schedule hologram update
        updateHologram(npcId);
    }

    /**
     * Create hologram line (armor stand with text)
     */
    private ArmorStand createHologramLine(Location location, String text) {
        ArmorStand hologram = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomName(plugin.getMessageManager().colorize(text));
        hologram.setCustomNameVisible(true);
        hologram.setInvulnerable(true);
        hologram.setMarker(true); // Makes it non-collidable
        return hologram;
    }

    /**
     * Create a custom player head with a Base64 texture
     */
    private ItemStack createCustomHead(String base64Texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64Texture));
            skullMeta.setPlayerProfile(profile);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    /**
     * Update hologram statistics
     */
    public void updateHologram(String npcId) {
        NPCData npcData = activeNPCs.get(npcId);
        if (npcData == null) return;

        // Get statistics
        int weeklyReviewed = plugin.getOverwatchManager().getWeeklyReviewedCount();
        int totalPunished = plugin.getOverwatchManager().getTotalPunishedCount();
        int pendingQueue = plugin.getOverwatchManager().getPendingQueueCount();

        List<ArmorStand> lines = npcData.getHologramLines();
        if (lines.size() >= 5) {
            String weeklyMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.weekly-reviewed")
                    .replace("%count%", String.valueOf(weeklyReviewed));
            lines.get(1).setCustomName(plugin.getMessageManager().colorize(weeklyMsg));

            String punishedMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.total-punished")
                    .replace("%count%", String.valueOf(totalPunished));
            lines.get(2).setCustomName(plugin.getMessageManager().colorize(punishedMsg));

            String queueMsg = plugin.getMessageManager().getMessage("overwatch.npc.hologram.pending-queue")
                    .replace("%count%", String.valueOf(pendingQueue));
            lines.get(3).setCustomName(plugin.getMessageManager().colorize(queueMsg));
        }
    }

    /**
     * Update all holograms
     */
    public void updateAllHolograms() {
        for (String npcId : activeNPCs.keySet()) {
            updateHologram(npcId);
        }
    }

    /**
     * Delete NPC
     */
    public boolean deleteNPC(String npcId) {
        try {
            plugin.getLogger().info("[OVERWATCH-NPC] Deleting NPC: " + npcId);

            // Remove from world
            NPCData npcData = activeNPCs.remove(npcId);
            if (npcData != null) {
                plugin.getLogger().info("[OVERWATCH-NPC] Found NPC in activeNPCs, removing entities...");

                // Remove NPC armor stand
                if (npcData.getNpcStand() != null) {
                    if (!npcData.getNpcStand().isDead()) {
                        npcData.getNpcStand().remove();
                        plugin.getLogger().info("[OVERWATCH-NPC] Removed NPC armor stand");
                    } else {
                        plugin.getLogger().warning("[OVERWATCH-NPC] NPC armor stand was already dead");
                    }
                } else {
                    plugin.getLogger().warning("[OVERWATCH-NPC] NPC armor stand was null");
                }

                // Remove hologram lines
                if (npcData.getHologramLines() != null) {
                    int removedCount = 0;
                    for (ArmorStand hologram : npcData.getHologramLines()) {
                        if (hologram != null && !hologram.isDead()) {
                            hologram.remove();
                            removedCount++;
                        }
                    }
                    plugin.getLogger().info("[OVERWATCH-NPC] Removed " + removedCount + " hologram lines");
                } else {
                    plugin.getLogger().warning("[OVERWATCH-NPC] Hologram lines were null");
                }
            } else {
                plugin.getLogger().warning("[OVERWATCH-NPC] NPC not found in activeNPCs map! Continuing with database deletion...");
            }

            // Remove from database
            overwatchDAO.deleteNPC(npcId);

            plugin.getLogger().info("[OVERWATCH-NPC] Successfully deleted NPC " + npcId + " from database");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("[OVERWATCH-NPC] Failed to delete NPC from database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get NPC at location (for click detection)
     */
    public String getNPCAtLocation(Entity entity) {
        plugin.getLogger().info("[NPC-MANAGER] Checking if entity ID " + entity.getEntityId() + " matches any NPC...");

        for (Map.Entry<String, NPCData> entry : activeNPCs.entrySet()) {
            ArmorStand npcStand = entry.getValue().getNpcStand();

            if (npcStand == null) {
                plugin.getLogger().warning("[NPC-MANAGER] NPC " + entry.getKey() + " has null armor stand!");
                continue;
            }

            if (npcStand.isDead()) {
                plugin.getLogger().warning("[NPC-MANAGER] NPC " + entry.getKey() + " armor stand is dead!");
                continue;
            }

            plugin.getLogger().info("[NPC-MANAGER] Comparing with NPC " + entry.getValue().getDisplayName() +
                    " (Entity ID: " + npcStand.getEntityId() + ")");

            // Compare by entity ID instead of equals()
            if (npcStand.getEntityId() == entity.getEntityId()) {
                plugin.getLogger().info("[NPC-MANAGER] MATCH FOUND! NPC: " + entry.getKey());
                return entry.getKey();
            }
        }

        plugin.getLogger().info("[NPC-MANAGER] No match found for entity ID " + entity.getEntityId());
        return null;
    }

    /**
     * Get all active NPCs
     */
    public Map<String, NPCData> getActiveNPCs() {
        return activeNPCs;
    }

    /**
     * Find NPC ID by display name or ID
     * Returns the ID if found, null otherwise
     */
    public String findNPCId(String nameOrId) {
        // First check if it's a direct ID match
        if (activeNPCs.containsKey(nameOrId)) {
            return nameOrId;
        }

        // Then search by display name
        for (Map.Entry<String, NPCData> entry : activeNPCs.entrySet()) {
            NPCData data = entry.getValue();
            if (data.getDisplayName() != null &&
                data.getDisplayName().equalsIgnoreCase(nameOrId)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Start hologram update task
     */
    public void startHologramUpdateTask() {
        int interval = plugin.getConfig().getInt("overwatch.npc.hologram-update-interval", 30);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateAllHolograms();
        }, 20L * interval, 20L * interval); // Every X seconds
    }

    /**
     * Cleanup all NPCs on plugin disable
     */
    public void shutdown() {
        clearAllNPCs();
        plugin.getLogger().info("[OVERWATCH-NPC] All NPCs cleaned up");
    }

    /**
     * Inner class to store NPC data
     */
    public static class NPCData {
        private final String id;
        private final ArmorStand npcStand;
        private final List<ArmorStand> hologramLines;
        private final Location location;
        private final String displayName;

        public NPCData(String id, ArmorStand npcStand, List<ArmorStand> hologramLines, Location location, String displayName) {
            this.id = id;
            this.npcStand = npcStand;
            this.hologramLines = hologramLines;
            this.location = location;
            this.displayName = displayName;
        }

        public String getId() { return id; }
        public ArmorStand getNpcStand() { return npcStand; }
        public List<ArmorStand> getHologramLines() { return hologramLines; }
        public Location getLocation() { return location; }
        public String getDisplayName() { return displayName; }
    }
}
