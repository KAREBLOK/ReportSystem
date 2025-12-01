package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.FallingBlockAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FallingBlockListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public FallingBlockListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof FallingBlock)) return;

        FallingBlock fallingBlock = (FallingBlock) event.getEntity();

        // Yakındaki kaydedilen oyuncuları bul
        for (Player player : fallingBlock.getWorld().getNearbyPlayers(fallingBlock.getLocation(), 15)) {
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                FallingBlockAction action = new FallingBlockAction(
                        fallingBlock.getBlockData().getMaterial().name(),
                        fallingBlock.getLocation().getX(),
                        fallingBlock.getLocation().getY(),
                        fallingBlock.getLocation().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Falling block: " +
                        fallingBlock.getBlockData().getMaterial() + " at " + fallingBlock.getLocation());
                break; // Sadece en yakın oyuncu kaydeder
            }
        }
    }
}
