package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.BedAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BedListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BedListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!event.isCancelled()) {
            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Location bedLoc = event.getBed().getLocation();
                BedAction action = new BedAction(
                        BedAction.ActionType.ENTER_BED,
                        bedLoc.getBlockX(),
                        bedLoc.getBlockY(),
                        bedLoc.getBlockZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Player entered bed at: " + bedLoc);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Location bedLoc = event.getBed().getLocation();
            BedAction action = new BedAction(
                    BedAction.ActionType.LEAVE_BED,
                    bedLoc.getBlockX(),
                    bedLoc.getBlockY(),
                    bedLoc.getBlockZ()
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Player left bed");
        }
    }
}