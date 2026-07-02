package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.AnimationAction;
import com.reportsystem.common.replay.actions.EntityStateAction;
import com.reportsystem.common.replay.actions.PlayerStateAction;
import com.reportsystem.common.replay.actions.PoseAction;
import com.reportsystem.common.replay.actions.VelocityAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStateListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    // Swimming state tracking
    private final Map<UUID, Boolean> lastSwimmingState = new HashMap<>();

    public PlayerStateListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            PlayerStateAction action = new PlayerStateAction(
                    PlayerStateAction.StateType.SNEAK,
                    event.isSneaking()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sneak: " + event.isSneaking());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            PlayerStateAction action = new PlayerStateAction(
                    PlayerStateAction.StateType.SPRINT,
                    event.isSprinting()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Sprint: " + event.isSprinting());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            PlayerStateAction action = new PlayerStateAction(
                    PlayerStateAction.StateType.GLIDE,
                    event.isGliding()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Glide: " + event.isGliding());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            // Swimming state kontrolü
            boolean isSwimming = player.isSwimming();
            Boolean lastState = lastSwimmingState.get(player.getUniqueId());

            // State değiştiyse kaydet
            if (lastState == null || lastState != isSwimming) {
                lastSwimmingState.put(player.getUniqueId(), isSwimming);

                // PoseAction olarak kaydet (SWIMMING pose)
                PoseAction poseAction = new PoseAction(
                        isSwimming ? PoseAction.PoseType.SWIMMING : PoseAction.PoseType.STANDING
                );
                session.addAction(poseAction);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Swimming state changed: " + isSwimming);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            // Kol sallama animasyonu
            AnimationAction action = new AnimationAction(AnimationAction.AnimationType.SWING_MAIN_HAND);
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Animation: SWING_MAIN_HAND");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            EntityStateAction action = new EntityStateAction(
                    EntityStateAction.StateType.FLYING,
                    event.isFlying()
            );
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Flight: " + event.isFlying());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Vector velocity = event.getVelocity();

            // Sadece anlamlı velocity değişikliklerini kaydet (knockback, patlama, vb.)
            if (velocity.lengthSquared() > 0.01) { // Minimum threshold
                VelocityAction action = new VelocityAction(
                        velocity.getX(),
                        velocity.getY(),
                        velocity.getZ()
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Velocity: " +
                        String.format("%.2f, %.2f, %.2f", velocity.getX(), velocity.getY(), velocity.getZ()));
            }
        }
    }
}
