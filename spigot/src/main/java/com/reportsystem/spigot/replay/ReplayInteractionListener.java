package com.reportsystem.spigot.replay;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.common.replay.actions.ReplayAction;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PacketEvents listener for replay NPC interactions
 * Listens for UseEntity packets to detect right-clicks on fake NPCs
 */
public class ReplayInteractionListener extends PacketListenerAbstract {

    private final ReportSystemSpigot plugin;
    private final ReplayManager replayManager;

    public ReplayInteractionListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.replayManager = plugin.getReplayManager();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Only handle UseEntity packets (right-click on entity)
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        Player viewer = (Player) event.getPlayer();
        if (viewer == null) {
            return;
        }

        // Check if viewer is watching a replay
        ReplayPlayer replayPlayer = replayManager.getViewerReplay(viewer);
        if (replayPlayer == null) {
            return; // Not watching a replay
        }

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);

        // Only handle right-click (INTERACT_AT)
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT &&
            packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT) {
            return;
        }

        int clickedEntityId = packet.getEntityId();

        plugin.getLogger().info("[REPLAY-INTERACTION] Player " + viewer.getName() +
                " clicked entity ID: " + clickedEntityId);

        // Check if this is the main recorded player NPC
        if (clickedEntityId == replayPlayer.getNpcManager().getEntityId()) {
            plugin.getLogger().info("[REPLAY-INTERACTION] Clicked main NPC");
            Bukkit.getScheduler().runTask(plugin, () -> {
                showPlayerEquipment(viewer, replayPlayer, null);
            });
            return;
        }

        // Check if this is a nearby player NPC
        UUID nearbyPlayerUUID = replayPlayer.getNpcManager().getNearbyPlayerByEntityId(clickedEntityId);
        if (nearbyPlayerUUID != null) {
            plugin.getLogger().info("[REPLAY-INTERACTION] Clicked nearby player NPC: " + nearbyPlayerUUID);
            Bukkit.getScheduler().runTask(plugin, () -> {
                showPlayerEquipment(viewer, replayPlayer, nearbyPlayerUUID);
            });
        }
    }

    /**
     * Show equipment GUI for player (main or nearby)
     */
    private void showPlayerEquipment(Player viewer, ReplayPlayer replayPlayer, UUID nearbyPlayerUUID) {
        // Get player name
        String playerName = nearbyPlayerUUID == null ?
                replayPlayer.getReplay().getRecordedPlayer() :
                getPlayerNameFromActions(replayPlayer, nearbyPlayerUUID);

        // Get full inventory from replay actions
        Map<Integer, EquipmentAction.ItemData> inventory =
                getLatestInventory(replayPlayer, nearbyPlayerUUID);

        // Create and show full inventory GUI
        showFullInventoryGUI(viewer, playerName, inventory);
    }

    /**
     * Get player name from nearby player actions
     */
    private String getPlayerNameFromActions(ReplayPlayer replayPlayer, UUID playerUUID) {
        for (ReplayAction action : replayPlayer.getActions()) {
            if (action instanceof NearbyPlayerAction) {
                NearbyPlayerAction nearbyAction = (NearbyPlayerAction) action;
                if (nearbyAction.getPlayerUuid().equals(playerUUID)) {
                    return nearbyAction.getPlayerName();
                }
            }
        }
        return "Unknown";
    }

    /**
     * Get latest full inventory for player from replay actions
     */
    private Map<Integer, EquipmentAction.ItemData> getLatestInventory(
            ReplayPlayer replayPlayer, UUID nearbyPlayerUUID) {

        Map<Integer, EquipmentAction.ItemData> inventory = new HashMap<>();
        int currentIndex = replayPlayer.getCurrentActionIndex();

        // Look through actions up to current point
        for (int i = 0; i <= currentIndex && i < replayPlayer.getActions().size(); i++) {
            ReplayAction action = replayPlayer.getActions().get(i);

            // For main player - check InventorySnapshotAction
            if (nearbyPlayerUUID == null && action instanceof com.reportsystem.common.replay.actions.InventorySnapshotAction) {
                com.reportsystem.common.replay.actions.InventorySnapshotAction inventoryAction =
                        (com.reportsystem.common.replay.actions.InventorySnapshotAction) action;

                // Convert InventorySnapshotAction.ItemData to EquipmentAction.ItemData
                for (Map.Entry<Integer, com.reportsystem.common.replay.actions.InventorySnapshotAction.ItemData> entry :
                        inventoryAction.getInventoryContents().entrySet()) {
                    com.reportsystem.common.replay.actions.InventorySnapshotAction.ItemData item = entry.getValue();
                    inventory.put(entry.getKey(), new EquipmentAction.ItemData(
                            item.getMaterial(), item.getAmount(), item.getDurability(), item.getDisplayName(),
                            item.getLore(), item.getEnchantments(), item.getItemStackData(),
                            item.getCustomModelData(), item.isUnbreakable()
                    ));
                }
            }

            // For nearby players - check NearbyPlayerAction with inventory
            if (nearbyPlayerUUID != null && action instanceof NearbyPlayerAction) {
                NearbyPlayerAction nearbyAction = (NearbyPlayerAction) action;
                if (nearbyAction.getPlayerUuid().equals(nearbyPlayerUUID) &&
                        nearbyAction.getInventory() != null && !nearbyAction.getInventory().isEmpty()) {
                    // Use nearby player inventory
                    inventory.putAll(nearbyAction.getInventory());
                }
            }
        }

        return inventory;
    }

    /**
     * Show full inventory GUI (54 slots - 6 rows)
     */
    private void showFullInventoryGUI(Player viewer, String playerName,
                                       Map<Integer, EquipmentAction.ItemData> inventory) {

        // Create inventory (54 slots - double chest)
        String title = plugin.getMessageManager().colorize("&8" + playerName + " &7- Envanter");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill main inventory (slots 0-35 → GUI slots 9-44)
        for (int i = 0; i < 36; i++) {
            EquipmentAction.ItemData itemData = inventory.get(i);
            if (itemData != null) {
                ItemStack item = convertItemData(itemData);
                inv.setItem(i + 9, item);
            }
        }

        // Armor slots in top row
        // Helmet (slot 39 → GUI slot 3)
        EquipmentAction.ItemData helmet = inventory.get(39);
        if (helmet != null) inv.setItem(3, convertItemData(helmet));

        // Chestplate (slot 38 → GUI slot 2)
        EquipmentAction.ItemData chestplate = inventory.get(38);
        if (chestplate != null) inv.setItem(2, convertItemData(chestplate));

        // Leggings (slot 37 → GUI slot 1)
        EquipmentAction.ItemData leggings = inventory.get(37);
        if (leggings != null) inv.setItem(1, convertItemData(leggings));

        // Boots (slot 36 → GUI slot 0)
        EquipmentAction.ItemData boots = inventory.get(36);
        if (boots != null) inv.setItem(0, convertItemData(boots));

        // Offhand (slot 40 → GUI slot 5)
        EquipmentAction.ItemData offhand = inventory.get(40);
        if (offhand != null) inv.setItem(5, convertItemData(offhand));

        // Open inventory
        viewer.openInventory(inv);

        plugin.getLogger().info("[REPLAY-INTERACTION] Showing full inventory for " + playerName + " to " + viewer.getName() +
                " - Total items: " + inventory.size());
    }

    /**
     * Convert ItemData to Bukkit ItemStack
     */
    private ItemStack convertItemData(EquipmentAction.ItemData itemData) {
        try {
            Material material = Material.valueOf(itemData.getMaterial());
            ItemStack item = new ItemStack(material, itemData.getAmount());

            // Set durability if needed
            if (itemData.getDurability() > 0) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                    ((org.bukkit.inventory.meta.Damageable) meta).setDamage(itemData.getDurability());
                    item.setItemMeta(meta);
                }
            }

            // Set display name and lore
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (itemData.getDisplayName() != null) {
                    meta.setDisplayName(itemData.getDisplayName());
                }
                if (itemData.getLore() != null) {
                    meta.setLore(itemData.getLore());
                }
                if (itemData.isUnbreakable()) {
                    meta.setUnbreakable(true);
                }
                item.setItemMeta(meta);
            }

            // Add enchantments
            if (itemData.getEnchantments() != null) {
                for (Map.Entry<String, Integer> entry : itemData.getEnchantments().entrySet()) {
                    try {
                        org.bukkit.enchantments.Enchantment enchant =
                                org.bukkit.enchantments.Enchantment.getByName(entry.getKey());
                        if (enchant != null) {
                            item.addUnsafeEnchantment(enchant, entry.getValue());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[REPLAY-INTERACTION] Failed to add enchantment: " + entry.getKey());
                    }
                }
            }

            return item;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[REPLAY-INTERACTION] Unknown material: " + itemData.getMaterial());
            return null;
        }
    }
}
