package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.managers.MessageManager;
import com.reportsystem.spigot.replay.ReplayPlayer;
import com.reportsystem.spigot.replay.ReplayNPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Replay isinlanma GUI - Replay'deki oyunculara isinlanma
 */
public class ReplayTeleportGUI extends GUI {

    private final ReplayPlayer replayPlayer;
    private final MessageManager msg;
    private final GUIConfig guiConfig;
    // slot -> player UUID mapping (null = main NPC)
    private final Map<Integer, UUID> slotPlayerMap = new HashMap<>();

    public ReplayTeleportGUI(ReportSystemSpigot plugin, Player player, ReplayPlayer replayPlayer) {
        super(plugin, player);
        this.replayPlayer = replayPlayer;
        this.msg = plugin.getMessageManager();
        this.guiConfig = new GUIConfig(plugin, "replay-teleport");
    }

    @Override
    public void build() {
        ReplayNPCManager npcManager = replayPlayer.getNpcManager();
        Map<UUID, String> nearbyNames = npcManager.getNearbyPlayerProfileNames();

        int totalPlayers = 1 + nearbyNames.size();
        int rows = Math.max(3, (int) Math.ceil((totalPlayers + 2) / 9.0) + 1);
        if (rows > 6) rows = 6;
        int size = rows * 9;

        String title = guiConfig.getTitle();
        inventory = Bukkit.createInventory(this, size, title);

        // Ana NPC (supheli)
        String mainName = replayPlayer.getReplay().getRecordedPlayer();
        Location mainLoc = replayPlayer.getLastLocation();
        inventory.setItem(10, createPlayerHead(mainName, mainLoc, true));
        slotPlayerMap.put(10, null);

        // Yakin oyuncular
        int slot = 11;
        for (Map.Entry<UUID, String> entry : nearbyNames.entrySet()) {
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot >= 44) break;
            while (slot % 9 == 0 || slot % 9 == 8) slot++;

            Location nearbyLoc = npcManager.getNearbyPlayerLocation(entry.getKey());
            inventory.setItem(slot, createPlayerHead(entry.getValue(), nearbyLoc, false));
            slotPlayerMap.put(slot, entry.getKey());
            slot++;
        }

        // Kapat butonu
        int closeSlot = size - 5;
        inventory.setItem(closeSlot, createCloseButton());
    }

    private ItemStack createPlayerHead(String playerName, Location location, boolean isMainTarget) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);

            String locText;
            if (location != null) {
                locText = String.format("%.0f, %.0f, %.0f", location.getX(), location.getY(), location.getZ());
            } else {
                locText = msg.colorize(guiConfig.getConfigString("items.location-unknown", "&cBilinmiyor"));
            }

            String key = isMainTarget ? "items.suspect" : "items.player";
            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString(key + ".name", "")
                    .replace("%player%", playerName)));
            List<String> lore = guiConfig.getConfig().getStringList(key + ".lore");
            meta.setLore(lore.stream().map(line -> msg.colorize(line
                    .replace("%player%", playerName)
                    .replace("%location%", locText)
            )).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString("items.close.name", "&c✖ Kapat")));
            List<String> lore = guiConfig.getConfig().getStringList("items.close.lore");
            meta.setLore(lore.stream().map(msg::colorize).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        if (item.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (item.getType() == Material.PLAYER_HEAD && slotPlayerMap.containsKey(slot)) {
            UUID targetUuid = slotPlayerMap.get(slot);
            Location targetLoc;

            if (targetUuid == null) {
                targetLoc = replayPlayer.getLastLocation();
            } else {
                targetLoc = replayPlayer.getNpcManager().getNearbyPlayerLocation(targetUuid);
            }

            if (targetLoc != null) {
                player.closeInventory();
                player.teleport(targetLoc);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            }
        }
    }

    @Override
    public void open() {
        build();
        player.openInventory(inventory);
    }

    public ReplayPlayer getReplayPlayer() {
        return replayPlayer;
    }
}
