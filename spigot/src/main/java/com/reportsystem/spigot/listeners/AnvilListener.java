package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.AnvilAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class AnvilListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public AnvilListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnvilUse(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        Player player = (Player) event.getWhoClicked();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null && event.getSlot() == 2) { // Result slot
            AnvilInventory anvil = (AnvilInventory) event.getInventory();
            ItemStack result = event.getCurrentItem();

            if (result != null && result.getType() != org.bukkit.Material.AIR) {
                ItemStack firstItem = anvil.getItem(0); // Input item
                ItemStack secondItem = anvil.getItem(1); // Material/book

                String itemBefore = firstItem != null ? ItemSerializer.serializeItemStack(firstItem) : null;
                String itemAfter = ItemSerializer.serializeItemStack(result);
                int repairCost = anvil.getRepairCost();

                // Determine action type
                AnvilAction.AnvilActionType actionType;
                if (anvil.getRenameText() != null && !anvil.getRenameText().isEmpty()) {
                    actionType = AnvilAction.AnvilActionType.RENAME;
                } else if (secondItem != null && secondItem.getType() != org.bukkit.Material.AIR) {
                    actionType = AnvilAction.AnvilActionType.COMBINE;
                } else {
                    actionType = AnvilAction.AnvilActionType.REPAIR;
                }

                AnvilAction action = new AnvilAction(
                        actionType,
                        itemBefore,
                        itemAfter,
                        repairCost
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Anvil " + actionType.name().toLowerCase() +
                        ": " + result.getType().name() + " (cost: " + repairCost + ")");
            }
        }
    }
}
