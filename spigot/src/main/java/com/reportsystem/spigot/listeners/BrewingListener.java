package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.BrewAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class BrewingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BrewingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBrew(BrewEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        BrewingStand brewingStand = (BrewingStand) block.getState();

        // Yakındaki kaydedilen oyuncuları bul
        for (Player player : block.getWorld().getNearbyPlayers(block.getLocation(), 10)) {
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                ItemStack ingredient = brewingStand.getInventory().getIngredient();
                String ingredientName = ingredient != null ? ingredient.getType().name() : "UNKNOWN";

                // Result potions
                String[] resultPotions = new String[3];
                for (int i = 0; i < 3; i++) {
                    ItemStack potion = event.getResults().get(i);
                    if (potion != null && potion.getType() != org.bukkit.Material.AIR) {
                        resultPotions[i] = ItemSerializer.serializeItemStack(potion);
                    }
                }

                BrewAction action = new BrewAction(
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        ingredientName,
                        resultPotions
                );
                session.addAction(action);

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Brewing completed: " + ingredientName +
                        " at " + block.getLocation());
                break; // Sadece en yakın oyuncu kaydeder
            }
        }
    }
}
