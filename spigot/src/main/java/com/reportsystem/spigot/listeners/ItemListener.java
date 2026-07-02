package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.ItemAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ItemListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public ItemListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack item = event.getItemDrop().getItemStack();
            String itemData = ItemSerializer.serializeItemStack(item);

            // DROP için eşyanın gerçek VELOCITY'sini kaydet (konum değil!)
            // Replay'de eşya gerçek fırlatma yönüne doğru atılacak
            org.bukkit.util.Vector vel = event.getItemDrop().getVelocity();

            session.addAction(new ItemAction(
                    ItemAction.ItemActionType.DROP,
                    itemData,
                    item.getAmount(),
                    vel.getX(), vel.getY(), vel.getZ()
            ));

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Item dropped: " + item.getType().name() +
                    " x" + item.getAmount() +
                    " vel=" + String.format("%.3f, %.3f, %.3f", vel.getX(), vel.getY(), vel.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack item = event.getItem().getItemStack();
            String itemData = ItemSerializer.serializeItemStack(item);

            session.addAction(new ItemAction(
                    ItemAction.ItemActionType.PICKUP,
                    itemData,
                    item.getAmount(),
                    event.getItem().getLocation().getX(),
                    event.getItem().getLocation().getY(),
                    event.getItem().getLocation().getZ()
            ));

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Item picked up: " + item.getType().name() + " x" + item.getAmount());
        }
    }

}
