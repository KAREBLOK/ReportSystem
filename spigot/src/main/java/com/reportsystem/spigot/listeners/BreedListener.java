package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.BreedAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BreedListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BreedListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;

        if (event.getBreeder() instanceof Player) {
            Player player = (Player) event.getBreeder();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null && event.getEntity() != null) {
                BreedAction action = new BreedAction(
                        event.getEntity().getType().name(),
                        event.getEntity().getLocation().getX(),
                        event.getEntity().getLocation().getY(),
                        event.getEntity().getLocation().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Entity bred: " +
                        event.getEntity().getType() + " at " + event.getEntity().getLocation());
            }
        }
    }
}
