package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.InteractEntityAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class LeashListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public LeashListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeash(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity entity = event.getEntity();

            InteractEntityAction action = new InteractEntityAction(
                    InteractEntityAction.InteractionType.RIGHT_CLICK, // Yeni tip ekleyebiliriz: LEASH
                    entity.getUniqueId(),
                    entity.getType().name() + "_LEASHED",
                    entity.getLocation().getX(),
                    entity.getLocation().getY(),
                    entity.getLocation().getZ()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Entity leashed: " + entity.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity entity = event.getEntity();

            InteractEntityAction action = new InteractEntityAction(
                    InteractEntityAction.InteractionType.RIGHT_CLICK,
                    entity.getUniqueId(),
                    entity.getType().name() + "_UNLEASHED",
                    entity.getLocation().getX(),
                    entity.getLocation().getY(),
                    entity.getLocation().getZ()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Entity unleashed: " + entity.getType());
        }
    }
}