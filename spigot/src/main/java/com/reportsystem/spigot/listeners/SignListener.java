package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.SignAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SignListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public SignListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getBlock();
            String[] lines = event.getLines();

            SignAction action = new SignAction(
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    lines,
                    block.getType().name()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sign text: " +
                    String.join(" | ", lines) +
                    " at " + block.getLocation());
        }
    }
}
