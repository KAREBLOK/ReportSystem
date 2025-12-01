package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.HealthAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HealthListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public HealthListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            // Hasar sonrası can durumunu kaydet
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordHealthState(player, session);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordHealthState(player, session);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recordHealthState(player, session);
            }, 1L);
        }
    }

    private void recordHealthState(Player player, RecordingSession session) {
        HealthAction action = new HealthAction(
                player.getHealth(),
                player.getMaxHealth(),
                player.getFoodLevel(),
                player.getSaturation()
        );
        session.addAction(action);

        plugin.getLogger().info("[RECORDING-DEBUG] Health state: " + player.getHealth() + "/" + player.getMaxHealth());
    }
}