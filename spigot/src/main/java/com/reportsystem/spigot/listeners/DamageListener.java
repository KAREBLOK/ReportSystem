package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.DamageAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DamageListener implements Listener {

    private final RecordingManager recordingManager;

    public DamageListener(RecordingManager recordingManager) {
        this.recordingManager = recordingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            DamageAction.DamageType damageType = DamageAction.DamageType.OTHER;

            // Hasar tipini belirle (tüm türler)
            switch (event.getCause()) {
                case FALL:
                    damageType = DamageAction.DamageType.FALL;
                    break;
                case ENTITY_ATTACK:
                case ENTITY_SWEEP_ATTACK:
                    damageType = DamageAction.DamageType.ATTACK;
                    break;
                case FIRE:
                case FIRE_TICK:
                case LAVA:
                    damageType = DamageAction.DamageType.FIRE;
                    break;
                case DROWNING:
                    damageType = DamageAction.DamageType.DROWNING;
                    break;
                case CONTACT:
                    damageType = DamageAction.DamageType.CONTACT; // Kaktüs, sweet berry
                    break;
                case HOT_FLOOR:
                    damageType = DamageAction.DamageType.HOT_FLOOR; // Magma block
                    break;
                case CAMPFIRE:
                    damageType = DamageAction.DamageType.CAMPFIRE;
                    break;
                case WITHER:
                    damageType = DamageAction.DamageType.WITHER; // Wither rose/effect
                    break;
                case POISON:
                    damageType = DamageAction.DamageType.POISON;
                    break;
                case STARVATION:
                    damageType = DamageAction.DamageType.STARVATION;
                    break;
                case SUFFOCATION:
                    damageType = DamageAction.DamageType.SUFFOCATION;
                    break;
                case VOID:
                    damageType = DamageAction.DamageType.VOID;
                    break;
                case FLY_INTO_WALL:
                    damageType = DamageAction.DamageType.FLY_INTO_WALL; // Elytra
                    break;
                case CRAMMING:
                    damageType = DamageAction.DamageType.CRAMMING;
                    break;
                case THORNS:
                    damageType = DamageAction.DamageType.THORNS;
                    break;
                case MAGIC:
                    damageType = DamageAction.DamageType.MAGIC; // Instant damage
                    break;
                case BLOCK_EXPLOSION:
                case ENTITY_EXPLOSION:
                    damageType = DamageAction.DamageType.EXPLOSION;
                    break;
                default:
                    damageType = DamageAction.DamageType.OTHER;
            }

            // Hasar action'ını kaydet
            session.addAction(new DamageAction(damageType, event.getDamage(), true));
        }
    }
}