package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.NoteBlockAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class NoteBlockListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public NoteBlockListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNotePlay(NotePlayEvent event) {
        try {
            if (event.isCancelled()) return;

            // Yakındaki kaydedilen oyuncuları bul
            for (Player player : event.getBlock().getWorld().getNearbyPlayers(event.getBlock().getLocation(), 20)) {
                RecordingSession session = recordingManager.getSession(player.getUniqueId());

                if (session != null) {
                    NoteBlockAction action = new NoteBlockAction(
                            event.getBlock().getX(),
                            event.getBlock().getY(),
                            event.getBlock().getZ(),
                            event.getInstrument().name(),
                            event.getNote().getId()
                    );
                    session.addAction(action);

                    plugin.getLogger().info("[RECORDING-DEBUG] Note block: " + event.getInstrument() +
                            " note " + event.getNote().getId() + " at " + event.getBlock().getLocation());
                    break; // Sadece en yakın oyuncu kaydeder
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[NoteBlockListener] Error in onNotePlay: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
