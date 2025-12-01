package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.DecorationAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class DecorationListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public DecorationListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJukeboxUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.JUKEBOX) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getClickedBlock();
            Jukebox jukebox = (Jukebox) block.getState();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            DecorationAction.DecorationType decorationType;
            String itemData = null;

            if (itemInHand != null && itemInHand.getType().name().contains("MUSIC_DISC")) {
                decorationType = DecorationAction.DecorationType.JUKEBOX_INSERT;
                itemData = ItemSerializer.serializeItemStack(itemInHand);
            } else if (jukebox.isPlaying()) {
                decorationType = DecorationAction.DecorationType.JUKEBOX_EJECT;
                itemData = jukebox.getPlaying() != null ? ItemSerializer.serializeItemStack(jukebox.getRecord()) : null;
            } else {
                return;
            }

            DecorationAction action = new DecorationAction(
                    decorationType,
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    itemData
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Jukebox: " + decorationType.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlowerPot(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.FLOWER_POT) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getClickedBlock();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            DecorationAction.DecorationType decorationType;
            String itemData = null;

            if (itemInHand != null && itemInHand.getType() != Material.AIR) {
                decorationType = DecorationAction.DecorationType.FLOWER_POT_PLANT;
                itemData = ItemSerializer.serializeItemStack(itemInHand);
            } else {
                decorationType = DecorationAction.DecorationType.FLOWER_POT_REMOVE;
            }

            DecorationAction action = new DecorationAction(
                    decorationType,
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    itemData
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Flower pot: " + decorationType.name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLecternTakeBook(PlayerTakeLecternBookEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            Block block = event.getLectern().getBlock();
            ItemStack book = event.getBook();

            DecorationAction action = new DecorationAction(
                    DecorationAction.DecorationType.LECTERN_TAKE_BOOK,
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    ItemSerializer.serializeItemStack(book)
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Lectern book taken");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            ItemStack playerItem = event.getPlayerItem();
            ItemStack armorStandItem = event.getArmorStandItem();

            DecorationAction.DecorationType decorationType;
            String itemData = null;

            if (playerItem != null && playerItem.getType() != Material.AIR) {
                decorationType = DecorationAction.DecorationType.ARMOR_STAND_EQUIP;
                itemData = ItemSerializer.serializeItemStack(playerItem);
            } else if (armorStandItem != null && armorStandItem.getType() != Material.AIR) {
                decorationType = DecorationAction.DecorationType.ARMOR_STAND_TAKE;
                itemData = ItemSerializer.serializeItemStack(armorStandItem);
            } else {
                return;
            }

            org.bukkit.Location loc = event.getRightClicked().getLocation();
            DecorationAction action = new DecorationAction(
                    decorationType,
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ(),
                    itemData
            );
            session.addAction(action);

            plugin.getLogger().info("[RECORDING-DEBUG] Armor stand: " + decorationType.name());
        }
    }
}
