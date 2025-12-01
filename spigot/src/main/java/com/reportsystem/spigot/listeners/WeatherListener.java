package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.WeatherAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class WeatherListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public WeatherListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.isCancelled()) return;

        // Tüm kayıt edilen oyuncular için hava durumunu kaydet
        for (UUID playerUuid : recordingManager.getActiveRecordings().keySet()) {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.getWorld().equals(event.getWorld())) {
                RecordingSession session = recordingManager.getSession(playerUuid);

                if (session != null) {
                    WeatherAction.WeatherType weatherType;

                    if (event.getWorld().hasStorm()) {
                        if (event.getWorld().isThundering()) {
                            weatherType = WeatherAction.WeatherType.THUNDER;
                        } else {
                            weatherType = WeatherAction.WeatherType.RAIN;
                        }
                    } else {
                        weatherType = WeatherAction.WeatherType.CLEAR;
                    }

                    WeatherAction action = new WeatherAction(weatherType, false);
                    session.addAction(action);

                    plugin.getLogger().info("[RECORDING-DEBUG] Weather changed to: " + weatherType);
                }
            }
        }
    }
}