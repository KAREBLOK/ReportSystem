package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.BlockIgniteAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockIgniteListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BlockIgniteListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getBlock();
            BlockIgniteAction.IgniteType igniteType = null;

            switch (event.getCause()) {
                case FLINT_AND_STEEL:
                    igniteType = BlockIgniteAction.IgniteType.FLINT_AND_STEEL;
                    break;
                case LAVA:
                    igniteType = BlockIgniteAction.IgniteType.LAVA;
                    break;
                case FIREBALL:
                    igniteType = BlockIgniteAction.IgniteType.FIRE_CHARGE;
                    break;
                case LIGHTNING:
                    igniteType = BlockIgniteAction.IgniteType.LIGHTNING;
                    break;
                case SPREAD:
                    igniteType = BlockIgniteAction.IgniteType.SPREAD;
                    break;
                default:
                    igniteType = BlockIgniteAction.IgniteType.FLINT_AND_STEEL;
            }

            BlockIgniteAction action = new BlockIgniteAction(
                    igniteType,
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Block ignited: " + igniteType.name() +
                    " at " + block.getLocation());
        }
    }
}
