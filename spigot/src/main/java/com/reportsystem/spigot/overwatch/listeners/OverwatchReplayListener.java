package com.reportsystem.spigot.overwatch.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.OverwatchVerdictGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Listener for Overwatch Replay Events
 * Replay bitince post-replay hotbar itemleri gosterir (Tekrar Izle / Karar Ver / Cik)
 */
public class OverwatchReplayListener implements Listener {

    private final ReportSystemSpigot plugin;
    private org.bukkit.scheduler.BukkitTask replayCheckTask;

    private final Map<UUID, OverwatchReviewData> activeReviews = new HashMap<>();
    private final Map<UUID, PlayerState> savedStates = new HashMap<>();
    private final Set<UUID> postReplayPlayers = new HashSet<>();

    public OverwatchReplayListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        startReplayCheckTask();
    }

    // ─── Public API ───

    /**
     * Oyuncu state'ini replay baslamadan ONCE kaydet.
     * OverwatchMenuGUI'den startReplay'den once cagrilir.
     */
    public void savePlayerState(Player player) {
        savedStates.put(player.getUniqueId(), new PlayerState(player));
        plugin.debug("[OVERWATCH-REPLAY] Saved original state for " + player.getName());
    }

    public void startReview(Player player, int reportId) {
        activeReviews.put(player.getUniqueId(), new OverwatchReviewData(reportId, System.currentTimeMillis()));
        plugin.debug("[OVERWATCH-REPLAY] Started review session for " + player.getName() +
                " (Report #" + reportId + ")");
    }

    public boolean isReviewing(UUID playerUUID) {
        return activeReviews.containsKey(playerUUID);
    }

    public boolean isInPostReplay(UUID playerUUID) {
        return postReplayPlayers.contains(playerUUID);
    }

    /**
     * Replay izliyor veya post-replay'de (koruma gerekli)
     */
    public boolean needsProtection(UUID playerUUID) {
        return activeReviews.containsKey(playerUUID);
    }

    public Integer getReviewingReportId(UUID playerUUID) {
        OverwatchReviewData data = activeReviews.get(playerUUID);
        return data != null ? data.reportId : null;
    }

    public boolean hasSavedState(UUID playerUUID) {
        return savedStates.containsKey(playerUUID);
    }

    /**
     * Oyuncuyu orijinal konumuna ve state'ine geri yukler.
     * Verdict sonrasi veya cikis icin kullanilir.
     */
    public void restoreAndReturn(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerState state = savedStates.remove(uuid);
        activeReviews.remove(uuid);
        postReplayPlayers.remove(uuid);

        // ReplayControlManager'daki stale veriyi temizle
        plugin.getReplayManager().getControlManager().clearSavedState(uuid);
        plugin.getReplayManager().clearOriginalLocation(uuid);

        if (state != null) {
            state.restore(player, plugin);
        }

        // Diger oyunculara tekrar goster
        showPlayerToAll(player);

        // Overwatch NPC'lerini tekrar gonder
        if (plugin.getNPCManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getNPCManager().showAllNPCsToPlayer(player);
                }
            }, 5L);
        }

        plugin.debug("[OVERWATCH-REPLAY] Restored state and returned " + player.getName());
    }

    // ─── Replay Check Task ───

    private void startReplayCheckTask() {
        this.replayCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerUUID : new HashMap<>(activeReviews).keySet()) {
                Player player = Bukkit.getPlayer(playerUUID);

                if (player == null || !player.isOnline()) {
                    activeReviews.remove(playerUUID);
                    savedStates.remove(playerUUID);
                    postReplayPlayers.remove(playerUUID);
                    continue;
                }

                OverwatchReviewData reviewData = activeReviews.get(playerUUID);
                if (reviewData == null) continue;

                // 10 dakika timeout - AFK korumasi
                long elapsedMs = System.currentTimeMillis() - reviewData.startTime;
                if (elapsedMs > 10 * 60 * 1000L) {
                    plugin.debug("[OVERWATCH-REPLAY] Review timeout for " + player.getName() +
                            " (Report #" + reviewData.reportId + ") - " + (elapsedMs / 1000) + "s elapsed");

                    // Aktif replay varsa durdur
                    if (plugin.getReplayManager().isWatchingReplay(player)) {
                        plugin.getReplayManager().stopReplay(player);
                    }

                    plugin.getOverwatchManager().unassignReport(reviewData.reportId);
                    player.sendMessage(plugin.getMessageManager().colorize(
                            "&c&l[OVERWATCH] &cInceleme zaman asimina ugradi (10 dakika)."));

                    activeReviews.remove(playerUUID);
                    postReplayPlayers.remove(playerUUID);

                    // State'i geri yukle
                    PlayerState state = savedStates.remove(playerUUID);
                    if (state != null) {
                        plugin.getReplayManager().getControlManager().clearSavedState(playerUUID);
                        plugin.getReplayManager().clearOriginalLocation(playerUUID);
                        state.restore(player, plugin);
                        showPlayerToAll(player);
                    }
                    continue;
                }

                // Post-replay'deyse bir sey yapma
                if (reviewData.state == ReviewState.POST_REPLAY) continue;

                boolean isWatching = plugin.getReplayManager().isWatchingReplay(player);

                if (reviewData.state == ReviewState.WAITING && isWatching) {
                    reviewData.state = ReviewState.WATCHING;
                } else if (reviewData.state == ReviewState.WATCHING && !isWatching) {
                    // Replay bitti - post-replay itemleri ver
                    reviewData.state = ReviewState.POST_REPLAY;

                    // NPC temizligi zaten tamamlandi (removeActiveReplay sonrasi tespit edildi)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && activeReviews.containsKey(playerUUID)) {
                            givePostReplayItems(player, reviewData);
                        }
                    }, 5L);
                }
                // WAITING + !isWatching: replay henuz baslamadi, bekle
            }
        }, 20L, 20L);
    }

    // ─── Post-Replay Items ───

    private void givePostReplayItems(Player player, OverwatchReviewData data) {
        postReplayPlayers.add(player.getUniqueId());

        // Adventure mod ve ucus (finish'ten sonra hala oyle olmali ama emin ol)
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);

        player.getInventory().clear();
        player.getInventory().setItem(2, createItem(Material.CLOCK,
                "&e\u25B6 Tekrar \u0130zle", "&7Replay'i ba\u015Ftan izleyin"));
        player.getInventory().setItem(4, createItem(Material.BOOK,
                "&a\u2714 Karar Ver", "&7Verdict ekran\u0131n\u0131 a\u00E7\u0131n"));
        player.getInventory().setItem(6, createItem(Material.BARRIER,
                "&c\u2716 \u00C7\u0131k", "&7\u0130ncelemeyi iptal edin"));

        String msg = plugin.getMessageManager().getMessage("overwatch.review.replay-completed");
        player.sendMessage(plugin.getMessageManager().colorize(msg));
        player.sendTitle(
                ChatColor.GOLD + "\u25A0 Replay Bitti",
                ChatColor.YELLOW + "Tekrar izleyebilir veya karar verebilirsiniz",
                10, 60, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        plugin.debug("[OVERWATCH-REPLAY] Post-replay items given to " + player.getName());
    }

    private ItemStack createItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Collections.singletonList(ChatColor.translateAlternateColorCodes('&', lore)));
        item.setItemMeta(meta);
        return item;
    }

    // ─── Click Handlers ───

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!postReplayPlayers.contains(player.getUniqueId())) return;

        event.setCancelled(true);

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType() == Material.AIR) return;

        OverwatchReviewData data = activeReviews.get(player.getUniqueId());
        if (data == null) return;

        switch (slot) {
            case 2: // Tekrar Izle
                rewatchReplay(player, data);
                break;
            case 4: // Karar Ver
                openVerdict(player, data);
                break;
            case 6: // Cik
                exitReview(player, data);
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (postReplayPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ─── Actions ───

    private void rewatchReplay(Player player, OverwatchReviewData data) {
        postReplayPlayers.remove(player.getUniqueId());
        data.state = ReviewState.WAITING;

        player.getInventory().clear();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // Orijinal location'i koru
        PlayerState originalState = savedStates.get(player.getUniqueId());
        Location realOriginal = originalState != null ? originalState.location : null;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            plugin.getReplayManager().startReplay(data.reportId, player).thenAccept(started -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (started) {
                        // startReplay yanlis konum kaydetti, gercek orijinali geri koy
                        if (realOriginal != null) {
                            plugin.getReplayManager().setOriginalLocation(player.getUniqueId(), realOriginal);
                        }
                        plugin.debug("[OVERWATCH-REPLAY] Rewatch started for " + player.getName() +
                                " (Report #" + data.reportId + ")");
                    } else {
                        // Basarisiz - post-replay'e geri don
                        data.state = ReviewState.POST_REPLAY;
                        givePostReplayItems(player, data);
                        player.sendMessage(plugin.getMessageManager().colorize(
                                plugin.getMessageManager().getMessage("overwatch.review.replay-failed")));
                    }
                });
            });
        }, 5L);
    }

    private void openVerdict(Player player, OverwatchReviewData data) {
        postReplayPlayers.remove(player.getUniqueId());
        // activeReviews'dan KALDIRMA - verdict GUI close handler kontrol ediyor
        // verdictSubmitted false ise geri post-replay'e doner

        player.getInventory().clear();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        new OverwatchVerdictGUI(plugin, player, data.reportId, data.startTime).open();
    }

    private void exitReview(Player player, OverwatchReviewData data) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);

        // Raporu queue'ya geri birak
        plugin.getOverwatchManager().unassignReport(data.reportId);
        player.sendMessage(plugin.getMessageManager().colorize(
                plugin.getMessageManager().getMessage("overwatch.verdict.cancelled")));

        restoreAndReturn(player);
    }

    /**
     * Verdict GUI submit olmadan kapatilirsa post-replay'e geri don.
     * OverwatchGUIListener'dan cagrilir.
     */
    public void returnToPostReplay(Player player) {
        OverwatchReviewData data = activeReviews.get(player.getUniqueId());
        if (data != null) {
            data.state = ReviewState.POST_REPLAY;
            givePostReplayItems(player, data);
        }
    }

    // ─── Lifecycle ───

    public void cleanup() {
        if (replayCheckTask != null) {
            replayCheckTask.cancel();
            replayCheckTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        if (activeReviews.containsKey(playerUUID)) {
            OverwatchReviewData data = activeReviews.remove(playerUUID);
            if (data != null) {
                plugin.getOverwatchManager().unassignReport(data.reportId);
            }
            plugin.debug("[OVERWATCH-REPLAY] Player " + event.getPlayer().getName() +
                    " quit during review - review cancelled");
        }

        savedStates.remove(playerUUID);
        postReplayPlayers.remove(playerUUID);

        if (plugin.getNPCManager() != null) {
            plugin.getNPCManager().clearSelection(playerUUID);
        }
    }

    // ─── Helpers ───

    private void showPlayerToAll(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.showPlayer(plugin, player);
                player.showPlayer(plugin, online);
            }
        }
    }

    // ─── Inner Classes ───

    private enum ReviewState {
        WAITING,     // Replay henuz activeReplays'a eklenmedi
        WATCHING,    // Replay aktif izleniyor
        POST_REPLAY  // Replay bitti, karar bekleniyor
    }

    private static class OverwatchReviewData {
        private final int reportId;
        private final long startTime;
        private ReviewState state = ReviewState.WAITING;

        public OverwatchReviewData(int reportId, long startTime) {
            this.reportId = reportId;
            this.startTime = startTime;
        }
    }

    /**
     * Oyuncunun replay oncesi state'i (konum, envanter, gamemode vs.)
     */
    private static class PlayerState {
        final Location location;
        final ItemStack[] inventory;
        final GameMode gameMode;
        final double health;
        final int foodLevel;
        final float saturation;
        final boolean allowFlight;
        final boolean flying;
        final int fireTicks;

        PlayerState(Player player) {
            this.location = player.getLocation().clone();
            this.inventory = player.getInventory().getContents().clone();
            this.gameMode = player.getGameMode();
            this.health = player.getHealth();
            this.foodLevel = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.allowFlight = player.getAllowFlight();
            this.flying = player.isFlying();
            this.fireTicks = player.getFireTicks();
        }

        void restore(Player player, ReportSystemSpigot plugin) {
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(flying);
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            player.setHealth(Math.min(health, maxHealth));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setFireTicks(fireTicks);
            player.setFallDistance(0f);

            player.getInventory().clear();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().setContents(inventory);
                    player.updateInventory();
                    player.teleport(location);
                    player.setFallDistance(0f);
                }
            }, 1L);
        }
    }
}
