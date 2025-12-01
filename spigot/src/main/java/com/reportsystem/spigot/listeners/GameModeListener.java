package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.GameModeAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GameModeListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public GameModeListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            GameModeAction.GameMode gameMode = convertGameMode(event.getNewGameMode());

            if (gameMode != null) {
                GameModeAction action = new GameModeAction(gameMode);
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] GameMode changed to: " + gameMode);
            }
        }
    }

    private GameModeAction.GameMode convertGameMode(GameMode bukkitGameMode) {
        switch (bukkitGameMode) {
            case SURVIVAL:
                return GameModeAction.GameMode.SURVIVAL;
            case CREATIVE:
                return GameModeAction.GameMode.CREATIVE;
            case ADVENTURE:
                return GameModeAction.GameMode.ADVENTURE;
            case SPECTATOR:
                return GameModeAction.GameMode.SPECTATOR;
            default:
                return null;
        }
    }
}