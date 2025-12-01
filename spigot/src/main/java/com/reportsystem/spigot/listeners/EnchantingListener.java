package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.EnchantAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class EnchantingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public EnchantingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnchant(EnchantItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getEnchanter();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack itemBefore = event.getItem().clone();
            ItemStack itemAfter = event.getItem().clone();

            // Apply enchantments to the after item
            event.getEnchantsToAdd().forEach(itemAfter::addEnchantment);

            String itemBeforeData = ItemSerializer.serializeItemStack(itemBefore);
            String itemAfterData = ItemSerializer.serializeItemStack(itemAfter);
            int expLevelCost = event.getExpLevelCost();
            int button = event.whichButton(); // 0, 1, 2 for top, middle, bottom

            EnchantAction action = new EnchantAction(
                    itemBeforeData,
                    itemAfterData,
                    expLevelCost,
                    button
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Item enchanted: " + itemAfter.getType().name() +
                    " with " + event.getEnchantsToAdd().size() + " enchantments (cost: " + expLevelCost + " levels)");
        }
    }
}
