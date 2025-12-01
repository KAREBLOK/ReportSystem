package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.EntityDyeAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SheepDyeWoolEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityDyeListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public EntityDyeListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSheepDye(SheepDyeWoolEvent event) {
        if (event.isCancelled()) return;

        if (event.getPlayer() != null) {
            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Sheep sheep = event.getEntity();

                EntityDyeAction action = new EntityDyeAction(
                        sheep.getUniqueId(),
                        "SHEEP",
                        event.getColor().name(),
                        sheep.getLocation().getX(),
                        sheep.getLocation().getY(),
                        sheep.getLocation().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Sheep dyed: " +
                        sheep.getColor().name() + " -> " + event.getColor().name() +
                        " at " + sheep.getLocation());
            }
        }
    }
}
