package com.reportsystem.spigot.overwatch.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.OverwatchMenuGUI;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NPCClickListener extends PacketListenerAbstract implements Listener {

    private final ReportSystemSpigot plugin;
    private final Map<UUID, Long> clickCooldown = new ConcurrentHashMap<>();

    public NPCClickListener(ReportSystemSpigot plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)
            return;

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);

        // Only handle INTERACT (skip INTERACT_AT and ATTACK to avoid duplicates)
        if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT) {
            // Still cancel INTERACT_AT for NPC entities to prevent further processing
            if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                if (plugin.getNPCManager().getNPCByEntityId(packet.getEntityId()) != null) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        int entityId = packet.getEntityId();
        String npcId = plugin.getNPCManager().getNPCByEntityId(entityId);

        if (npcId == null)
            return;

        event.setCancelled(true);

        Player player = (Player) event.getPlayer();
        // event.getPlayer() handshake/disconnect aninda null donebilir
        if (player == null) {
            return;
        }

        // Cooldown: 500ms between clicks
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldown.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < 500)
            return;
        clickCooldown.put(player.getUniqueId(), now);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline())
                return;

            if (!player.hasPermission("reportsystem.overwatch")) {
                player.sendMessage(plugin.getMessageManager().colorize(
                        plugin.getMessageManager().getMessage("overwatch.npc.no-permission")));
                return;
            }

            new OverwatchMenuGUI(plugin, player).open();
        });
    }

    /**
     * Show NPCs to player on join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && plugin.getNPCManager() != null) {
                plugin.getNPCManager().showAllNPCsToPlayer(player);
            }
        }, 20L); // 1 second after join
    }

    /**
     * Prevent players from taking/swapping armor from hologram armor stands
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand armorStand = event.getRightClicked();

        if (isOverwatchHologram(armorStand)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent players from damaging hologram armor stands
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();

        if (!(damaged instanceof ArmorStand))
            return;

        ArmorStand armorStand = (ArmorStand) damaged;

        if (isOverwatchHologram(armorStand)) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if armor stand is an Overwatch hologram
     */
    private boolean isOverwatchHologram(ArmorStand armorStand) {
        return armorStand.getScoreboardTags().contains("overwatch_hologram");
    }

    /**
     * Clean up orphaned holograms that were saved to the world (e.g. from server
     * crashes)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getNPCManager() == null)
                return;

            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof ArmorStand && isOverwatchHologram((ArmorStand) entity)) {
                    // Check if this entity is tracked by an active NPC
                    boolean tracked = false;
                    for (com.reportsystem.spigot.overwatch.NPCManager.NPCData npcData : plugin.getNPCManager()
                            .getActiveNPCs().values()) {
                        if (npcData.getHologramLines() != null && npcData.getHologramLines().contains(entity)) {
                            tracked = true;
                            break;
                        }
                    }
                    if (!tracked) {
                        entity.remove();
                    }
                }
            }
        }, 10L); // wait 10 ticks to ensure tracker is updated
    }
}
