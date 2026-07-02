package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.RedstoneAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.DaylightDetector;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class RedstoneListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public RedstoneListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstoneInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getClickedBlock();
            Material type = block.getType();

            RedstoneAction.RedstoneType redstoneType = null;
            int delay = 0;
            String mode = null;

            if (type.name().contains("REPEATER")) {
                redstoneType = RedstoneAction.RedstoneType.REPEATER_DELAY;
                Repeater repeater = (Repeater) block.getBlockData();
                delay = repeater.getDelay(); // 1-4
            } else if (type.name().contains("COMPARATOR")) {
                redstoneType = RedstoneAction.RedstoneType.COMPARATOR_MODE;
                Comparator comparator = (Comparator) block.getBlockData();
                mode = comparator.getMode().name(); // COMPARE or SUBTRACT
            } else if (type.name().contains("DAYLIGHT_DETECTOR")) {
                redstoneType = RedstoneAction.RedstoneType.DAYLIGHT_MODE;
                DaylightDetector detector = (DaylightDetector) block.getBlockData();
                mode = detector.isInverted() ? "NIGHT" : "DAY";
            } else {
                return;
            }

            RedstoneAction action = new RedstoneAction(
                    redstoneType,
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    delay,
                    mode
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Redstone: " + redstoneType.name() +
                    " delay=" + delay + " mode=" + mode);
        }
    }
}
