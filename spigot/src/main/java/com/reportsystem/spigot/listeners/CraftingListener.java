package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.CraftAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class CraftingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public CraftingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack result = event.getCurrentItem();

            if (result != null && result.getType() != org.bukkit.Material.AIR) {
                String craftedItem = ItemSerializer.serializeItemStack(result);
                int amount = result.getAmount();
                boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;

                // Shift-click ile birden fazla craft ediliyorsa hesapla
                if (isShiftClick) {
                    // Shift-click miktarını bul
                    amount = result.getAmount() * Math.min(
                            result.getMaxStackSize() / result.getAmount(),
                            64 / result.getAmount()
                    );
                }

                CraftAction action = new CraftAction(
                        craftedItem,
                        amount,
                        isShiftClick
                );
                session.addAction(action);

                plugin.getLogger().info("[RECORDING-DEBUG] Item crafted: " + result.getType().name() +
                        " x" + amount + (isShiftClick ? " (shift-click)" : ""));
            }
        }
    }
}
