package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.AnimationAction;
import com.reportsystem.common.replay.actions.InteractEntityAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityInteractListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public EntityInteractListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity target = event.getRightClicked();
            InteractEntityAction.InteractionType type = InteractEntityAction.InteractionType.RIGHT_CLICK;

            // Etkileşim tipini belirle
            ItemStack item = player.getInventory().getItemInMainHand();

            // Koyun kırpma kontrolü
            if (target instanceof Sheep && item != null && item.getType() == Material.SHEARS) {
                Sheep sheep = (Sheep) target;
                if (!sheep.isSheared()) {
                    type = InteractEntityAction.InteractionType.SHEAR;
                    plugin.getLogger().info("[RECORDING-DEBUG] Shearing sheep detected in interact event");
                }
            } else if (target instanceof Villager) {
                type = InteractEntityAction.InteractionType.TRADE;
            } else if (target instanceof Animals) {
                if (item != null && ((Animals) target).isBreedItem(item)) {
                    type = InteractEntityAction.InteractionType.BREED;
                } else if (target instanceof Tameable && !((Tameable) target).isTamed()) {
                    type = InteractEntityAction.InteractionType.TAME;
                } else {
                    type = InteractEntityAction.InteractionType.FEED;
                }
            } else if (target instanceof Cow && item != null && item.getType() == Material.BUCKET) {
                type = InteractEntityAction.InteractionType.MILK;
            }

            // Kullanılan item'ın tipini kaydet
            String itemUsed = (item != null && item.getType() != Material.AIR) ? item.getType().name() : null;

            InteractEntityAction action = new InteractEntityAction(
                    type,
                    target.getUniqueId(),
                    target.getType().name(),
                    target.getLocation().getX(),
                    target.getLocation().getY(),
                    target.getLocation().getZ(),
                    itemUsed
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Entity interaction: " + type + " with " + target.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Entity target = event.getEntity();

                InteractEntityAction action = new InteractEntityAction(
                        InteractEntityAction.InteractionType.LEFT_CLICK,
                        target.getUniqueId(),
                        target.getType().name(),
                        target.getLocation().getX(),
                        target.getLocation().getY(),
                        target.getLocation().getZ()
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Entity attack: " + target.getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShear(PlayerShearEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity target = event.getEntity();

            // Önce shear animasyonunu kaydet
            session.addAction(new AnimationAction(AnimationAction.AnimationType.SWING_MAIN_HAND));

            // Entity type ve ekstra bilgi
            String entityTypeData = target.getType().name();

            // Koyun ise renk bilgisini ekle
            if (target instanceof Sheep) {
                Sheep sheep = (Sheep) target;
                entityTypeData = target.getType().name() + ":" + sheep.getColor().name();
                plugin.getLogger().info("[RECORDING-DEBUG] Sheep color: " + sheep.getColor().name());
            }

            // Interaction'ı kaydet
            InteractEntityAction action = new InteractEntityAction(
                    InteractEntityAction.InteractionType.SHEAR,
                    target.getUniqueId(),
                    entityTypeData,
                    target.getLocation().getX(),
                    target.getLocation().getY(),
                    target.getLocation().getZ()
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Entity shear: " + entityTypeData +
                    " at " + target.getLocation());
        }
    }
}