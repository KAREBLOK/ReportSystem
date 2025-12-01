package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.BucketAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BucketListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BucketListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            BucketAction.BucketType bucketType = getBucketType(event.getBucket());

            if (bucketType != null) {
                BucketAction action = new BucketAction(
                        BucketAction.ActionType.EMPTY,
                        bucketType,
                        event.getBlock().getX(),
                        event.getBlock().getY(),
                        event.getBlock().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Bucket emptied: " + bucketType +
                        " at " + event.getBlock().getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            BucketAction.BucketType bucketType = getBucketType(event.getItemStack().getType());

            if (bucketType != null) {
                BucketAction action = new BucketAction(
                        BucketAction.ActionType.FILL,
                        bucketType,
                        event.getBlock().getX(),
                        event.getBlock().getY(),
                        event.getBlock().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Bucket filled: " + bucketType +
                        " at " + event.getBlock().getLocation());
            }
        }
    }

    private BucketAction.BucketType getBucketType(Material material) {
        switch (material) {
            case WATER_BUCKET:
                return BucketAction.BucketType.WATER;
            case LAVA_BUCKET:
                return BucketAction.BucketType.LAVA;
            case MILK_BUCKET:
                return BucketAction.BucketType.MILK;
            case POWDER_SNOW_BUCKET:
                return BucketAction.BucketType.POWDER_SNOW;
            case PUFFERFISH_BUCKET:
            case SALMON_BUCKET:
            case COD_BUCKET:
            case TROPICAL_FISH_BUCKET:
            case AXOLOTL_BUCKET:
            case TADPOLE_BUCKET:
                return BucketAction.BucketType.FISH;
            default:
                return null;
        }
    }
}
