package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.SoundAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class SoundListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public SoundListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    // Kapı sesleri
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDoorToggle(PlayerInteractEvent event) {
        if (event.isCancelled() || event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null && event.getClickedBlock().getType().name().contains("DOOR")) {
            recordSound(session, Sound.BLOCK_WOODEN_DOOR_OPEN, event.getClickedBlock().getLocation());
        }
    }

    // Patlama sesleri
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplosion(EntityExplodeEvent event) {
        if (event.isCancelled()) return;

        // Yakındaki kayıt edilen oyuncular için ses kaydet
        for (UUID playerUuid : recordingManager.getActiveRecordings().keySet()) {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.getLocation().distance(event.getLocation()) < 100) {
                RecordingSession session = recordingManager.getSession(playerUuid);
                if (session != null) {
                    recordSound(session, Sound.ENTITY_GENERIC_EXPLODE, event.getLocation());
                }
            }
        }
    }

    // Diğer önemli sesler buraya eklenebilir...

    private void recordSound(RecordingSession session, Sound sound, org.bukkit.Location location) {
        SoundAction action = new SoundAction(
                sound.name(),
                location.getX(),
                location.getY(),
                location.getZ(),
                1.0f,
                1.0f
        );
        session.addAction(action);

        plugin.getLogger().info("[RECORDING-DEBUG] Sound recorded: " + sound.name());
    }
}