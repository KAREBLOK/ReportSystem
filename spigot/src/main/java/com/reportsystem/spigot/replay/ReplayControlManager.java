package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.GUIConfig;
import com.reportsystem.spigot.managers.MessageManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ReplayControlManager {

    private final ReportSystemSpigot plugin;
    private final ReplayManager replayManager;
    private final GUIConfig guiConfig;
    private final Map<UUID, BossBar> progressBars = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> updateTasks = new ConcurrentHashMap<>();

    // Oyuncu durumu kaydetme (replay bitince geri yuklenir)
    private final Map<UUID, GameMode> savedGameModes = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedHealth = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> savedFoodLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Float> savedSaturation = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedAllowFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedFlying = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> savedFireTicks = new ConcurrentHashMap<>();

    public ReplayControlManager(ReportSystemSpigot plugin, ReplayManager replayManager) {
        this.plugin = plugin;
        this.replayManager = replayManager;
        this.guiConfig = new GUIConfig(plugin, "replay-hotbar");
    }

    private MessageManager msg() {
        return plugin.getMessageManager();
    }

    private FileConfiguration cfg() {
        return guiConfig.getConfig();
    }

    /**
     * Oyuncuya replay kontrol itemlerini verir
     */
    public void giveControlItems(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        // Mevcut durumu kaydet
        savedInventories.put(playerUUID, player.getInventory().getContents().clone());
        savedGameModes.put(playerUUID, player.getGameMode());
        savedHealth.put(playerUUID, player.getHealth());
        savedFoodLevels.put(playerUUID, player.getFoodLevel());
        savedSaturation.put(playerUUID, player.getSaturation());
        savedAllowFlight.put(playerUUID, player.getAllowFlight());
        savedFlying.put(playerUUID, player.isFlying());
        savedFireTicks.put(playerUUID, player.getFireTicks());
        plugin.debug("[REPLAY-CONTROL] Saved player state for " + player.getName());

        // Replay izleme moduna gec
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(com.reportsystem.spigot.utils.AttributeCompat.maxHealth(player));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setAllowFlight(true);
        player.setFlying(true);

        // Kontrol itemlerini ver
        setControlItems(player, replayPlayer);

        // Progress bar olustur
        createProgressBar(player, replayPlayer);

        // Hotbar guncelleme task'i
        startHotbarUpdateTask(player, replayPlayer);

        plugin.debug("[REPLAY-CONTROL] Control items given to " + player.getName());
    }

    /**
     * Kontrol itemlerini hotbar'a yerlestirir
     */
    private void setControlItems(Player player, ReplayPlayer replayPlayer) {
        player.getInventory().setItem(0, createPlayPauseItem(replayPlayer.isPaused()));
        player.getInventory().setItem(1, createInfoItem(replayPlayer));
        player.getInventory().setItem(3, createRewindItem());
        player.getInventory().setItem(4, createTeleportItem());
        player.getInventory().setItem(5, createForwardItem());
        player.getInventory().setItem(7, createSpeedItem(replayPlayer.getPlaybackSpeed()));
        player.getInventory().setItem(8, createStopItem());
    }

    private ItemStack createRewindItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwner("MHF_ArrowLeft");
        meta.setDisplayName(msg().colorize(cfg().getString("items.rewind.name", "&c◀ 5 Saniye Geri")));
        List<String> lore = cfg().getStringList("items.rewind.lore");
        meta.setLore(lore.stream().map(msg()::colorize).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createForwardItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwner("MHF_ArrowRight");
        meta.setDisplayName(msg().colorize(cfg().getString("items.forward.name", "&a5 Saniye İleri ▶")));
        List<String> lore = cfg().getStringList("items.forward.lore");
        meta.setLore(lore.stream().map(msg()::colorize).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayPauseItem(boolean isPaused) {
        ItemStack item;
        ItemMeta meta;
        String key = isPaused ? "items.play" : "items.pause";

        if (isPaused) {
            item = new ItemStack(Material.LIME_DYE);
        } else {
            item = new ItemStack(Material.GRAY_DYE);
        }
        meta = item.getItemMeta();
        meta.setDisplayName(msg().colorize(cfg().getString(key + ".name", "")));
        List<String> lore = cfg().getStringList(key + ".lore");
        meta.setLore(lore.stream().map(msg()::colorize).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSpeedItem(double currentSpeed) {
        ItemStack item = new ItemStack(Material.RABBIT_FOOT);
        ItemMeta meta = item.getItemMeta();
        String speedStr = String.valueOf(currentSpeed);
        meta.setDisplayName(msg().colorize(cfg().getString("items.speed.name", "&b⚡ Hız: &f%speed%x")
                .replace("%speed%", speedStr)));
        List<String> lore = cfg().getStringList("items.speed.lore");
        meta.setLore(lore.stream().map(line -> msg().colorize(line
                .replace("%speed%", speedStr))).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(ReplayPlayer replayPlayer) {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        long elapsedSeconds = replayPlayer.getElapsedTime() / 1000;
        long totalSeconds = replayPlayer.getTotalTime() / 1000;
        String speedStr = String.valueOf(replayPlayer.getPlaybackSpeed());

        meta.setDisplayName(msg().colorize(cfg().getString("items.settings.name", "&eReplay Ayarları")));
        List<String> lore = cfg().getStringList("items.settings.lore");
        meta.setLore(lore.stream().map(line -> msg().colorize(line
                .replace("%player%", replayPlayer.getReplay().getRecordedPlayer())
                .replace("%elapsed%", formatTime(elapsedSeconds))
                .replace("%total%", formatTime(totalSeconds))
                .replace("%progress%", String.format("%.1f%%", replayPlayer.getProgress() * 100))
                .replace("%speed%", speedStr)
        )).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTeleportItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(msg().colorize(cfg().getString("items.teleport.name", "&d🌀 Işınlanma")));
        List<String> lore = cfg().getStringList("items.teleport.lore");
        meta.setLore(lore.stream().map(msg()::colorize).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStopItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(msg().colorize(cfg().getString("items.stop.name", "&c■ DURDUR")));
        List<String> lore = cfg().getStringList("items.stop.lore");
        meta.setLore(lore.stream().map(msg()::colorize).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Progress bar olusturur
     */
    private void createProgressBar(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        BossBar oldBar = progressBars.remove(playerUUID);
        if (oldBar != null) {
            oldBar.removeAll();
        }

        BossBar bossBar = plugin.getServer().createBossBar(
                ChatColor.GOLD + "Replay: " + ChatColor.WHITE + replayPlayer.getReplay().getRecordedPlayer(),
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );

        bossBar.addPlayer(player);
        bossBar.setProgress(0.0);
        progressBars.put(playerUUID, bossBar);
    }

    /**
     * Hotbar guncelleme task'i
     */
    private void startHotbarUpdateTask(Player player, ReplayPlayer replayPlayer) {
        UUID playerUUID = player.getUniqueId();

        BukkitRunnable oldTask = updateTasks.remove(playerUUID);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitRunnable updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || replayPlayer.getState() == ReplayPlayer.ReplayState.STOPPED) {
                    this.cancel();
                    updateTasks.remove(playerUUID);
                    return;
                }

                if (replayPlayer.getState() == ReplayPlayer.ReplayState.FINISHED) {
                    this.cancel();
                    updateTasks.remove(playerUUID);
                    return;
                }

                // 1. sahis modunda veya GUI acikken hotbar guncelleme
                boolean isFirstPerson = replayPlayer.getNpcManager() != null && replayPlayer.getNpcManager().isFirstPersonEnabled();
                boolean hasGuiOpen = player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING;

                if (!hasGuiOpen && !isFirstPerson) {
                    player.getInventory().setItem(0, createPlayPauseItem(replayPlayer.isPaused()));
                    player.getInventory().setItem(1, createInfoItem(replayPlayer));
                    player.getInventory().setItem(7, createSpeedItem(replayPlayer.getPlaybackSpeed()));
                }

                // Progress bar guncelle
                BossBar bossBar = progressBars.get(playerUUID);
                if (bossBar != null) {
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
                    bossBar.setColor(replayPlayer.isPaused() ? BarColor.RED : BarColor.YELLOW);
                }
            }
        };

        updateTasks.put(playerUUID, updateTask);
        updateTask.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Kontrol itemlerini kaldirir ve eski envanteri geri yukler
     */
    /**
     * Sadece replay UI'ini temizler (BossBar, update task, hotbar) - state geri yuklenmez.
     * Overwatch review icin kullanilir: oyuncu adventure modda ve ucuşta kalir.
     */
    public void stopReplayUI(Player player) {
        UUID playerUUID = player.getUniqueId();

        BukkitRunnable updateTask = updateTasks.remove(playerUUID);
        if (updateTask != null) updateTask.cancel();

        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        BossBar bossBar = progressBars.remove(playerUUID);
        if (bossBar != null) bossBar.removeAll();

        player.getInventory().clear();
        plugin.debug("[REPLAY-CONTROL] Stopped replay UI for " + player.getName() + " (state preserved)");
    }

    /**
     * Kaydedilmis state'i temizler (restore etmeden). Overwatch review bitisinde kullanilir.
     */
    public void clearSavedState(UUID playerUUID) {
        savedInventories.remove(playerUUID);
        savedGameModes.remove(playerUUID);
        savedHealth.remove(playerUUID);
        savedFoodLevels.remove(playerUUID);
        savedSaturation.remove(playerUUID);
        savedAllowFlight.remove(playerUUID);
        savedFlying.remove(playerUUID);
        savedFireTicks.remove(playerUUID);
        updateTasks.remove(playerUUID);
        progressBars.remove(playerUUID);
    }

    public void removeControlItems(Player player) {
        UUID playerUUID = player.getUniqueId();

        plugin.debug("[REPLAY-CONTROL] Starting cleanup for " + player.getName());

        BukkitRunnable updateTask = updateTasks.remove(playerUUID);
        if (updateTask != null) {
            updateTask.cancel();
        }

        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        BossBar bossBar = progressBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        restorePlayerState(player, playerUUID);

        plugin.debug("[REPLAY-CONTROL] Cleanup completed for " + player.getName());
    }

    /**
     * Oyuncu durumunu geri yukler (gamemode, can, yemek, fly, envanter)
     */
    private void restorePlayerState(Player player, UUID playerUUID) {
        GameMode savedGM = savedGameModes.remove(playerUUID);
        if (savedGM != null) {
            player.setGameMode(savedGM);
        }

        Boolean wasAllowFlight = savedAllowFlight.remove(playerUUID);
        Boolean wasFlying = savedFlying.remove(playerUUID);
        if (wasAllowFlight != null) {
            player.setAllowFlight(wasAllowFlight);
        }
        if (wasFlying != null && Boolean.TRUE.equals(wasAllowFlight)) {
            player.setFlying(wasFlying);
        }

        Double savedHP = savedHealth.remove(playerUUID);
        Integer savedFood = savedFoodLevels.remove(playerUUID);
        Float savedSat = savedSaturation.remove(playerUUID);
        Integer savedFire = savedFireTicks.remove(playerUUID);

        if (savedHP != null) {
            double maxHealth = com.reportsystem.spigot.utils.AttributeCompat.maxHealth(player);
            player.setHealth(Math.min(savedHP, maxHealth));
        }
        if (savedFood != null) player.setFoodLevel(savedFood);
        if (savedSat != null) player.setSaturation(savedSat);
        if (savedFire != null) player.setFireTicks(savedFire);

        ItemStack[] savedInventory = savedInventories.remove(playerUUID);
        if (savedInventory != null) {
            player.getInventory().clear();
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.getInventory().setContents(savedInventory);
                    player.updateInventory();
                }, 1L);
            } else {
                player.getInventory().setContents(savedInventory);
                player.updateInventory();
            }
        } else {
            player.getInventory().clear();
            player.updateInventory();
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * 1. sahis moduna girerken hotbar'i temizle
     * Cikis egilme tusu (sneak) ile yapilir, envantere ozel item konmaz
     */
    public void enterFirstPersonMode(Player player, ReplayPlayer replayPlayer) {
        player.getInventory().clear();
        // GUI kapanma eventi envanter guncellemeyi ezebilir, 1 tick sonra temizle
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.getInventory().clear();
            player.getInventory().setHeldItemSlot(0);
            player.updateInventory();

            // Envanter temizlendikten sonra NPC'nin el itemlerini viewer'a gonder
            // Bukkit clear() kendi equipment paketlerini gonderir, bizimkileri ezer
            // Bu yuzden clear SONRASI tekrar gondermemiz lazim
            if (replayPlayer.getNpcManager() != null) {
                replayPlayer.getNpcManager().resendFirstPersonEquipment(player);
            }
        }, 1L);
    }

    /**
     * 1. sahis modundan cikarken kontrol itemlerini geri ver ve NPC'nin yanina isinla
     */
    public void exitFirstPersonMode(Player player, ReplayPlayer replayPlayer) {
        // NPC'nin son konumunun yakinina isinla
        Location npcLoc = replayPlayer.getLastLocation();
        if (npcLoc != null) {
            Location tpLoc = npcLoc.clone().add(0, 2, 0);
            player.teleport(tpLoc);
        }

        player.getInventory().clear();
        setControlItems(player, replayPlayer);
        player.updateInventory();
    }

    /**
     * Tum progress barlari ve envanteri temizle
     */
    public void cleanup() {
        plugin.debug("[REPLAY-CONTROL] Starting full cleanup - " + progressBars.size() +
                " boss bars, " + savedInventories.size() + " inventories, " + updateTasks.size() + " tasks");

        for (Map.Entry<UUID, BukkitRunnable> entry : updateTasks.entrySet()) {
            BukkitRunnable task = entry.getValue();
            if (task != null) {
                task.cancel();
            }
        }
        updateTasks.clear();

        for (Map.Entry<UUID, BossBar> entry : progressBars.entrySet()) {
            BossBar bar = entry.getValue();
            if (bar != null) {
                bar.removeAll();
            }
        }
        progressBars.clear();

        for (UUID playerUUID : new HashMap<>(savedInventories).keySet()) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                restorePlayerState(player, playerUUID);
            }
        }
        savedInventories.clear();
        savedGameModes.clear();
        savedHealth.clear();
        savedFoodLevels.clear();
        savedSaturation.clear();
        savedAllowFlight.clear();
        savedFlying.clear();
        savedFireTicks.clear();

        plugin.debug("[REPLAY-CONTROL] Full cleanup completed");
    }
}
