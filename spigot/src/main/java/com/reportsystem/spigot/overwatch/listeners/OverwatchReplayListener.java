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
import org.bukkit.event.player.PlayerMoveEvent;
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

    // savePlayerState ile konumu alinmis ama envanteri/durumu HENUZ yakalanmamis oyuncular.
    // Envanter, replay'in envanteri gercekten temizledigi tick'te (giveControlItems) yakalanir;
    // bu kume doluyken savedStates'teki envanter kopyasi GECERSIZDIR ve geri yazilmaz.
    private final Set<UUID> pendingCapture = new HashSet<>();

    public OverwatchReplayListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        startReplayCheckTask();
    }

    // ─── Public API ───

    /**
     * Inceleme baslatilirken (OverwatchMenuGUI, startReplay'den ONCE) cagrilir.
     * Yalniz BASLANGIC KONUMU kesinlesir; envanter/durum "bekliyor" isaretlenir ve
     * {@link #captureIfPending(Player)} ile envanterin gercekten temizlendigi tick'te
     * (ReplayControlManager.giveControlItems) yakalanir. Arada gecen sure (20 tick + async
     * yukleme + dunya degisimi) korumasiz oldugu icin anlik goruntu burada ALINMAZ:
     * alinsaydi oyuncu o pencerede esyalari sandiga koyup sonunda geri alabilirdi (dupe).
     * PDC yedegi de burada yazilmaz (envanter henuz degismedi).
     */
    public void savePlayerState(Player player) {
        // IDEMPOTENT: inceleme suruyorsa ilk kayit korunur ("Tekrar Izle" bunu cagirmaz).
        UUID uuid = player.getUniqueId();
        if (savedStates.putIfAbsent(uuid, new PlayerState(player)) == null) {
            pendingCapture.add(uuid);
        }
        plugin.debug("[OVERWATCH-REPLAY] Saved original location for " + player.getName() + " (inventory pending)");
    }

    /**
     * ReplayControlManager.giveControlItems icinden, envanter temizlenmeden HEMEN once
     * cagrilir. Bekleyen bir Overwatch kaydi varsa envanter/durum O ANKI degerlerle yakalanir
     * (konum savePlayerState anindaki baslangic noktasi kalir) ve PDC yedegi OWNER_OVERWATCH
     * ile yazilir. Bekleyen kayit yoksa ("Tekrar Izle", normal replay) hicbir sey yapmaz.
     *
     * @return yakalama yapildiysa true
     */
    public boolean captureIfPending(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingCapture.remove(uuid)) return false;
        PlayerState old = savedStates.get(uuid);
        if (old == null) return false;
        savedStates.put(uuid, new PlayerState(player, old.location));
        if (plugin.getViewerBackup() != null) {
            plugin.getViewerBackup().saveIfAbsent(player,
                    com.reportsystem.spigot.replay.ViewerBackup.OWNER_OVERWATCH, old.location);
        }
        plugin.debug("[OVERWATCH-REPLAY] Captured real inventory for " + player.getName() + " at clear time");
        return true;
    }

    /**
     * savedStates'ten kaydi alir. Envanteri hic yakalanmamis (bekleyen) kayit icin null doner:
     * o kopya gecersizdir, geri yazilirsa pencerede baska yere konan esyalar cogalir.
     */
    private PlayerState takeCapturedState(UUID uuid) {
        PlayerState state = savedStates.remove(uuid);
        return pendingCapture.remove(uuid) ? null : state;
    }

    /**
     * ReplayControlManager kendi kopyasindan envanteri geri yazdiktan HEMEN sonra cagrilir.
     * Overwatch kaydi var ama inceleme henuz baslamadiysa, yakalamayi o arada baslayip biten
     * BASKA bir replay yapmis demektir: o kopya artik bayat. Bekleyen duruma geri al ve PDC'deki
     * OWNER_OVERWATCH yedegini sil (envanter az once gercek haliyle geri yazildi); Overwatch'in
     * kendi replay'i baslarken yeniden yakalanir.
     */
    public void onReplayInventoryRestored(Player player) {
        UUID uuid = player.getUniqueId();
        if (!savedStates.containsKey(uuid) || activeReviews.containsKey(uuid) || pendingCapture.contains(uuid)) {
            return;
        }
        pendingCapture.add(uuid);
        if (plugin.getViewerBackup() != null) {
            plugin.getViewerBackup().clear(player, com.reportsystem.spigot.replay.ViewerBackup.OWNER_OVERWATCH);
        }
        plugin.debug("[OVERWATCH-REPLAY] Foreign replay restored inventory of " + player.getName() +
                " before review start; capture reset to pending");
    }

    /** Replay hic baslamadiysa kaydi restore etmeden birak (envantere dokunma). */
    public void dropSavedState(UUID playerUUID) {
        savedStates.remove(playerUUID);
        pendingCapture.remove(playerUUID);
        Player p = Bukkit.getPlayer(playerUUID);
        if (p != null && plugin.getViewerBackup() != null) {
            if (plugin.getReplayManager() != null
                    && plugin.getReplayManager().getControlManager().hasSavedState(playerUUID)) {
                // Arada baska bir replay (ör. normal replay) envanteri temizledi ve gercek
                // kopyayi RAM'de tutuyor: tek PDC yedegini SILME, sahipligini o replay'e devret.
                // ControlManager bitiste clear(OWNER_REPLAY) ile temizler.
                plugin.getViewerBackup().retag(p,
                        com.reportsystem.spigot.replay.ViewerBackup.OWNER_OVERWATCH,
                        com.reportsystem.spigot.replay.ViewerBackup.OWNER_REPLAY);
            } else {
                plugin.getViewerBackup().clear(p, com.reportsystem.spigot.replay.ViewerBackup.OWNER_OVERWATCH);
            }
        }
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
        // Post-replay asamasi da korunmali: Saat/Kitap/Bariyer gercek ItemStack'ler,
        // koruma kalkarsa oyuncu bunlari yere atip dunyaya sokabiliyordu.
        return activeReviews.containsKey(playerUUID) || postReplayPlayers.contains(playerUUID);
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

        // TEK MERKEZ: Overwatch incelemesinin tum bitisleri (karar, Cik, zaman asimi)
        // buradan gecer → anti-cheat muafiyeti burada kalkar.
        if (plugin.getAntiCheatBypassManager() != null) {
            plugin.getAntiCheatBypassManager().unexempt(player);
        }

        // Envanteri hic yakalanmamis (bekleyen) kayit null doner → envanter geri YAZILMAZ
        PlayerState state = takeCapturedState(uuid);
        activeReviews.remove(uuid);
        postReplayPlayers.remove(uuid);

        plugin.getReplayManager().clearOriginalLocation(uuid);

        // SIRA KRITIK: eskiden clearSavedState() burada, restore'dan ONCE cagriliyordu.
        // clearSavedState envanteri geri YUKLEMEDEN siliyor; kendi savedStates'imiz de
        // hic doldurulmadigi icin (savePlayerState cagrilmiyordu) oyuncu bombos kaliyordu.
        if (state != null) {
            // Bizim kopyamiz gercek envanteri tasiyor. ControlManager'in kopyasi
            // rewatch sirasinda bozulmus olabilecegi icin onu restore ETMEDEN dusur.
            plugin.getReplayManager().getControlManager().clearSavedState(uuid);
            state.restore(player, plugin);
        } else {
            // YEDEK YOL: ControlManager replay basinda (giveControlItems) envanteri
            // kaydetmisti. Kendi kaydimiz yoksa onu kullan - silme.
            plugin.getReplayManager().getControlManager().removeControlItems(player);
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
                    pendingCapture.remove(playerUUID);
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

                    // Tek merkez: state geri yukleme (state null ise ControlManager kopyasi),
                    // gorunurluk, NPC'leri yeniden gosterme ve anti-cheat muafiyetinin
                    // kaldirilmasi restoreAndReturn'de.
                    restoreAndReturn(player);
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
        // H-47: post-replay konum capasi. Yalniz yoksa alinir: karar GUI'si kapatilip geri
        // donuldugunde (returnToPostReplay) arada kayan konum capayi tasimasin.
        // "Tekrar Izle" capayi sifirlar → yeni replay bitince yeni konumda capalanir.
        if (data.postAnchor == null) {
            data.postAnchor = player.getLocation().clone();
        }
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

    /**
     * H-47: post-replay asamasinda (ADVENTURE + ucus + gizli, 10 dk'ya kadar) oyuncu capadan
     * 1.5 bloktan fazla uzaklasamaz; farkli dunyaya gecmisse de capaya geri cekilir.
     * Bakis acisi serbest. Ucus acik kalir (konum sabit; dusup gezinme yok).
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!postReplayPlayers.contains(uuid)) return;
        OverwatchReviewData data = activeReviews.get(uuid);
        if (data == null || data.postAnchor == null || data.postAnchor.getWorld() == null) return;
        Location to = event.getTo();
        if (to == null) return;

        Location anchor = data.postAnchor;
        if (!Objects.equals(anchor.getWorld(), to.getWorld()) || to.distanceSquared(anchor) > 2.25) {
            Location b = anchor.clone();
            b.setYaw(to.getYaw());
            b.setPitch(to.getPitch());
            event.setTo(b);
        }
    }

    // ─── Actions ───

    private void rewatchReplay(Player player, OverwatchReviewData data) {
        postReplayPlayers.remove(player.getUniqueId());
        data.state = ReviewState.WAITING;
        data.postAnchor = null;

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

        // Plugin kapanirken inceleme yapan herkesin envanterini iade et.
        // Yoksa /reload veya sunucu kapanisinda esyalar kaybolur.
        for (UUID uuid : new HashSet<>(savedStates.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            // Bekleyen (envanteri yakalanmamis) kayit geri yazilmaz: envanter hic degismedi
            PlayerState state = takeCapturedState(uuid);
            if (player != null && state != null) {
                try {
                    state.restore(player, plugin, true);
                } catch (Throwable ignored) {
                    // kapanista tek bir oyuncunun hatasi digerlerini engellemesin
                }
            }
        }
        activeReviews.clear();
        postReplayPlayers.clear();
        pendingCapture.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();

        // Tutarlilik: muafiyeti burada da kaldir (idempotent; asil temizlik ReplayManager.handlePlayerQuit)
        if (plugin.getAntiCheatBypassManager() != null) {
            plugin.getAntiCheatBypassManager().unexempt(playerUUID);
        }

        if (activeReviews.containsKey(playerUUID)) {
            OverwatchReviewData data = activeReviews.remove(playerUUID);
            if (data != null) {
                plugin.getOverwatchManager().unassignReport(data.reportId);
            }
            plugin.debug("[OVERWATCH-REPLAY] Player " + event.getPlayer().getName() +
                    " quit during review - review cancelled");
        }

        // ★ ENVANTER KAYBI KORUMASI
        // Eskiden burada kayit sadece SILINIYORDU. Bukkit, quit olayindan sonra
        // oyuncunun O ANKI envanterini (replay kontrol esyalari ya da bos) diske
        // yaziyor; gercek esyalar kalici olarak yok oluyordu. Oyuncu cikarken
        // envanteri SENKRON geri yukleniyor ki diske dogru hali yazilsin.
        // Bekleyen (envanteri yakalanmamis) kayit null doner → bayat kopya geri yazilmaz
        PlayerState state = takeCapturedState(playerUUID);
        postReplayPlayers.remove(playerUUID);

        if (state != null) {
            plugin.getReplayManager().getControlManager().clearSavedState(playerUUID);
            state.restore(event.getPlayer(), plugin, true);
        } else {
            // Yedek yol: ControlManager'in replay basinda aldigi kopya.
            // O metot artik senkron yaziyor.
            plugin.getReplayManager().getControlManager().removeControlItems(event.getPlayer(), true);
        }

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
        // H-47: post-replay konum capasi (givePostReplayItems'ta alinir, rewatch'ta sifirlanir)
        private Location postAnchor;

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
            this(player, player.getLocation());
        }

        /** Envanter/durum o anki degerlerle; konum verilen (baslangic) konum. */
        PlayerState(Player player, Location originalLocation) {
            this.location = originalLocation.clone();
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
            restore(player, plugin, false);
        }

        void restore(Player player, ReportSystemSpigot plugin, boolean sync) {
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            if (allowFlight) player.setFlying(flying);
            double maxHealth = com.reportsystem.spigot.utils.AttributeCompat.maxHealth(player);
            player.setHealth(Math.min(health, maxHealth));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setFireTicks(fireTicks);
            player.setFallDistance(0f);

            player.getInventory().clear();

            // Oyuncu cikiyorsa (sync) ya da plugin kapaniyorsa 1 tik ERTELEME calismaz:
            // gorev hic kosmaz, Bukkit bos envanteri diske yazar ve esyalar gider.
            // Bu iki durumda senkron yaz; sadece normal akista ertele (teleport
            // ve envanterin ayni tikta catismamasi icin).
            // PDC yedegi (ViewerBackup) yalniz envanter GERCEKTEN geri yazildiktan sonra,
            // ayni blokta silinir. Ertelenmis gorevde oyuncu cevrimdisiysa yedek kalir →
            // sonraki giriste geri yuklenir.
            if (!sync && plugin.isEnabled() && player.isOnline()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.getInventory().setContents(inventory);
                        player.updateInventory();
                        if (plugin.getViewerBackup() != null) {
                            plugin.getViewerBackup().clearAny(player);
                        }
                        player.teleport(location);
                        player.setFallDistance(0f);
                    }
                }, 1L);
            } else {
                player.getInventory().setContents(inventory);
                player.updateInventory();
                if (plugin.getViewerBackup() != null) {
                    plugin.getViewerBackup().clearAny(player);
                }
                player.teleport(location);
                player.setFallDistance(0f);
            }
        }
    }
}
