package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.LocationAction;
import com.reportsystem.common.replay.actions.TeleportAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TeleportListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public TeleportListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            TeleportAction.TeleportCause cause = TeleportAction.TeleportCause.UNKNOWN;

            switch (event.getCause()) {
                case ENDER_PEARL:
                    cause = TeleportAction.TeleportCause.ENDER_PEARL;
                    break;
                case CHORUS_FRUIT:
                    cause = TeleportAction.TeleportCause.CHORUS_FRUIT;
                    break;
                case COMMAND:
                    cause = TeleportAction.TeleportCause.COMMAND;
                    break;
                case PLUGIN:
                    cause = TeleportAction.TeleportCause.PLUGIN;
                    break;
            }

            // World isimleri
            String fromWorldName = event.getFrom().getWorld().getName();
            String toWorldName = event.getTo().getWorld().getName();

            TeleportAction action = new TeleportAction(
                    event.getFrom().getX(),
                    event.getFrom().getY(),
                    event.getFrom().getZ(),
                    fromWorldName,
                    event.getTo().getX(),
                    event.getTo().getY(),
                    event.getTo().getZ(),
                    toWorldName,
                    cause
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Teleport: " + cause +
                    " from " + fromWorldName + " to " + toWorldName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Location respawnLoc = event.getRespawnLocation();

            // Respawn'ı bir teleport olarak kaydet
            TeleportAction action = new TeleportAction(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getWorld().getName(),
                    respawnLoc.getX(),
                    respawnLoc.getY(),
                    respawnLoc.getZ(),
                    respawnLoc.getWorld().getName(),
                    TeleportAction.TeleportCause.PLUGIN // Respawn için PLUGIN cause kullan
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Respawn at: " +
                    respawnLoc.getWorld().getName() + " " +
                    String.format("%.1f, %.1f, %.1f", respawnLoc.getX(), respawnLoc.getY(), respawnLoc.getZ()) +
                    " (Bed: " + event.isBedSpawn() + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Location currentLoc = player.getLocation();

            // Dünya değişikliğini LocationAction ile kaydet
            LocationAction action = new LocationAction(
                    currentLoc.getX(),
                    currentLoc.getY(),
                    currentLoc.getZ(),
                    currentLoc.getYaw(),
                    currentLoc.getPitch(),
                    player.isOnGround()
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] World changed: " +
                    event.getFrom().getName() + " -> " + currentLoc.getWorld().getName());
        }
    }
}