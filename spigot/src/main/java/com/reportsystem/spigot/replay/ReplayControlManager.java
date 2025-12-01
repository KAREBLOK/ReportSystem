package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReplayControlManager {

    private final ReportSystemSpigot plugin;
    private final ReplayManager replayManager;
    private final Map<UUID, BossBar> progressBars = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, BukkitRunnable> updateTasks = new HashMap<>();

    public ReplayControlManager(ReportSystemSpigot plugin, ReplayManager replayManager) {
        this.plugin = plugin;
        this.replayManager = replayManager;
    }

    /**
     * Oyuncuya replay kontrol itemlerini verir
     */
    public void giveControlItems(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        // Mevcut envanteri kaydet
        savedInventories.put(playerUUID, player.getInventory().getContents().clone());
        plugin.getLogger().info("[REPLAY-CONTROL] Saved inventory for " + player.getName());

        // Envanteri temizle
        player.getInventory().clear();

        // Kontrol itemlerini ver - YENİ DÜZEN
        player.getInventory().setItem(0, createPlayPauseItem(replayPlayer.isPaused())); // Play/Pause
        player.getInventory().setItem(1, createInfoItem(replayPlayer)); // Replay Bilgileri

        player.getInventory().setItem(3, createRewindItem()); // Geri sar
        player.getInventory().setItem(4, createTeleportItem()); // Işınlanma
        player.getInventory().setItem(5, createForwardItem()); // İleri sar

        player.getInventory().setItem(7, createSpeedItem(replayPlayer.getPlaybackSpeed())); // Hız kontrolü
        player.getInventory().setItem(8, createStopItem()); // Durdur

        // Progress bar oluştur
        createProgressBar(player, replayPlayer);

        // Hotbar güncelleme task'ı
        startHotbarUpdateTask(player, replayPlayer);

        plugin.getLogger().info("[REPLAY-CONTROL] Control items given to " + player.getName());
    }

    /**
     * Geri sarma itemi oluşturur (5 saniye)
     */
    private ItemStack createRewindItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // MHF_ArrowLeft kafası
        meta.setOwner("MHF_ArrowLeft");
        meta.setDisplayName(ChatColor.RED + "◀ 5 Saniye Geri");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Replay'i 5 saniye geri sar",
                "",
                ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Geri sar"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * İleri sarma itemi oluşturur (5 saniye)
     */
    private ItemStack createForwardItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // MHF_ArrowRight kafası
        meta.setOwner("MHF_ArrowRight");
        meta.setDisplayName(ChatColor.GREEN + "5 Saniye İleri ▶");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Replay'i 5 saniye ileri sar",
                "",
                ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "İleri sar"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Play/Pause itemi oluşturur
     */
    private ItemStack createPlayPauseItem(boolean isPaused) {
        ItemStack item;
        ItemMeta meta;

        if (isPaused) {
            item = new ItemStack(Material.LIME_DYE);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "▶ OYNAT");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Replay'i devam ettir",
                    "",
                    ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Oynat"
            ));
        } else {
            item = new ItemStack(Material.GRAY_DYE);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "⏸ DURAKLAT");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Replay'i duraklat",
                    "",
                    ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Duraklat"
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Hız kontrolü itemi oluşturur
     */
    private ItemStack createSpeedItem(double currentSpeed) {
        ItemStack item = new ItemStack(Material.RABBIT_FOOT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "⚡ Hız: " + ChatColor.WHITE + currentSpeed + "x");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Replay oynatma hızını değiştir",
                "",
                ChatColor.YELLOW + "Sol Tık: " + ChatColor.WHITE + "Hızlandır",
                ChatColor.YELLOW + "Sağ Tık: " + ChatColor.WHITE + "Yavaşlat",
                "",
                ChatColor.DARK_GRAY + "Hızlar: 0.25x, 0.5x, 1.0x, 2.0x, 4.0x"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Bilgi itemi oluşturur - Artık GUI açar
     */
    private ItemStack createInfoItem(ReplayPlayer replayPlayer) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        long elapsedSeconds = replayPlayer.getElapsedTime() / 1000;
        long totalSeconds = replayPlayer.getTotalTime() / 1000;

        meta.setDisplayName(ChatColor.YELLOW + "📊 Replay Bilgileri");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Oyuncu: " + ChatColor.WHITE + replayPlayer.getReplay().getRecordedPlayer(),
                ChatColor.GRAY + "Süre: " + ChatColor.WHITE + formatTime(elapsedSeconds) + " / " + formatTime(totalSeconds),
                ChatColor.GRAY + "İlerleme: " + ChatColor.WHITE + String.format("%.1f%%", replayPlayer.getProgress() * 100),
                ChatColor.GRAY + "Hız: " + ChatColor.WHITE + replayPlayer.getPlaybackSpeed() + "x",
                "",
                ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Detaylı bilgileri aç"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Işınlanma itemi oluşturur
     */
    private ItemStack createTeleportItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "🌀 Işınlanma");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Replay'deki oyuncuların yanına ışınlan",
                "",
                ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Işınlanma menüsünü aç"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Durdurma itemi oluşturur
     */
    private ItemStack createStopItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "■ DURDUR");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Replay'i durdur ve çık",
                "",
                ChatColor.YELLOW + "Tık: " + ChatColor.WHITE + "Hemen çık"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Progress bar oluşturur
     */
    private void createProgressBar(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        // Önceki boss bar'ı kaldır
        BossBar oldBar = progressBars.remove(playerUUID);
        if (oldBar != null) {
            oldBar.removeAll();
            plugin.getLogger().info("[REPLAY-CONTROL] Removed old boss bar for " + player.getName());
        }

        BossBar bossBar = plugin.getServer().createBossBar(
                ChatColor.GOLD + "Replay: " + ChatColor.WHITE + replayPlayer.getReplay().getRecordedPlayer(),
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );

        bossBar.addPlayer(player);
        bossBar.setProgress(0.0);
        progressBars.put(playerUUID, bossBar);

        plugin.getLogger().info("[REPLAY-CONTROL] Created boss bar for " + player.getName());
    }

    /**
     * Hotbar güncelleme task'ı
     */
    private void startHotbarUpdateTask(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        // Önceki task'ı durdur
        BukkitRunnable oldTask = updateTasks.remove(playerUUID);
        if (oldTask != null) {
            oldTask.cancel();
            plugin.getLogger().info("[REPLAY-CONTROL] Cancelled old update task for " + player.getName());
        }

        BukkitRunnable updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || replayPlayer.getState() == ReplayPlayer.ReplayState.STOPPED) {
                    plugin.getLogger().info("[REPLAY-CONTROL] Stopping update task for " + player.getName() +
                            " (online: " + player.isOnline() + ", state: " + replayPlayer.getState() + ")");
                    this.cancel();
                    updateTasks.remove(playerUUID);
                    return;
                }

                // Replay bitmiş mi kontrol et
                if (replayPlayer.getState() == ReplayPlayer.ReplayState.FINISHED) {
                    plugin.getLogger().info("[REPLAY-CONTROL] Replay finished, stopping update task for " + player.getName());
                    this.cancel();
                    updateTasks.remove(playerUUID);
                    return;
                }

                // Play/Pause butonunu güncelle
                player.getInventory().setItem(0, createPlayPauseItem(replayPlayer.isPaused()));

                // Bilgi butonunu güncelle
                player.getInventory().setItem(1, createInfoItem(replayPlayer));

                // Hız butonunu güncelle
                player.getInventory().setItem(7, createSpeedItem(replayPlayer.getPlaybackSpeed()));

                // Progress bar güncelle
                BossBar bossBar = progressBars.get(playerUUID);
                if (bossBar != null) {
                    // Eğer duraklatılmışsa progress'i güncelleme
                    if (!replayPlayer.isPaused()) {
                        double progress = replayPlayer.getProgress();
                        bossBar.setProgress(Math.max(0, Math.min(1, progress)));
                    }

                    long elapsedSeconds = replayPlayer.getElapsedTime() / 1000;
                    long totalSeconds = replayPlayer.getTotalTime() / 1000;

                    String title = ChatColor.GOLD + "Replay: " + ChatColor.WHITE + replayPlayer.getReplay().getRecordedPlayer() +
                            ChatColor.GRAY + " [" + formatTime(elapsedSeconds) + "/" + formatTime(totalSeconds) + "]" +
                            ChatColor.AQUA + " " + replayPlayer.getPlaybackSpeed() + "x";

                    if (replayPlayer.isPaused()) {
                        title += ChatColor.RED + " [DURAKLADI]";
                    }

                    bossBar.setTitle(title);

                    // Durum rengini değiştir
                    if (replayPlayer.isPaused()) {
                        bossBar.setColor(BarColor.RED);
                    } else {
                        bossBar.setColor(BarColor.YELLOW);
                    }
                }
            }
        };

        updateTasks.put(playerUUID, updateTask);
        updateTask.runTaskTimer(plugin, 0L, 5L); // Her 5 tick'te bir güncelle

        plugin.getLogger().info("[REPLAY-CONTROL] Started update task for " + player.getName());
    }

    /**
     * Kontrol itemlerini kaldırır ve eski envanteri geri yükler - TAMAMİYLE YENİDEN YAZILDI
     */
    public void removeControlItems(Player player) {
        UUID playerUUID = player.getUniqueId();

        plugin.getLogger().info("[REPLAY-CONTROL] Starting cleanup for " + player.getName());

        // 1. Update task'ını durdur
        BukkitRunnable updateTask = updateTasks.remove(playerUUID);
        if (updateTask != null) {
            updateTask.cancel();
            plugin.getLogger().info("[REPLAY-CONTROL] Update task cancelled for " + player.getName());
        }

        // 2. Boss bar'ı kaldır - ÖNEMLİ: Bu boss bar'ı tamamen siler
        BossBar bossBar = progressBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll(); // Tüm oyunculardan kaldır
            plugin.getLogger().info("[REPLAY-CONTROL] Boss bar removed for " + player.getName());
        }

        // 3. Envanteri restore et - SYNC olarak yapılmalı
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] savedInventory = savedInventories.remove(playerUUID);
            if (savedInventory != null) {
                // Envanteri tamamen temizle
                player.getInventory().clear();

                // 1 tick bekle ve eski envanteri geri yükle
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.getInventory().setContents(savedInventory);
                    player.updateInventory();
                    plugin.getLogger().info("[REPLAY-CONTROL] Inventory restored for " + player.getName());
                }, 1L);

            } else {
                // Eğer kaydedilmiş envanter yoksa temizle
                player.getInventory().clear();
                player.updateInventory();
                plugin.getLogger().info("[REPLAY-CONTROL] Inventory cleared for " + player.getName() + " (no saved inventory)");
            }
        });

        plugin.getLogger().info("[REPLAY-CONTROL] Cleanup completed for " + player.getName());
    }

    /**
     * Zaman formatı
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Tüm progress barları ve envanteri temizle - TAMAMİYLE YENİDEN YAZILDI
     */
    public void cleanup() {
        plugin.getLogger().info("[REPLAY-CONTROL] Starting full cleanup - " + progressBars.size() +
                " boss bars, " + savedInventories.size() + " inventories, " + updateTasks.size() + " tasks");

        // 1. Tüm update task'larını durdur
        for (Map.Entry<UUID, BukkitRunnable> entry : updateTasks.entrySet()) {
            BukkitRunnable task = entry.getValue();
            if (task != null) {
                task.cancel();
            }
        }
        updateTasks.clear();
        plugin.getLogger().info("[REPLAY-CONTROL] All update tasks cancelled");

        // 2. Tüm boss bar'ları kaldır
        for (Map.Entry<UUID, BossBar> entry : progressBars.entrySet()) {
            BossBar bar = entry.getValue();
            if (bar != null) {
                bar.removeAll(); // Tüm oyunculardan kaldır
            }
        }
        progressBars.clear();
        plugin.getLogger().info("[REPLAY-CONTROL] All boss bars removed");

        // 3. Tüm inventory'leri geri yükle
        for (Map.Entry<UUID, ItemStack[]> entry : savedInventories.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.getInventory().clear();
                player.getInventory().setContents(entry.getValue());
                player.updateInventory();
                plugin.getLogger().info("[REPLAY-CONTROL] Restored inventory for: " + player.getName());
            }
        }
        savedInventories.clear();

        plugin.getLogger().info("[REPLAY-CONTROL] Full cleanup completed");
    }
}