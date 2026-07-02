package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.ContainerAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;

public class ContainerListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public ContainerListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ContainerAction.ContainerType containerType = getContainerType(event.getInventory().getType());

            if (containerType != null && event.getInventory().getLocation() != null) {
                Block block = event.getInventory().getLocation().getBlock();

                ContainerAction action = new ContainerAction(
                        containerType,
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        true // opened
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Container opened: " + containerType.name());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onContainerClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ContainerAction.ContainerType containerType = getContainerType(event.getInventory().getType());

            if (containerType != null && event.getInventory().getLocation() != null) {
                Block block = event.getInventory().getLocation().getBlock();

                ContainerAction action = new ContainerAction(
                        containerType,
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        false // closed
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Container closed: " + containerType.name());
            }
        }
    }

    private ContainerAction.ContainerType getContainerType(InventoryType type) {
        switch (type) {
            case CHEST:
                return ContainerAction.ContainerType.CHEST;
            case ENDER_CHEST:
                return ContainerAction.ContainerType.ENDER_CHEST;
            case SHULKER_BOX:
                return ContainerAction.ContainerType.SHULKER_BOX;
            case BARREL:
                return ContainerAction.ContainerType.BARREL;
            case HOPPER:
                return ContainerAction.ContainerType.HOPPER;
            case FURNACE:
                return ContainerAction.ContainerType.FURNACE;
            case DISPENSER:
                return ContainerAction.ContainerType.DISPENSER;
            case DROPPER:
                return ContainerAction.ContainerType.DROPPER;
            default:
                return null;
        }
    }
}
