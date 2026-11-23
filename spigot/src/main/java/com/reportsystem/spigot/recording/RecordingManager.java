package com.reportsystem.spigot.recording;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.database.ReplayDAO;
import com.reportsystem.common.models.Replay;
import com.reportsystem.common.replay.ReplaySerializer;
import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.EntityUpdateAction;
import com.reportsystem.common.replay.actions.LocationAction;
import com.reportsystem.common.replay.actions.PlayerInfoAction;
import com.reportsystem.spigot.utils.SkinUtils;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.reportsystem.spigot.ReportSystemSpigot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RecordingManager {

    private final ReportSystemSpigot plugin;
    private final ReplayDAO replayDAO;
    private final Map<UUID, RecordingSession> activeRecordings = new ConcurrentHashMap<>();
    private final Map<UUID, RecordingInfo> recordingInfoMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> entityTrackingTasks = new ConcurrentHashMap<>();
    // Süre-sonu durdurma task'ları — iptal edilebilmeleri için ID saklanır (aksi halde
    // aynı oyuncuya gelen 2. rapor, 1. kaydın timer'ı tarafından erken kesiliyordu).
    private final Map<UUID, Integer> stopTasks = new ConcurrentHashMap<>();
    private final Map<UUID, NearbyPlayerTracker> nearbyPlayerTrackers = new ConcurrentHashMap<>();

    // H-27: çökme/kill'e karşı periyodik disk checkpoint'i + açılışta kurtarma
    private final RecordingJournal journal;
    private final boolean crashRecoveryEnabled;
    private org.bukkit.scheduler.BukkitTask checkpointTask;

    public RecordingManager(ReportSystemSpigot plugin, ReplayDAO replayDAO) {
        this.plugin = plugin;
        this.replayDAO = replayDAO;
        this.journal = new RecordingJournal(plugin);
        this.crashRecoveryEnabled = plugin.getConfig().getBoolean("replay.crash-recovery.enabled", true);

        if (crashRecoveryEnabled) {
            // Önceki çökmeden kalan günlükleri DB'ye taşı (dosya + DB işi yalnız journal executor'ında)
            journal.recoverAsync(replayDAO, plugin.getConfig().getInt("replay.max-file-size", 5));

            int intervalSeconds = Math.max(2, plugin.getConfig().getInt("replay.crash-recovery.interval-seconds", 10));
            long intervalTicks = intervalSeconds * 20L;
            this.checkpointTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin, this::checkpointActiveRecordings, intervalTicks, intervalTicks);
        } else {
            // F-K5: kapalıyken eski günlükler işlenmez; varsa bir kez uyar
            journal.warnIfLeftoverAsync();
        }
    }

    /**
     * Gerçek (DB'ye yazılacak) rapor ID'si mi? 0/negatif geçersiz, 9999 manuel kayıt.
     */
    private static boolean isRealReportId(int id) {
        return id > 0 && id != 9999;
    }

    /**
     * Async zamanlayıcıdan çağrılır: her aktif kaydın anlık kopyasını günlüğe yazar.
     * Kopya burada (kısa kilitle) alınır; serileştirme + dosya G/Ç journal executor'ında.
     */
    private void checkpointActiveRecordings() {
        for (Map.Entry<UUID, RecordingInfo> entry : recordingInfoMap.entrySet()) {
            try {
                RecordingInfo info = entry.getValue();
                List<Integer> ids = info.getAllReportIds();
                if (ids.isEmpty()) {
                    continue;
                }
                RecordingSession session = info.getSession();
                List<com.reportsystem.common.replay.actions.ReplayAction> live = session.getActions();
                List<com.reportsystem.common.replay.actions.ReplayAction> snapshot;
                synchronized (live) {
                    snapshot = new ArrayList<>(live);
                }
                if (snapshot.isEmpty()) {
                    continue;
                }
                journal.checkpointAsync(entry.getKey(), ids, session.getRecordedPlayerName(),
                        session.getWorldName(), session.getStartTime(), snapshot);
            } catch (Exception e) {
                plugin.getLogger().warning("[RECORDING] Checkpoint alınamadı: " + e.getMessage());
            }
        }
    }

    /**
     * Oyuncu için kayıt başlatır
     */
    public void startRecording(Player player, int reportId, int durationSeconds, String reporterName, String reason) {
        startRecording(player, reportId, durationSeconds, reporterName, reason, null);
    }

    /**
     * Oyuncu için kayıt başlatır (dünya override desteği ile)
     * 
     * @param overrideWorldName /report anındaki dünya adı - GUI gecikmesi nedeniyle
     *                          oyuncu dünya değiştirebilir
     */
    public void startRecording(Player player, int reportId, int durationSeconds, String reporterName, String reason,
            String overrideWorldName) {
        UUID playerUUID = player.getUniqueId();

        // PERFORMANS: Eşzamanlı kayıt sınırı kontrolü
        int maxRecordings = plugin.getConfigManager().getMaxRecordings();
        if (activeRecordings.size() >= maxRecordings && !activeRecordings.containsKey(playerUUID)) {
            plugin.getLogger().warning("[RECORDING] Eşzamanlı kayıt limiti aşıldı (" + maxRecordings +
                    "). " + player.getName() + " için kayıt başlatılamıyor.");
            return;
        }

        // Zaten kayıt var mı kontrol et
        if (recordingInfoMap.containsKey(playerUUID)) {
            plugin.getLogger()
                    .warning("[RECORDING] " + player.getName() + " için zaten aktif kayıt var, önce durdurulacak.");
            // PERFORMANS: .join() ana thread'i bloklar — async callback kullan.
            // F-Ö3: süre dolmadan kesilen eski kaydın raporları için de bildirim gönderilir.
            stopRecording(playerUUID, true).thenRun(() -> {
                plugin.getLogger().info("[RECORDING] Eski kayıt durduruldu: " + player.getName());
            }).exceptionally(e -> {
                plugin.getLogger().warning("[RECORDING] Eski kayıt durdurulurken hata: " + e.getMessage());
                return null;
            });
        }

        plugin.getLogger().info("[RECORDING] Kayıt başlatılıyor: " + player.getName() +
                " (Initial Report ID: " + reportId + ") - Süre: " + durationSeconds + " saniye");

        // Session oluştur (H-07: aksiyon tavanı + tip başına saniyelik sınır; <= 0 → sınırsız)
        RecordingSession session = new RecordingSession(player.getName(),
                plugin.getConfig().getInt("replay.limits.max-actions", 150000),
                plugin.getConfig().getInt("replay.limits.max-actions-per-type-per-second", 400));
        // /report anındaki dünyayı kullan (oyuncu GUI açıkken ölüp dünya
        // değiştirebilir)
        String worldName = (overrideWorldName != null) ? overrideWorldName : player.getWorld().getName();
        session.setWorldName(worldName);
        session.setStartTime(System.currentTimeMillis());

        // İlk konum ve ekipmanları kaydet
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            LocationAction initialLocation = new LocationAction(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch(),
                    player.isOnGround());
            session.addAction(initialLocation);

            recordAllEquipment(player, session);
            recordFullInventory(player, session);
            plugin.getLogger().info("[RECORDING] Initial equipment and inventory recorded for " + player.getName());
        }, 2L);

        // Packet listener oluştur ve kaydet
        ReplayPacketListener listener = new ReplayPacketListener(session, player, plugin);
        session.setPacketListener(listener);

        // PacketEvents'e listener'ı ekle
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        plugin.getLogger().info("[RECORDING] PacketListener kaydedildi: " + player.getName());

        // RecordingInfo oluştur ve kaydet
        RecordingInfo info = new RecordingInfo(reportId, session, reporterName, reason);
        recordingInfoMap.put(playerUUID, info);
        activeRecordings.put(playerUUID, session);

        plugin.getLogger().info("[RECORDING] Aktif kayıt sayısı: " + activeRecordings.size());

        // Entity tracking task başlat (her 10 tick = 0.5 saniye)
        int trackingTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> trackSpawnedEntities(session),
                20L, // 1 saniye sonra başla
                10L // Her 0.5 saniyede bir çalıştır
        );
        entityTrackingTasks.put(playerUUID, trackingTaskId);
        plugin.getLogger().info("[RECORDING] Entity tracking task başlatıldı: #" + trackingTaskId);

        // NearbyPlayerTracker başlat (yeni gelişmiş sistem)
        NearbyPlayerTracker nearbyTracker = new NearbyPlayerTracker(plugin, session, player);
        nearbyTracker.start();
        nearbyPlayerTrackers.put(playerUUID, nearbyTracker);
        plugin.getLogger().info("[RECORDING] NearbyPlayerTracker başlatıldı: " + player.getName());

        // Belirtilen süre sonra durdurmak için task oluştur.
        // Task ID'sini sakla → stopRecording bunu iptal edebilsin (erken/çift durdurma önlenir).
        org.bukkit.scheduler.BukkitTask stopTask = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    stopTasks.remove(playerUUID); // kendi kendine tetiklendi, kaydı temizle
                    plugin.getLogger().info("[RECORDING] Kayıt süresi doldu, durduruluyor: " + player.getName());
                    // Bildirimler stopRecording içinde (notifyReports=true) tek noktadan gönderilir;
                    // kaydın DB'ye yazılma başarısına bağlı değildir (F-Ö3).
                    stopRecording(playerUUID, true);
                },
                durationSeconds * 20L);
        stopTasks.put(playerUUID, stopTask.getTaskId());
    }

    /**
     * Açık kayda rapor ID'si bağlar (H-40: EZMEZ). Birincil ID yoksa (<= 0) birincil
     * olur; aksi halde ek rapor olarak eklenir — kayıt bitince aynı replay her ID'ye yazılır.
     * Proxy yolu (ReportSystemSpigot.updateRecordingReportId) bu metodu kullanır.
     */
    public void updateReportId(UUID playerUUID, int newReportId) {
        attachReport(playerUUID, newReportId, false);
    }

    /**
     * Açık kayda rapor bağlar.
     *
     * @param notifyOnFinish true ise bu rapor için yetkili/webhook/proxy bildirimi kayıt
     *                       bitince gönderilir (bildirimi henüz yapılmamış yollar için)
     * @return kayıt bulunup bağlandıysa true
     */
    public boolean attachReport(UUID playerUUID, int reportId, boolean notifyOnFinish) {
        if (reportId <= 0) {
            plugin.getLogger().warning("[RECORDING] Geçersiz rapor ID'si bağlanamaz: " + reportId);
            return false;
        }
        RecordingInfo info = recordingInfoMap.get(playerUUID);
        if (info == null) {
            plugin.getLogger().warning("[RECORDING] Rapor #" + reportId + " bağlanacak aktif kayıt bulunamadı: " + playerUUID);
            return false;
        }
        synchronized (info) {
            if (info.reportId <= 0) {
                int oldId = info.reportId;
                info.reportId = reportId;
                plugin.getLogger().info("[RECORDING] Birincil rapor ID atandı: " + oldId + " -> " + reportId);
            } else if (reportId != info.reportId && !info.extraReportIds.contains(reportId)) {
                info.extraReportIds.add(reportId);
                plugin.getLogger().info("[RECORDING] Açık kayda ek rapor bağlandı: #" + reportId
                        + " (birincil #" + info.reportId + ")");
            }
            if (notifyOnFinish) {
                info.notifyOnFinish.add(reportId);
            }
        }
        return true;
    }

    /**
     * Kayıt bitince (süre sonu / çıkış / AC yeniden başlatma) bildirimleri gönderir: birincil
     * rapor (kaydı başlatan bilgilerle) + notifyOnFinish isteyen ek raporlar (DB'den okunur).
     * Herhangi bir thread'den çağrılabilir; Bukkit işleri runTask ile ana thread'e alınır.
     */
    private void sendFinishNotifications(RecordingInfo info, String targetName) {
        if (info == null || !plugin.isEnabled()) {
            return;
        }
        int primary = info.getReportId();
        if (isRealReportId(primary)) {
            plugin.getLogger().info("[RECORDING] Kayıt bitti, bildirim gönderiliyor (Rapor #" + primary + ")");
            dispatchReportNotification(primary, targetName, info.getReporterName(), info.getReason(), null);
        }

        List<Integer> extra = new ArrayList<>();
        for (Integer id : info.notifyOnFinish) {
            if (id != null && id != primary && isRealReportId(id) && !extra.contains(id)) {
                extra.add(id);
            }
        }
        if (extra.isEmpty()) {
            return;
        }
        // Ek raporların raporlayan/sebep bilgisi kayıtta yok → DB'den async oku
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int id : extra) {
                com.reportsystem.common.models.Report report = plugin.getReportService().getReportById(id);
                if (report == null) {
                    plugin.getLogger().warning("[RECORDING] Bildirim için rapor #" + id + " okunamadı.");
                    continue;
                }
                String reporter = report.getReporterName() != null ? report.getReporterName() : report.getReporter();
                dispatchReportNotification(id, targetName, reporter != null ? reporter : "",
                        report.getReason() != null ? report.getReason() : "", report);
            }
        });
    }

    /**
     * Tek rapor için yetkili toast + Discord webhook + proxy bildirimi.
     *
     * @param prefetched DB'den zaten okunmuş rapor (webhook için); null ise async okunur
     */
    private void dispatchReportNotification(int reportId, String targetName, String reporterName, String reason,
            com.reportsystem.common.models.Report prefetched) {
        if (!plugin.isEnabled()) {
            return;
        }
        // Yetkililere toast bildirim (messages dosyasından okunur) — ana thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getReportCommand() != null) {
                plugin.getReportCommand().notifyStaff(reporterName, targetName, reason, reportId);
            }
        });

        // Discord webhook bildirimi — async
        if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                com.reportsystem.common.models.Report report = prefetched != null ? prefetched
                        : plugin.getReportService().getReportById(reportId);
                if (report != null) {
                    plugin.getLogger().info("[Webhook] Sending new report notification for report #" + reportId);
                    plugin.getWebhookManager().sendNewReportNotification(report);
                } else {
                    plugin.getLogger().warning("[Webhook] Could not fetch report #" + reportId + " from database!");
                }
            });
        }

        // BungeeCord'a bildirim (network-wide) — oyuncu listesi + plugin mesajı ana thread'de
        if (plugin.getConfigManager().isBungeeCordEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> sendNotificationToBungee(reportId, targetName, reporterName, reason));
        }
    }

    /**
     * Spawned entity'lerin pozisyonlarını track eder ve günceller
     */
    private void trackSpawnedEntities(RecordingSession session) {
        Map<UUID, org.bukkit.entity.Entity> spawnedEntities = session.getSpawnedEntities();
        Map<UUID, org.bukkit.Location> lastLocations = session.getLastEntityLocations();

        // Her spawned entity için
        for (Map.Entry<UUID, org.bukkit.entity.Entity> entry : spawnedEntities.entrySet()) {
            UUID entityUUID = entry.getKey();
            org.bukkit.entity.Entity entity = entry.getValue();

            // Entity hala geçerli mi?
            if (entity == null || !entity.isValid()) {
                session.removeSpawnedEntity(entityUUID);
                continue;
            }

            org.bukkit.Location currentLoc = entity.getLocation();
            org.bukkit.Location lastLoc = lastLocations.get(entityUUID);

            // Pozisyon değişti mi? (0.1 blok threshold)
            if (lastLoc == null || !lastLoc.getWorld().equals(currentLoc.getWorld())
                    || lastLoc.distance(currentLoc) > 0.1) {
                // Entity hareket etti, güncellemeyi kaydet
                EntityUpdateAction updateAction = new EntityUpdateAction(
                        entityUUID,
                        entity.getType().name(),
                        currentLoc.getX(),
                        currentLoc.getY(),
                        currentLoc.getZ(),
                        currentLoc.getYaw(),
                        currentLoc.getPitch());
                session.addAction(updateAction);
                session.updateEntityLocation(entityUUID, currentLoc);

                plugin.debug("[RECORDING-DEBUG] Entity moved: " + entity.getType() +
                        " from " + (lastLoc != null ? lastLoc.toVector() : "initial") +
                        " to " + currentLoc.toVector());
            }
        }
    }

    /**
     * Kaydı durdurur ve veritabanına kaydeder; rapor bildirimi GÖNDERMEZ.
     * (Kapanış / yönetimsel durdurma için. Çıkış ve yeniden başlatmada
     * {@link #stopRecording(UUID, boolean)} ile true verilir.)
     */
    public CompletableFuture<Boolean> stopRecording(UUID playerUUID) {
        return stopRecording(playerUUID, false);
    }

    /**
     * Kaydı durdurur ve veritabanına kaydeder.
     *
     * @param notifyReports true → kayda bağlı raporların (birincil + notifyOnFinish) yetkili/webhook/
     *                      proxy bildirimi kayıt kaydetme işi bittikten sonra, BAŞARISINA BAKILMAKSIZIN
     *                      gönderilir (süre sonu, oyuncu çıkışı, AC yeniden başlatma). Kapanışta (stopAll)
     *                      false. Kayıt haritadan atomik olarak alındığı için aynı kayda iki kez bildirim
     *                      gitmez (ikinci çağıran kaydı bulamaz).
     */
    public CompletableFuture<Boolean> stopRecording(UUID playerUUID, boolean notifyReports) {
        RecordingSession session = activeRecordings.remove(playerUUID);
        RecordingInfo info = recordingInfoMap.remove(playerUUID);

        // Entity tracking task'ı durdur
        Integer taskId = entityTrackingTasks.remove(playerUUID);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getLogger().info("[RECORDING] Entity tracking task durduruldu: #" + taskId);
        }

        // Süre-sonu durdurma task'ını iptal et (varsa) — böylece bu oyuncuya sonradan
        // başlayan yeni bir kayıt, eski timer tarafından erken kesilmez.
        Integer stopTaskId = stopTasks.remove(playerUUID);
        if (stopTaskId != null) {
            plugin.getServer().getScheduler().cancelTask(stopTaskId);
        }

        // NearbyPlayerTracker'ı durdur
        NearbyPlayerTracker nearbyTracker = nearbyPlayerTrackers.remove(playerUUID);
        if (nearbyTracker != null) {
            nearbyTracker.stop();
            plugin.getLogger().info("[RECORDING] NearbyPlayerTracker durduruldu");
        }

        if (session != null && info != null) {
            CompletableFuture<Boolean> result = finishAndSave(playerUUID, session, info);
            if (notifyReports) {
                String targetName = session.getRecordedPlayerName();
                result.whenComplete((saved, error) -> {
                    try {
                        sendFinishNotifications(info, targetName);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[RECORDING] Rapor bildirimi gönderilemedi: " + e.getMessage());
                    }
                });
            }
            return result;
        } else {
            plugin.getLogger().warning("[RECORDING] Durdurulacak kayıt bulunamadı: " + playerUUID);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Haritalardan alınmış (durdurulmuş) kaydı sonlandırır ve DB'ye yazar.
     */
    private CompletableFuture<Boolean> finishAndSave(UUID playerUUID, RecordingSession session, RecordingInfo info) {
        List<Integer> ids = info.getAllReportIds();
        plugin.getLogger().info("[RECORDING] Kayıt durduruluyor: " + session.getRecordedPlayerName() +
                " (Rapor ID'leri: " + ids + ")");

        // Packet listener'ı kaldır
        var listener = session.getPacketListener();
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            plugin.getLogger().info("[RECORDING] PacketListener kaldırıldı");
        }

        session.setEndTime(System.currentTimeMillis());

        int actionCount = session.getActions().size();
        long dropped = session.getDroppedCount();
        plugin.getLogger().info("[RECORDING] " + session.getRecordedPlayerName() +
                " için kayıt tamamlandı. Toplam " + actionCount + " aksiyon yakalandı."
                + (dropped > 0 ? " (Sınırlar nedeniyle düşürülen: " + dropped + ")" : ""));

        // Manuel kayıt kontrolü (ek rapor bağlanmadıysa DB'ye yazılmaz)
        if (ids.isEmpty() && info.reportId == 9999) {
            plugin.getLogger().info("[RECORDING] Manuel kayıt tamamlandı, veritabanına kaydedilmeyecek.");
            return CompletableFuture.completedFuture(true);
        }

        // Bağlı geçerli rapor yoksa
        if (ids.isEmpty()) {
            plugin.getLogger().warning("[RECORDING] Geçerli rapor ID'si yok (" + info.reportId +
                    "), replay veritabanına kaydedilmeyecek!");
            return CompletableFuture.completedFuture(false);
        }

        // Action kontrolü
        if (actionCount == 0) {
            plugin.getLogger().warning("[RECORDING] Hiç aksiyon yakalanmadı! Kayıt yapılmayacak.");
            return CompletableFuture.completedFuture(false);
        }

        // Normal kaydetme — tek serileştirme, her rapor ID'sine aynı veri (H-40)
        plugin.getLogger().info("[RECORDING] Veritabanına kaydediliyor (Rapor " + ids + ")");
        return saveReplayAsync(session, ids, playerUUID);
    }

    /**
     * Replay'i asenkron olarak veritabanına kaydeder. Tek serileştirme yapılır, aynı veri
     * kayda bağlı her rapor ID'sine ayrı satır olarak yazılır (H-40). Boyut sınırı aşılırsa
     * replay ATILMAZ, kuyruktan kısaltılır (H-07). Tümü başarılıysa çökme günlüğü silinir;
     * bir ID bile başarısızsa günlük son hâliyle kalır ve açılışta kurtarılır (H-27).
     *
     * @return en az bir rapor ID'si için yazıldıysa true
     */
    private CompletableFuture<Boolean> saveReplayAsync(RecordingSession session, List<Integer> reportIds,
            UUID recordedUUID) {
        final int primaryId = reportIds.get(0);
        return CompletableFuture.supplyAsync(() -> {
            // VERİ KAYBI KORUMASI: session.getActions() bir synchronizedList; serialize
            // onu iterasyonla yazarken başka thread (gecikmeli paket callback'i) addAction
            // çağırırsa ConcurrentModificationException → catch → replay HİÇ kaydedilmez.
            // Bu yüzden serialize'dan önce senkronize bir anlık kopya alıyoruz.
            java.util.List<com.reportsystem.common.replay.actions.ReplayAction> live = session.getActions();
            java.util.List<com.reportsystem.common.replay.actions.ReplayAction> actionsSnapshot;
            synchronized (live) {
                actionsSnapshot = new java.util.ArrayList<>(live);
            }

            byte[] data;
            int savedActionCount;
            try {
                // Action'ları serialize et
                byte[] fullData = ReplaySerializer.serialize(actionsSnapshot, true);
                double sizeKB = ReplaySerializer.calculateSizeKB(fullData);

                plugin.getLogger().info("[RECORDING] Replay verisi sıkıştırıldı: " +
                        String.format(Locale.ROOT, "%.2f KB (%.2f MB)", sizeKB, sizeKB / 1024.0)
                        + " (Rapor " + reportIds + ")");

                // Boyut limiti: aşılırsa kuyruktan kısalt (en çok 3 deneme), ATMA
                int maxFileSizeMB = plugin.getConfig().getInt("replay.max-file-size", 5);
                long limitBytes = maxFileSizeMB > 0 ? maxFileSizeMB * 1024L * 1024L : -1L;
                RecordingJournal.FitResult fitted = RecordingJournal.fitToSizeLimit(actionsSnapshot, fullData,
                        limitBytes, plugin.getLogger(), "Rapor " + reportIds);
                data = fitted.data();
                savedActionCount = fitted.actionCount();
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "[RECORDING] Replay serialize edilirken hata", e);
                return false;
            }

            List<Integer> failedIds = new ArrayList<>();
            List<Integer> savedIds = new ArrayList<>();
            for (int reportId : reportIds) {
                try {
                    Replay replay = new Replay(
                            reportId,
                            session.getRecordedPlayerName(),
                            session.getWorldName(),
                            session.getStartTime(),
                            session.getEndTime(),
                            savedActionCount,
                            data,
                            true);
                    replayDAO.saveReplay(replay);
                    savedIds.add(reportId);
                    plugin.getLogger()
                            .info("[RECORDING] ✓ Replay başarıyla veritabanına kaydedildi (Report #" + reportId + ")");
                } catch (Exception e) {
                    failedIds.add(reportId);
                    plugin.getLogger().severe("[RECORDING] Replay kaydedilirken hata (Report #" + reportId + "): "
                            + e.getMessage());
                }
            }

            if (failedIds.isEmpty()) {
                journal.delete(recordedUUID, session.getStartTime());
            } else if (crashRecoveryEnabled) {
                // Günlüğü son (tam) hâliyle ve yalnız başarısız ID'lerle güncelle → açılışta kurtarılır
                journal.checkpointAsync(recordedUUID, failedIds, session.getRecordedPlayerName(),
                        session.getWorldName(), session.getStartTime(), actionsSnapshot, true);
                plugin.getLogger().warning("[RECORDING] Kaydedilemeyen raporlar " + failedIds
                        + " için replay çökme günlüğünde tutuluyor; sonraki açılışta tekrar denenecek.");
            }

            // Web replay auto-generate — yalnız birincil ID için
            if (savedIds.contains(primaryId)
                    && plugin.getConfigManager().isWebReplayEnabled()
                    && plugin.getConfigManager().isWebReplayAutoGenerate()
                    && plugin.getReplayManager() != null) {
                plugin.getReplayManager().generateWebReplay(primaryId)
                        .thenAccept(url -> {
                            if (url != null) {
                                plugin.getLogger().info("[WebReplay] Otomatik export tamamlandi: " + url);
                            }
                        });
            }

            return !savedIds.isEmpty();
        });
    }

    /**
     * Oyuncunun ekipmanlarını kaydeder
     */
    private void recordAllEquipment(Player player, RecordingSession session) {
        // Ana el
        recordEquipmentSlot(player.getInventory().getItemInMainHand(),
                EquipmentAction.EquipmentSlot.MAIN_HAND, session);

        // Yardımcı el
        recordEquipmentSlot(player.getInventory().getItemInOffHand(),
                EquipmentAction.EquipmentSlot.OFF_HAND, session);

        // Kask
        recordEquipmentSlot(player.getInventory().getHelmet(),
                EquipmentAction.EquipmentSlot.HELMET, session);

        // Göğüslük
        recordEquipmentSlot(player.getInventory().getChestplate(),
                EquipmentAction.EquipmentSlot.CHESTPLATE, session);

        // Pantolon
        recordEquipmentSlot(player.getInventory().getLeggings(),
                EquipmentAction.EquipmentSlot.LEGGINGS, session);

        // Botlar
        recordEquipmentSlot(player.getInventory().getBoots(),
                EquipmentAction.EquipmentSlot.BOOTS, session);
    }

    /**
     * Oyuncunun tam inventory'sini kaydeder (36 slot + 4 armor + 1 offhand = 41
     * slot)
     */
    private void recordFullInventory(Player player, RecordingSession session) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        Map<Integer, EquipmentAction.ItemData> inventoryMap = new HashMap<>();

        // Main inventory (0-35 slots)
        org.bukkit.inventory.ItemStack[] contents = inv.getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                inventoryMap.put(i, convertItemToData(item));
            }
        }

        // Armor slots (36-39)
        org.bukkit.inventory.ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && armor[i].getType() != org.bukkit.Material.AIR) {
                inventoryMap.put(36 + i, convertItemToData(armor[i]));
            }
        }

        // Offhand (slot 40)
        org.bukkit.inventory.ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != org.bukkit.Material.AIR) {
            inventoryMap.put(40, convertItemToData(offhand));
        }

        // Create InventorySnapshotAction
        com.reportsystem.common.replay.actions.InventorySnapshotAction inventoryAction = new com.reportsystem.common.replay.actions.InventorySnapshotAction(
                convertInventoryMapToSnapshotFormat(inventoryMap));

        session.addAction(inventoryAction);

        plugin.getLogger().info("[RECORDING] Full inventory recorded: " + inventoryMap.size() + " items");
    }

    /**
     * Convert inventory map to InventorySnapshotAction format
     */
    private Map<Integer, com.reportsystem.common.replay.actions.InventorySnapshotAction.ItemData> convertInventoryMapToSnapshotFormat(
            Map<Integer, EquipmentAction.ItemData> source) {

        Map<Integer, com.reportsystem.common.replay.actions.InventorySnapshotAction.ItemData> result = new HashMap<>();

        for (Map.Entry<Integer, EquipmentAction.ItemData> entry : source.entrySet()) {
            EquipmentAction.ItemData item = entry.getValue();
            result.put(entry.getKey(), new com.reportsystem.common.replay.actions.InventorySnapshotAction.ItemData(
                    item.getMaterial(),
                    item.getAmount(),
                    item.getDurability(),
                    item.getDisplayName(),
                    item.getLore(),
                    item.getEnchantments(),
                    item.getItemStackData(),
                    item.getCustomModelData(),
                    item.isUnbreakable()));
        }

        return result;
    }

    /**
     * ItemStack'i EquipmentAction.ItemData'ya çevirir
     */
    private EquipmentAction.ItemData convertItemToData(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        try {
            // ItemStack'i serialize et
            byte[] itemStackData = ItemSerializer.itemStackToBytes(item);

            // Enchantment'ları al
            Map<String, Integer> enchantments = new HashMap<>();
            item.getEnchantments().forEach((enchant, level) -> {
                enchantments.put(enchant.getKey().getKey(), level);
            });

            // Meta bilgileri
            String displayName = null;
            List<String> lore = null;
            int customModelData = 0;
            boolean unbreakable = false;

            if (item.hasItemMeta()) {
                var meta = item.getItemMeta();

                if (meta.hasDisplayName()) {
                    displayName = meta.getDisplayName();
                }

                if (meta.hasLore()) {
                    lore = meta.getLore();
                }

                if (meta.hasCustomModelData()) {
                    customModelData = meta.getCustomModelData();
                }

                unbreakable = meta.isUnbreakable();
            }

            return new EquipmentAction.ItemData(
                    item.getType().name(),
                    item.getAmount(),
                    item.getDurability(),
                    displayName,
                    lore,
                    enchantments,
                    itemStackData,
                    customModelData,
                    unbreakable);

        } catch (Exception e) {
            plugin.getLogger().warning("[RECORDING] Failed to convert item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Tek bir ekipman slotunu kaydeder
     */
    private void recordEquipmentSlot(ItemStack item, EquipmentAction.EquipmentSlot slot, RecordingSession session) {
        EquipmentAction.ItemData itemData = convertItemToData(item);
        session.addAction(new EquipmentAction(slot, itemData));
    }

    /**
     * Oyuncunun kaydedilip kaydedilmediğini kontrol eder
     */
    public boolean isRecording(UUID playerUUID) {
        return recordingInfoMap.containsKey(playerUUID);
    }

    /**
     * Aktif session'ı döndürür
     */
    public RecordingSession getSession(UUID playerUUID) {
        return activeRecordings.get(playerUUID);
    }

    /**
     * Aktif kayıt sayısını döndürür
     */
    public int getActiveRecordingCount() {
        return activeRecordings.size();
    }

    /**
     * Tüm aktif kayıtları durdurur
     * Shutdown sırasında async save'lerin tamamlanmasını bekler (DB kapanmadan önce)
     */
    public void stopAll() {
        // Periyodik checkpoint'i durdur — kayıtlar aşağıda son hâlleriyle kaydedilecek
        if (checkpointTask != null) {
            checkpointTask.cancel();
            checkpointTask = null;
        }

        Set<UUID> activeUUIDs = new HashSet<>(recordingInfoMap.keySet());
        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();
        for (UUID uuid : activeUUIDs) {
            saveFutures.add(stopRecording(uuid));
        }

        // Async save'leri bekle — shutdown'da database kapanmadan flush garantile
        if (!saveFutures.isEmpty()) {
            try {
                CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                        .get(10, TimeUnit.SECONDS);
                plugin.getLogger().info("[RECORDING] " + saveFutures.size() + " aktif kayıt flush edildi.");
            } catch (Exception e) {
                plugin.getLogger().warning("[RECORDING] Shutdown save timeout/error: " + e.getMessage());
            }
        }

        // Tüm NearbyPlayerTracker'ları durdur
        for (NearbyPlayerTracker tracker : nearbyPlayerTrackers.values()) {
            tracker.stop();
        }
        nearbyPlayerTrackers.clear();

        // Kuyruktaki günlük işlerini (silme / son checkpoint) kısa süre bekle, executor'ı kapat.
        // Bitmeyenler diskte kalır; açılış kurtarması DB'de replay varsa tekrar yazmaz.
        journal.shutdown();

        plugin.getLogger().info("[RECORDING] Tüm aktif kayıtlar durduruldu.");
    }

    /**
     * Tüm aktif kayıtları durdurur - async wrapper
     */
    public void stopAllRecordings() {
        stopAll();
    }

    /**
     * 5 dakikalık auto-save görevinden çağrılır (ReportSystemSpigot.startTasks).
     * Çökmeye karşı koruma burada DEĞİL: aktif kayıtlar ayrı async zamanlayıcıyla
     * (replay.crash-recovery.interval-seconds) RecordingJournal'a checkpoint'lenir ve
     * açılışta kurtarılır (H-27). Bu metot yalnız durum logu yazar.
     */
    public void saveAllRecordings() {
        ReportSystemSpigot.getInstance()
                .debug("[RECORDING] Auto-save check completed. Active recordings: " + activeRecordings.size()
                        + " (crash-recovery " + (crashRecoveryEnabled ? "on" : "off") + ")");
    }

    /**
     * Kaydedilen oyuncunun etkin periyodik NearbyPlayerTracker'ı (yoksa / config'te kapalıysa null).
     * Etkinse yakın oyuncu PLAYER_APPEAR'ının tek sahibi odur (H-11): olay dinleyicisi APPEAR
     * üretmek yerine {@link NearbyPlayerTracker#ensureTracked} çağırır.
     */
    public NearbyPlayerTracker getActiveNearbyTracker(UUID recordedPlayerUUID) {
        NearbyPlayerTracker tracker = nearbyPlayerTrackers.get(recordedPlayerUUID);
        return tracker != null && tracker.isEnabled() ? tracker : null;
    }

    /**
     * Aktif kayıtların haritasını döndürür
     */
    public Map<UUID, RecordingInfo> getActiveRecordings() {
        return new HashMap<>(recordingInfoMap);
    }

    /**
     * BungeeCord'a notification gönder (network-wide broadcast için)
     */
    private void sendNotificationToBungee(int reportId, String targetName, String reporterName, String reason) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("NewReport");
        out.writeInt(reportId);
        // Alan sirasi proxy/Spigot tuketicisiyle ayni: id, reporter, reported, reason, server
        out.writeUTF(reporterName);
        out.writeUTF(targetName);
        out.writeUTF(reason);
        out.writeUTF(plugin.getServerName());

        // Herhangi bir online oyuncuya mesajı gönder
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player != null) {
            plugin.sendSigned(player, "reportsystem:notification", out.toByteArray());
            plugin.getLogger().info("[ReportNotification] Sent notification to BungeeCord for report #" + reportId);
        } else {
            plugin.getLogger().warning("[ReportNotification] Cannot send to BungeeCord - no online players!");
        }
    }

    /**
     * Recording bilgilerini tutan inner class
     */
    public static class RecordingInfo {
        // Birincil rapor ID'si (kaydı başlatan). Async checkpoint zamanlayıcısı da okur → volatile.
        private volatile int reportId;
        // H-40: aynı açık kayda sonradan bağlanan raporlar (birincili EZMEZ)
        private final List<Integer> extraReportIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        // Bildirimi kayıt bitince gönderilecek rapor ID'leri
        private final Set<Integer> notifyOnFinish = ConcurrentHashMap.newKeySet();
        private RecordingSession session;
        private long startTime;
        private String reporterName;
        private String reason;

        public RecordingInfo(int reportId, RecordingSession session, String reporterName, String reason) {
            this.reportId = reportId;
            this.session = session;
            this.startTime = System.currentTimeMillis();
            this.reporterName = reporterName;
            this.reason = reason;
        }

        public int getReportId() {
            return reportId;
        }

        /**
         * Replay'in yazılacağı tüm rapor ID'leri: birincil (> 0 ve ≠ 9999) + ekler, tekrarsız.
         */
        public List<Integer> getAllReportIds() {
            List<Integer> ids = new ArrayList<>();
            int primary = reportId;
            if (isRealReportId(primary)) {
                ids.add(primary);
            }
            for (Integer id : extraReportIds) {
                if (id != null && isRealReportId(id) && !ids.contains(id)) {
                    ids.add(id);
                }
            }
            return ids;
        }

        public RecordingSession getSession() {
            return session;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getReporterName() {
            return reporterName;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Tüm aktif recording session'ları döndürür (nearby player tracking için)
     */
    public Map<UUID, RecordingSession> getActiveRecordingSessions() {
        return new HashMap<>(activeRecordings);
    }
}