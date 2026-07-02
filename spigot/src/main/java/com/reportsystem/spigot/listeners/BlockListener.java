package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.BlockAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BlockListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        try {
            if (event.isCancelled()) return;

            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Block block = event.getBlock();
                Location loc = block.getLocation();

                // Kırma animasyonu tamamlandı
                session.addAction(new BlockAction(
                        BlockAction.BlockActionType.STOP_BREAKING,
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ(),
                        10 // Son stage
                ));

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Block broken: " + block.getType() +
                        " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BlockListener] Error in onBlockBreak: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        try {
            if (event.isCancelled()) return;

            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
                Block block = event.getBlockPlaced();
                Location loc = block.getLocation();
                String blockData = block.getType().name();

                // Özel blok tipleri için ek bilgi ekle
                BlockData data = block.getBlockData();

                // Yatak için facing ve part bilgisi
                if (data instanceof org.bukkit.block.data.type.Bed) {
                    org.bukkit.block.data.type.Bed bed = (org.bukkit.block.data.type.Bed) data;
                    blockData = block.getType().name() + ":" +
                            bed.getFacing().name() + ":" +
                            bed.getPart().name();
                }
                // Kapı için facing ve half bilgisi
                else if (data instanceof Door) {
                    Door door = (Door) data;
                    blockData = block.getType().name() + ":" +
                            door.getFacing().name() + ":" +
                            door.getHalf().name() + ":" +
                            door.getHinge().name();
                }
                //階段 (stairs) için facing ve half
                else if (data instanceof org.bukkit.block.data.type.Stairs) {
                    org.bukkit.block.data.type.Stairs stairs = (org.bukkit.block.data.type.Stairs) data;
                    blockData = block.getType().name() + ":" +
                            stairs.getFacing().name() + ":" +
                            stairs.getHalf().name();
                }
                // Directional bloklar (furnace, chest vb.)
                else if (data instanceof org.bukkit.block.data.Directional) {
                    org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) data;
                    blockData = block.getType().name() + ":" +
                            directional.getFacing().name();
                }

                session.addAction(new BlockAction(
                        BlockAction.BlockActionType.PLACE_BLOCK,
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ(),
                        blockData
                ));

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Block placed: " + blockData +
                        " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BlockListener] Error in onBlockPlace: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;

            Player player = event.getPlayer();
            RecordingSession session = recordingManager.getSession(player.getUniqueId());

            if (session != null) {
            Block block = event.getClickedBlock();
            Material type = block.getType();
            BlockData blockData = block.getBlockData();

            // Kapı, trapdoor, fence gate, button, lever vb. etkileşimli bloklar
            boolean isInteractable = false;
            String interactionType = "";

            if (blockData instanceof Openable) {
                isInteractable = true;
                Openable openable = (Openable) blockData;

                // Kapı için özel işlem - double door kontrolü
                if (blockData instanceof Door) {
                    Door door = (Door) blockData;
                    // Alt parça mı kontrol et
                    Block targetBlock = block;
                    if (door.getHalf() == Bisected.Half.TOP) {
                        // Üst parçaysa, alt parçayı bul
                        targetBlock = block.getRelative(0, -1, 0);
                    }

                    interactionType = openable.isOpen() ? "DOOR_CLOSE" : "DOOR_OPEN";

                    // Hem açılan kapıyı hem de komşu kapıyı kaydet
                    recordDoorInteraction(session, targetBlock, interactionType);

                } else if (blockData instanceof TrapDoor) {
                    interactionType = openable.isOpen() ? "TRAPDOOR_CLOSE" : "TRAPDOOR_OPEN";
                } else if (type.name().contains("GATE")) {
                    interactionType = openable.isOpen() ? "GATE_CLOSE" : "GATE_OPEN";
                }
            } else if (type.name().contains("BUTTON")) {
                isInteractable = true;
                interactionType = "BUTTON_PRESS";
            } else if (type.name().contains("LEVER")) {
                isInteractable = true;
                interactionType = "LEVER_TOGGLE";
            } else if (type.name().contains("PLATE")) {
                isInteractable = true;
                interactionType = "PRESSURE_PLATE";
            }

            if (isInteractable && !interactionType.isEmpty() && !interactionType.startsWith("DOOR")) {
                Location loc = block.getLocation();
                session.addAction(new BlockAction(
                        BlockAction.BlockActionType.INTERACT_BLOCK,
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ(),
                        interactionType
                ));

                ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Block interaction: " + interactionType +
                        " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[BlockListener] Error in onPlayerInteract: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Kapı etkileşimini kaydeder (double door desteği ile)
     */
    private void recordDoorInteraction(RecordingSession session, Block doorBlock, String interactionType) {
        Location loc = doorBlock.getLocation();

        // Ana kapıyı kaydet
        session.addAction(new BlockAction(
                BlockAction.BlockActionType.INTERACT_BLOCK,
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                interactionType
        ));

        ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Door interaction: " + interactionType +
                " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

        // Komşu çift kapıyı kontrol et (NORTH, SOUTH, EAST, WEST)
        Block[] neighbors = new Block[] {
            doorBlock.getRelative(1, 0, 0),  // EAST
            doorBlock.getRelative(-1, 0, 0), // WEST
            doorBlock.getRelative(0, 0, 1),  // SOUTH
            doorBlock.getRelative(0, 0, -1)  // NORTH
        };

        for (Block neighbor : neighbors) {
            if (neighbor.getBlockData() instanceof Door) {
                Door neighborDoor = (Door) neighbor.getBlockData();
                // Aynı açma durumunda mı kontrol et (double door sistemi)
                if (neighborDoor.isOpen() == ((Door) doorBlock.getBlockData()).isOpen()) {
                    Location neighborLoc = neighbor.getLocation();
                    session.addAction(new BlockAction(
                            BlockAction.BlockActionType.INTERACT_BLOCK,
                            neighborLoc.getBlockX(),
                            neighborLoc.getBlockY(),
                            neighborLoc.getBlockZ(),
                            interactionType
                    ));

                    ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Double door detected: " + interactionType +
                            " at " + neighborLoc.getBlockX() + "," + neighborLoc.getBlockY() + "," + neighborLoc.getBlockZ());
                    break; // Sadece bir komşu yeterli
                }
            }
        }
    }

}
