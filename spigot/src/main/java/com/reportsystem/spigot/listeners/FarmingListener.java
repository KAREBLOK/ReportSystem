package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.FarmingAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.java.JavaPlugin;

public class FarmingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public FarmingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoneMeal(BlockFertilizeEvent event) {
        try {
            if (event.isCancelled()) return;

            Player player = event.getPlayer();
            if (player == null) return;

            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Block block = event.getBlock();

                FarmingAction action = new FarmingAction(
                        FarmingAction.FarmingType.BONE_MEAL,
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        block.getType().name()
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Bone meal used on: " + block.getType().name());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[FarmingListener] Error in onBoneMeal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHoeUse(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Material tool = player.getInventory().getItemInMainHand().getType();

                // Hoe kontrolü
                if (tool.name().contains("HOE")) {
                    Block block = event.getClickedBlock();

                    // Dirt/grass -> farmland
                    if (block.getType() == Material.DIRT || block.getType() == Material.GRASS_BLOCK) {
                        FarmingAction action = new FarmingAction(
                                FarmingAction.FarmingType.HOE_TILL,
                                block.getX(),
                                block.getY(),
                                block.getZ(),
                                block.getType().name()
                        );
                        session.addAction(action);

                        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Hoe used at: " + block.getLocation());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[FarmingListener] Error in onHoeUse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropHarvest(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Block block = event.getClickedBlock();
                Material type = block.getType();

                // Hasat edilebilir bitkiler
                if (type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES ||
                    type == Material.BEETROOTS || type == Material.NETHER_WART ||
                    type == Material.COCOA || type == Material.SWEET_BERRY_BUSH) {

                    FarmingAction action = new FarmingAction(
                            FarmingAction.FarmingType.CROP_HARVEST,
                            block.getX(),
                            block.getY(),
                            block.getZ(),
                            type.name()
                    );
                    session.addAction(action);

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Crop harvested: " + type.name());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[FarmingListener] Error in onCropHarvest: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
