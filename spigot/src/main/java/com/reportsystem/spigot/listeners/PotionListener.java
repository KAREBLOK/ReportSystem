package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.PotionEffectAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public class PotionListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public PotionListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            PotionEffectAction.ActionType actionType = null;
            PotionEffect effect = null;

            switch (event.getAction()) {
                case ADDED:
                    actionType = PotionEffectAction.ActionType.ADD;
                    effect = event.getNewEffect();
                    break;
                case REMOVED:
                    actionType = PotionEffectAction.ActionType.REMOVE;
                    effect = event.getOldEffect();
                    break;
                case CLEARED:
                    actionType = PotionEffectAction.ActionType.CLEAR_ALL;
                    break;
            }

            if (actionType != null) {
                PotionEffectAction action;

                if (effect != null) {
                    action = new PotionEffectAction(
                            actionType,
                            effect.getType().getName(),
                            effect.getAmplifier(),
                            effect.getDuration(),
                            effect.isAmbient(),
                            effect.hasParticles()
                    );
                } else {
                    // CLEAR_ALL durumu
                    action = new PotionEffectAction(
                            actionType,
                            "",
                            0,
                            0,
                            false,
                            false
                    );
                }

                session.addAction(action);
                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Potion effect: " + actionType);
            }
        }
    }
}