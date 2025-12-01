package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.EntityCommandAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class EntityCommandListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public EntityCommandListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityCommand(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Entity entity = event.getRightClicked();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            EntityCommandAction.CommandType commandType = null;
            String itemData = null;

            // Kedi/Kurt oturma/kalkma
            if (entity instanceof Sittable) {
                Sittable sittable = (Sittable) entity;
                commandType = sittable.isSitting() ? EntityCommandAction.CommandType.SIT : EntityCommandAction.CommandType.STAND;
            }
            // Papağan omza çıkma
            else if (entity instanceof Parrot) {
                // Papağan omza çıkma event'i ayrıdır, burada sadece omza çıkma tespiti
                commandType = EntityCommandAction.CommandType.PARROT_SHOULDER;
            }
            // At/Domuz zırh/eyer
            else if (entity instanceof Horse && itemInHand != null) {
                if (itemInHand.getType().name().contains("HORSE_ARMOR")) {
                    commandType = EntityCommandAction.CommandType.HORSE_ARMOR;
                    itemData = ItemSerializer.serializeItemStack(itemInHand);
                } else if (itemInHand.getType() == Material.SADDLE) {
                    commandType = EntityCommandAction.CommandType.HORSE_SADDLE;
                    itemData = ItemSerializer.serializeItemStack(itemInHand);
                }
            }
            // Domuz eyeri
            else if (entity instanceof Pig && itemInHand != null && itemInHand.getType() == Material.SADDLE) {
                commandType = EntityCommandAction.CommandType.PIG_SADDLE;
                itemData = ItemSerializer.serializeItemStack(itemInHand);
            }
            // Lama halı
            else if (entity instanceof Llama && itemInHand != null && itemInHand.getType().name().contains("CARPET")) {
                commandType = EntityCommandAction.CommandType.LLAMA_CARPET;
                itemData = ItemSerializer.serializeItemStack(itemInHand);
            }

            if (commandType != null) {
                EntityCommandAction action = new EntityCommandAction(
                        commandType,
                        entity.getUniqueId(),
                        entity.getType().name(),
                        entity.getLocation().getX(),
                        entity.getLocation().getY(),
                        entity.getLocation().getZ(),
                        itemData
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Entity command: " + commandType.name() +
                        " on " + entity.getType().name());
            }
        }
    }
}
