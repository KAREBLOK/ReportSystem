package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.PortalAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PortalListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public PortalListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPortalUse(PlayerPortalEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null) {
                PortalAction.PortalType portalType = PortalAction.PortalType.NETHER;

                // Cause'a göre portal türünü belirle
                if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                    portalType = PortalAction.PortalType.NETHER;
                } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                    portalType = PortalAction.PortalType.END;
                } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
                    portalType = PortalAction.PortalType.END_GATEWAY;
                }

                PortalAction action = new PortalAction(
                        portalType,
                        from.getX(), from.getY(), from.getZ(),
                        from.getWorld().getName(),
                        to.getX(), to.getY(), to.getZ(),
                        to.getWorld().getName()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Portal used: " + portalType.name() +
                        " from " + from.getWorld().getName() + " to " + to.getWorld().getName());
            }
        }
    }
}
