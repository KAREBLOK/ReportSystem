package com.reportsystem.spigot.overwatch.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.OverwatchMenuGUI;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Listener for NPC clicks and protection
 * Opens Overwatch Menu GUI when player right-clicks NPC
 * Prevents armor manipulation and damage
 */
public class NPCClickListener implements Listener {

    private final ReportSystemSpigot plugin;

    public NPCClickListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCClick(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // Check if it's an armor stand
        if (!(clickedEntity instanceof ArmorStand)) {
            return;
        }

        plugin.getLogger().info("[NPC-CLICK] Player " + player.getName() + " clicked armor stand, checking if it's an NPC...");
        plugin.getLogger().info("[NPC-CLICK] Total active NPCs in memory: " + plugin.getNPCManager().getActiveNPCs().size());

        // Check if it's an Overwatch NPC
        String npcId = plugin.getNPCManager().getNPCAtLocation(clickedEntity);

        if (npcId == null) {
            plugin.getLogger().info("[NPC-CLICK] Not an Overwatch NPC, allowing normal interaction");
            return; // Not an Overwatch NPC
        }

        plugin.getLogger().info("[NPC-CLICK] Found Overwatch NPC: " + npcId);

        // Cancel event to prevent normal interaction
        event.setCancelled(true);

        // Check permission
        if (!player.hasPermission("reportsystem.overwatch")) {
            player.sendMessage("§c[Overwatch] Bu NPC'yi kullanma yetkiniz yok!");
            return;
        }

        // Open Overwatch Menu GUI
        new OverwatchMenuGUI(plugin, player).open();
    }

    /**
     * Prevent players from taking/swapping armor from Overwatch NPCs
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();

        // Check if it's an Overwatch NPC or hologram
        String npcId = plugin.getNPCManager().getNPCAtLocation(armorStand);

        if (npcId != null || isOverwatchHologram(armorStand)) {
            // Cancel event to prevent armor manipulation
            event.setCancelled(true);
        }
    }

    /**
     * Prevent players from damaging Overwatch NPCs
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();

        // Check if damaged entity is an armor stand
        if (!(damaged instanceof ArmorStand)) {
            return;
        }

        ArmorStand armorStand = (ArmorStand) damaged;

        // Check if it's an Overwatch NPC or hologram
        String npcId = plugin.getNPCManager().getNPCAtLocation(armorStand);

        if (npcId != null || isOverwatchHologram(armorStand)) {
            // Cancel damage event
            event.setCancelled(true);
        }
    }

    /**
     * Check if armor stand is an Overwatch hologram
     */
    private boolean isOverwatchHologram(ArmorStand armorStand) {
        // Holograms are invisible, have custom name, and marker mode
        return armorStand.isMarker() &&
               !armorStand.isVisible() &&
               armorStand.hasGravity() == false;
    }
}
