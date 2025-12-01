package com.reportsystem.spigot.listeners;

import com.reportsystem.common.replay.actions.HangingAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HangingListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public HangingListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHangingPlace(HangingPlaceEvent event) {
        try {
            if (event.isCancelled()) return;

            Player player = event.getPlayer();
            if (player == null) return;

            RecordingSession session = recordingManager.getSession(player.getUniqueId());
            if (session != null) {
                Hanging hanging = event.getEntity();
                HangingAction.HangingType type = null;
                String itemData = null;
                String paintingArt = null;

                if (hanging instanceof Painting) {
                    type = HangingAction.HangingType.PAINTING;
                    Painting painting = (Painting) hanging;
                    paintingArt = painting.getArt().name();
                } else if (hanging instanceof ItemFrame) {
                    ItemFrame frame = (ItemFrame) hanging;
                    if (frame instanceof GlowItemFrame) {
                        type = HangingAction.HangingType.GLOW_ITEM_FRAME;
                    } else {
                        type = HangingAction.HangingType.ITEM_FRAME;
                    }

                    // Item Frame içindeki item
                    if (frame.getItem() != null && !frame.getItem().getType().isAir()) {
                        itemData = ItemSerializer.serializeItemStack(frame.getItem());
                    }
                }

                if (type != null) {
                    HangingAction action = new HangingAction(
                            HangingAction.ActionType.PLACE,
                            type,
                            hanging.getLocation().getX(),
                            hanging.getLocation().getY(),
                            hanging.getLocation().getZ(),
                            hanging.getFacing().name(),
                            itemData,
                            paintingArt
                    );
                    session.addAction(action);

                    plugin.getLogger().info("[RECORDING-DEBUG] Hanging placed: " + type +
                            " facing " + hanging.getFacing() + " at " + hanging.getLocation() +
                            (paintingArt != null ? " art: " + paintingArt : ""));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[HangingListener] Error in onHangingPlace: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        try {
            if (event.isCancelled()) return;

            if (event.getRemover() instanceof Player) {
                Player player = (Player) event.getRemover();
                RecordingSession session = recordingManager.getSession(player.getUniqueId());

                if (session != null) {
                    Hanging hanging = event.getEntity();
                    HangingAction.HangingType type = null;

                    if (hanging instanceof Painting) {
                        type = HangingAction.HangingType.PAINTING;
                    } else if (hanging instanceof GlowItemFrame) {
                        type = HangingAction.HangingType.GLOW_ITEM_FRAME;
                    } else if (hanging instanceof ItemFrame) {
                        type = HangingAction.HangingType.ITEM_FRAME;
                    }

                    if (type != null) {
                        HangingAction action = new HangingAction(
                                HangingAction.ActionType.BREAK,
                                type,
                                hanging.getLocation().getX(),
                                hanging.getLocation().getY(),
                                hanging.getLocation().getZ(),
                                hanging.getFacing().name(),
                                null
                        );
                        session.addAction(action);

                        plugin.getLogger().info("[RECORDING-DEBUG] Hanging broken: " + type);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[HangingListener] Error in onHangingBreak: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
