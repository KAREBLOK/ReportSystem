package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.replay.ReplayPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Replay bilgileri GUI
 */
public class ReplayInfoGUI extends GUI {

    private final ReplayPlayer replayPlayer;

    public ReplayInfoGUI(ReportSystemSpigot plugin, Player player, ReplayPlayer replayPlayer) {
        super(plugin, player);
        this.replayPlayer = replayPlayer;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + "Replay Bilgileri");

        // Background
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);

        // Replay bilgileri
        inventory.setItem(13, createReplayInfoItem());

        // Geri dön
        inventory.setItem(22, createBackButton());
    }

    private ItemStack createReplayInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Replay Detayları");

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Kayıtlı Oyuncu: " + ChatColor.WHITE + replayPlayer.getReplay().getRecordedPlayer(),
                    ChatColor.GRAY + "Toplam Süre: " + ChatColor.YELLOW + formatDuration(replayPlayer.getTotalTime()),
                    ChatColor.GRAY + "Aksiyon Sayısı: " + ChatColor.AQUA + replayPlayer.getTotalActions(),
                    ChatColor.GRAY + "Mevcut Aksiyon: " + ChatColor.GREEN + replayPlayer.getCurrentActionIndex() + "/" + replayPlayer.getTotalActions(),
                    "",
                    ChatColor.GRAY + "Durum: " + (replayPlayer.isPaused() ? ChatColor.YELLOW + "Duraklatıldı" : ChatColor.GREEN + "Oynatılıyor"),
                    ChatColor.GRAY + "Hız: " + ChatColor.WHITE + replayPlayer.getPlaybackSpeed() + "x",
                    ""
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "◀ Geri Dön");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Replay'e geri dön"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 22) {
            player.closeInventory();
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
