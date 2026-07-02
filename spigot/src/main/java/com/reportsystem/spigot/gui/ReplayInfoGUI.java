package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.managers.MessageManager;
import com.reportsystem.spigot.replay.ReplayPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Replay bilgileri ve ayarlar GUI
 */
public class ReplayInfoGUI extends GUI {

    private final ReplayPlayer replayPlayer;
    private final MessageManager msg;
    private final GUIConfig guiConfig;

    private static final int SLOT_INFO = 10;
    private static final int SLOT_GLOW = 12;
    private static final int SLOT_NIGHT_VISION = 14;
    private static final int SLOT_FIRST_PERSON = 16;
    private static final int SLOT_CLOSE = 22;

    public ReplayInfoGUI(ReportSystemSpigot plugin, Player player, ReplayPlayer replayPlayer) {
        super(plugin, player);
        this.replayPlayer = replayPlayer;
        this.msg = plugin.getMessageManager();
        this.guiConfig = new GUIConfig(plugin, "replay-settings");
    }

    @Override
    public void build() {
        String title = guiConfig.getTitle();
        inventory = Bukkit.createInventory(this, 27, title);

        inventory.setItem(SLOT_INFO, createReplayInfoItem());
        inventory.setItem(SLOT_GLOW, createGlowItem());
        inventory.setItem(SLOT_NIGHT_VISION, createNightVisionItem());
        inventory.setItem(SLOT_FIRST_PERSON, createFirstPersonItem());
        inventory.setItem(SLOT_CLOSE, createCloseButton());
    }

    private ItemStack createReplayInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String playerName = replayPlayer.getReplay().getRecordedPlayer();
            long elapsedSeconds = replayPlayer.getElapsedTime() / 1000;
            long totalSeconds = replayPlayer.getTotalTime() / 1000;
            double progress = replayPlayer.getProgress() * 100;
            int totalActions = replayPlayer.getTotalActions();
            int currentAction = replayPlayer.getCurrentActionIndex();
            String status = replayPlayer.isPaused()
                    ? msg.colorize(guiConfig.getConfig().getString("status-paused", "&cDuraklatıldı"))
                    : msg.colorize(guiConfig.getConfig().getString("status-playing", "&aOynuyor"));
            String speed = String.valueOf(replayPlayer.getPlaybackSpeed());

            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString("items.info.name", "&eReplay Bilgileri")));
            List<String> lore = guiConfig.getConfig().getStringList("items.info.lore");
            meta.setLore(lore.stream().map(line -> msg.colorize(line
                    .replace("%player%", playerName)
                    .replace("%elapsed%", formatDuration(elapsedSeconds))
                    .replace("%total%", formatDuration(totalSeconds))
                    .replace("%progress%", String.format("%.1f%%", progress))
                    .replace("%current%", String.valueOf(currentAction))
                    .replace("%total_actions%", String.valueOf(totalActions))
                    .replace("%status%", status)
                    .replace("%speed%", speed)
            )).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlowItem() {
        boolean glowEnabled = replayPlayer.getNpcManager().isGlowEnabled();
        String key = glowEnabled ? "items.glow.enabled" : "items.glow.disabled";
        Material mat = glowEnabled ? Material.SPECTRAL_ARROW : Material.ARROW;
        String matName = guiConfig.getConfig().getString(key + ".material");
        if (matName != null) {
            Material m = Material.getMaterial(matName);
            if (m != null) mat = m;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString(key + ".name", "")));
            List<String> lore = guiConfig.getConfig().getStringList(key + ".lore");
            meta.setLore(lore.stream().map(msg::colorize).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNightVisionItem() {
        boolean hasNightVision = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        String key = hasNightVision ? "items.night-vision.enabled" : "items.night-vision.disabled";
        Material mat = hasNightVision ? Material.GOLDEN_CARROT : Material.CARROT;
        String matName = guiConfig.getConfig().getString(key + ".material");
        if (matName != null) {
            Material m = Material.getMaterial(matName);
            if (m != null) mat = m;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString(key + ".name", "")));
            List<String> lore = guiConfig.getConfig().getStringList(key + ".lore");
            meta.setLore(lore.stream().map(msg::colorize).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFirstPersonItem() {
        boolean fpEnabled = replayPlayer.getNpcManager().isFirstPersonEnabled();
        String key = fpEnabled ? "items.first-person.enabled" : "items.first-person.disabled";
        Material mat = fpEnabled ? Material.ENDER_EYE : Material.ENDER_PEARL;
        String matName = guiConfig.getConfig().getString(key + ".material");
        if (matName != null) {
            Material m = Material.getMaterial(matName);
            if (m != null) mat = m;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(msg.colorize(guiConfig.getConfig().getString(key + ".name", "")));
            List<String> lore = guiConfig.getConfig().getStringList(key + ".lore");
            meta.setLore(lore.stream().map(msg::colorize).collect(Collectors.toList()));
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

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        switch (slot) {
            case SLOT_GLOW:
                boolean newGlow = !replayPlayer.getNpcManager().isGlowEnabled();
                replayPlayer.getNpcManager().setGlowEnabled(newGlow);
                plugin.getReplayManager().getPreferences(player.getUniqueId()).setGlowEnabled(newGlow);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, newGlow ? 2.0f : 0.5f);
                inventory.setItem(SLOT_GLOW, createGlowItem());
                break;

            case SLOT_NIGHT_VISION:
                boolean hasNV = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
                if (hasNV) {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                }
                plugin.getReplayManager().getPreferences(player.getUniqueId()).setNightVision(!hasNV);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, hasNV ? 0.5f : 2.0f);
                inventory.setItem(SLOT_NIGHT_VISION, createNightVisionItem());
                break;

            case SLOT_FIRST_PERSON:
                boolean newFP = !replayPlayer.getNpcManager().isFirstPersonEnabled();
                replayPlayer.getNpcManager().setFirstPerson(newFP);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, newFP ? 2.0f : 0.5f);
                player.closeInventory();
                if (newFP) {
                    plugin.getReplayManager().getControlManager().enterFirstPersonMode(player, replayPlayer);
                    player.sendMessage(msg.colorize(guiConfig.getConfig().getString("first-person-exit-message", "&e&lEğilme &7tuşuna basarak 1. şahıstan çıkabilirsiniz.")));
                } else {
                    plugin.getReplayManager().getControlManager().exitFirstPersonMode(player, replayPlayer);
                }
                break;

            case SLOT_CLOSE:
                player.closeInventory();
                break;
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
