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

import java.util.Arrays;

/**
 * Replay ışınlanma GUI - Belirli zamanlara atlamak için
 */
public class ReplayTeleportGUI extends GUI {

    private final ReplayPlayer replayPlayer;

    public ReplayTeleportGUI(ReportSystemSpigot plugin, Player player, ReplayPlayer replayPlayer) {
        super(plugin, player);
        this.replayPlayer = replayPlayer;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + "Zaman Atlama");

        // Background
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);

        // Zaman atlama seçenekleri
        inventory.setItem(10, createJumpItem(Material.YELLOW_WOOL, "Başlangıç", 0));
        inventory.setItem(11, createJumpItem(Material.LIME_WOOL, "25%", 25));
        inventory.setItem(12, createJumpItem(Material.CYAN_WOOL, "50%", 50));
        inventory.setItem(13, createJumpItem(Material.BLUE_WOOL, "75%", 75));
        inventory.setItem(14, createJumpItem(Material.RED_WOOL, "Son", 100));

        // İleri/Geri atlama
        inventory.setItem(19, createSkipItem(Material.PLAYER_HEAD, "⏪ 30 Saniye Geri", -30000));
        inventory.setItem(20, createSkipItem(Material.PLAYER_HEAD, "⏪ 10 Saniye Geri", -10000));
        inventory.setItem(23, createSkipItem(Material.PLAYER_HEAD, "10 Saniye İleri ⏩", 10000));
        inventory.setItem(24, createSkipItem(Material.PLAYER_HEAD, "30 Saniye İleri ⏩", 30000));

        // Geri dön
        inventory.setItem(22, createBackButton());
    }

    private ItemStack createJumpItem(Material material, String name, int percentage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            long targetTime = (replayPlayer.getTotalTime() * percentage) / 100;
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Hedef: " + ChatColor.WHITE + formatDuration(targetTime),
                    "",
                    ChatColor.YELLOW + "▶ Tıkla ve atla!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSkipItem(Material material, String name, int millis) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Atla: " + ChatColor.WHITE + (millis / 1000) + " saniye",
                    "",
                    ChatColor.YELLOW + "▶ Tıkla!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "✖ Kapat");
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
        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        player.closeInventory();

        // Yüzdelik atlamalar (henüz desteklenmiyor)
        if (slot == 10) { // Başlangıç
            player.sendMessage(ChatColor.RED + "✗ Zaman atlama özelliği henüz aktif değil!");
        } else if (slot == 11) { // 25%
            player.sendMessage(ChatColor.RED + "✗ Zaman atlama özelliği henüz aktif değil!");
        } else if (slot == 12) { // 50%
            player.sendMessage(ChatColor.RED + "✗ Zaman atlama özelliği henüz aktif değil!");
        } else if (slot == 13) { // 75%
            player.sendMessage(ChatColor.RED + "✗ Zaman atlama özelliği henüz aktif değil!");
        } else if (slot == 14) { // Son
            player.sendMessage(ChatColor.RED + "✗ Zaman atlama özelliği henüz aktif değil!");
        }
        // İleri/Geri
        else if (slot == 19) { // -30s
            replayPlayer.rewind(30);
            player.sendMessage(ChatColor.YELLOW + "⏪ 30 saniye geri sarıldı!");
        } else if (slot == 20) { // -10s
            replayPlayer.rewind(10);
            player.sendMessage(ChatColor.YELLOW + "⏪ 10 saniye geri sarıldı!");
        } else if (slot == 23) { // +10s
            replayPlayer.forward(10);
            player.sendMessage(ChatColor.GREEN + "⏩ 10 saniye ileri sarıldı!");
        } else if (slot == 24) { // +30s
            replayPlayer.forward(30);
            player.sendMessage(ChatColor.GREEN + "⏩ 30 saniye ileri sarıldı!");
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
