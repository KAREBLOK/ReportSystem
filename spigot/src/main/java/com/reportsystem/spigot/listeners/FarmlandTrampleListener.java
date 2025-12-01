package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.FarmingAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class FarmlandTrampleListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public FarmlandTrampleListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getClickedBlock();

            FarmingAction action = new FarmingAction(
                    FarmingAction.FarmingType.FARMLAND_TRAMPLE,
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    Material.FARMLAND.name()
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Farmland trampled at: " + block.getLocation());
        }
    }
}
