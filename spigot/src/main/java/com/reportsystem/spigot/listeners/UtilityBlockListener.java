package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.UtilityBlockAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class UtilityBlockListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public UtilityBlockListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUtilityUse(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            InventoryType invType = event.getInventory().getType();
            UtilityBlockAction.UtilityType utilityType = null;
            Block block = null;

            // Inventory türüne göre utility type belirle
            switch (invType) {
                case GRINDSTONE:
                    utilityType = UtilityBlockAction.UtilityType.GRINDSTONE;
                    break;
                case SMITHING:
                    utilityType = UtilityBlockAction.UtilityType.SMITHING;
                    break;
                case STONECUTTER:
                    utilityType = UtilityBlockAction.UtilityType.STONECUTTER;
                    break;
                case LOOM:
                    utilityType = UtilityBlockAction.UtilityType.LOOM;
                    break;
                case CARTOGRAPHY:
                    utilityType = UtilityBlockAction.UtilityType.CARTOGRAPHY;
                    break;
                default:
                    return; // İlgili değil
            }

            // Result slot tıklanmış mı kontrol et
            if (event.getSlot() == 2 || event.getSlot() == 3) { // Genelde result slot
                ItemStack result = event.getCurrentItem();

                if (result != null && result.getType() != org.bukkit.Material.AIR) {
                    ItemStack input = event.getInventory().getItem(0);
                    String itemBefore = input != null ? ItemSerializer.serializeItemStack(input) : null;
                    String itemAfter = ItemSerializer.serializeItemStack(result);

                    // Blok konumunu al (inventory holder'dan)
                    if (event.getInventory().getLocation() != null) {
                        block = event.getInventory().getLocation().getBlock();

                        UtilityBlockAction action = new UtilityBlockAction(
                                utilityType,
                                block.getX(),
                                block.getY(),
                                block.getZ(),
                                itemBefore,
                                itemAfter
                        );
                        session.addAction(action);

                        plugin.getLogger().info("[RECORDING-DEBUG] Utility block used: " + utilityType.name() +
                                " at " + block.getLocation());
                    }
                }
            }
        }
    }
}
