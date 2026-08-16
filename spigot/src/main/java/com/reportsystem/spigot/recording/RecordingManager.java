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
import com.reportsystem.spigot.commands.ReportCommand;
import com.reportsystem.spigot.utils.SkinUtils;
import com.reportsystem.spigot.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.reportsystem.spigot.ReportSystemSpigot;

import java.io.IOException;
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

    public RecordingManager(ReportSystemSpigot plugin, ReplayDAO replayDAO) {
        this.plugin = plugin;
        this.replayDAO = replayDAO;
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
            // PERFORMANS: .join() ana thread'i bloklar — async callback kullan
            stopRecording(playerUUID).thenRun(() -> {
                plugin.getLogger().info("[RECORDING] Eski kayıt durduruldu: " + player.getName());
            }).exceptionally(e -> {
                plugin.getLogger().warning("[RECORDING] Eski kayıt durdurulurken hata: " + e.getMessage());
                return null;
            });
        }

        plugin.getLogger().info("[RECORDING] Kayıt başlatılıyor: " + player.getName() +
                " (Initial Report ID: " + reportId + ") - Süre: " + durationSeconds + " saniye");

        // Session oluştur
        RecordingSession session = new RecordingSession(player.getName());
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
                    stopRecording(playerUUID).thenAccept(success -> {
                        if (success) {
                            // Kayıt başarıyla tamamlandı, yetkililere bildirim gönder
                            RecordingInfo recordingInfo = info;
                            if (recordingInfo != null && recordingInfo.getReportId() > 0) {
                                plugin.getLogger()
                                        .info("[RECORDING] Kayıt başarıyla tamamlandı, bildirim gönderiliyor...");

                                // Yetkililere toast bildirim gönder (messages dosyasından okunur)
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getReportCommand().notifyStaff(
                                            recordingInfo.getReporterName(),
                                            player.getName(),
                                            recordingInfo.getReason(),
                                            recordingInfo.getReportId());
                                });

                                // Discord webhook bildirimi
                                if (plugin.getWebhookManager() != null && plugin.getWebhookManager().isEnabled()) {
                                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                                        com.reportsystem.common.models.Report report = plugin.getReportService()
                                                .getReportById(recordingInfo.getReportId());
                                        if (report != null) {
                                            plugin.getLogger()
                                                    .info("[Webhook] Sending new report notification for report #"
                                                            + recordingInfo.getReportId());
                                            plugin.getWebhookManager().sendNewReportNotification(report);
                                        } else {
                                            plugin.getLogger().warning("[Webhook] Could not fetch report #"
                                                    + recordingInfo.getReportId() + " from database!");
                                        }
                                    });
                                }

                                // BungeeCord'a bildirim gönder (network-wide)
                                if (plugin.getConfigManager().isBungeeCordEnabled()) {
                                    sendNotificationToBungee(
                                            recordingInfo.getReportId(),
                                            player.getName(),
                                            recordingInfo.getReporterName(),
                                            recordingInfo.getReason());
                                }
                            }
                        }
                    });
                },
                durationSeconds * 20L);
        stopTasks.put(playerUUID, stopTask.getTaskId());
    }

    /**
     * Report ID'yi günceller
     */
    public void updateReportId(UUID playerUUID, int newReportId) {
        RecordingInfo info = recordingInfoMap.get(playerUUID);
        if (info != null) {
            int oldId = info.reportId;
            info.reportId = newReportId;
            plugin.getLogger().info("[RECORDING] Report ID güncellendi: " + oldId + " -> " + newReportId);
        } else {
            plugin.getLogger().warning("[RECORDING] Güncellenecek kayıt bulunamadı: " + playerUUID);
        }
    }

    /**
     * Report ID ve reason'ı günceller
     */
    public void updateReportIdAndReason(UUID playerUUID, int newReportId, String reason) {
        RecordingInfo info = recordingInfoMap.get(playerUUID);
        if (info != null) {
            int oldId = info.reportId;
            info.reportId = newReportId;
            info.reason = reason;
            plugin.getLogger().info("[RECORDING] Report ID ve sebep güncellendi: " + oldId + " -> " + newReportId
                    + " | Sebep: " + reason);
        } else {
            plugin.getLogger().warning("[RECORDING] Güncellenecek kayıt bulunamadı: " + playerUUID);
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
     * Kaydı durdurur ve veritabanına kaydeder
     */
    public CompletableFuture<Boolean> stopRecording(UUID playerUUID) {
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
            // Önce bekleyen Report ID var mı kontrol et
            Integer pendingReportId = ReportCommand.getPendingReportId(playerUUID);
            if (pendingReportId != null && info.reportId == -999) {
                info.reportId = pendingReportId;
                plugin.getLogger().info("[RECORDING] Bekleyen Report ID kullanıldı: #" + pendingReportId);
            }

            plugin.getLogger().info("[RECORDING] Kayıt durduruluyor: " + session.getRecordedPlayerName() +
                    " (Final Report ID: " + info.reportId + ")");

            // Packet listener'ı kaldır
            var listener = session.getPacketListener();
            if (listener != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
                plugin.getLogger().info("[RECORDING] PacketListener kaldırıldı");
            }

            session.setEndTime(System.currentTimeMillis());

            int actionCount = session.getActions().size();
            plugin.getLogger().info("[RECORDING] " + session.getRecordedPlayerName() +
                    " için kayıt tamamlandı. Toplam " + actionCount + " aksiyon yakalandı.");

            // Manuel kayıt kontrolü
            if (info.reportId == 9999) {
                plugin.getLogger().info("[RECORDING] Manuel kayıt tamamlandı, veritabanına kaydedilmeyecek.");
                return CompletableFuture.completedFuture(true);
            }

            // Geçici ID kontrolü (-999 ise ve değişmemişse)
            if (info.reportId == -999) {
                plugin.getLogger().warning("[RECORDING] Report ID hala geçici değerde (-999), " +
                        "5 saniye daha beklenecek...");

                // 5 saniye sonra Bukkit scheduler ile tekrar kontrol et (Thread.sleep yerine)
                CompletableFuture<Boolean> delayedFuture = new CompletableFuture<>();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    Integer lateReportId = ReportCommand.getPendingReportId(playerUUID);
                    if (lateReportId != null && lateReportId > 0) {
                        plugin.getLogger().info("[RECORDING] Geç gelen Report ID alındı: #" + lateReportId);
                        saveReplayAsync(session, lateReportId).thenAccept(delayedFuture::complete);
                    } else {
                        plugin.getLogger().severe("[RECORDING] Report ID alınamadı, kayıt iptal edildi!");
                        delayedFuture.complete(false);
                    }
                }, 100L); // 5 saniye = 100 tick
                return delayedFuture;
            }

            // Report ID geçersizse
            if (info.reportId <= 0) {
                plugin.getLogger().warning("[RECORDING] Geçersiz Report ID (" + info.reportId +
                        "), replay veritabanına kaydedilmeyecek!");
                return CompletableFuture.completedFuture(false);
            }

            // Action kontrolü
            if (actionCount == 0) {
                plugin.getLogger().warning("[RECORDING] Hiç aksiyon yakalanmadı! Kayıt yapılmayacak.");
                return CompletableFuture.completedFuture(false);
            }

            // Normal kaydetme
            plugin.getLogger().info("[RECORDING] Veritabanına kaydediliyor (Report #" + info.reportId + ")");
            return saveReplayAsync(session, info.reportId);

        } else {
            plugin.getLogger().warning("[RECORDING] Durdurulacak kayıt bulunamadı: " + playerUUID);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Replay'i asenkron olarak veritabanına kaydeder
     */
    private CompletableFuture<Boolean> saveReplayAsync(RecordingSession session, int reportId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // VERİ KAYBI KORUMASI: session.getActions() bir synchronizedList; serialize
                // onu iterasyonla yazarken başka thread (gecikmeli paket callback'i) addAction
                // çağırırsa ConcurrentModificationException → catch → replay HİÇ kaydedilmez.
                // Bu yüzden serialize'dan önce senkronize bir anlık kopya alıyoruz.
                java.util.List<com.reportsystem.common.replay.actions.ReplayAction> live = session.getActions();
                java.util.List<com.reportsystem.common.replay.actions.ReplayAction> actionsSnapshot;
                synchronized (live) {
                    actionsSnapshot = new java.util.ArrayList<>(live);
                }

                // Action'ları serialize et
                byte[] data = ReplaySerializer.serialize(actionsSnapshot, true);
                double sizeKB = ReplaySerializer.calculateSizeKB(data);
                double sizeMB = sizeKB / 1024.0;

                plugin.getLogger().info("[RECORDING] Replay verisi sıkıştırıldı: " +
                        String.format("%.2f KB (%.2f MB)", sizeKB, sizeMB) + " (Report #" + reportId + ")");

                // Boyut limiti kontrolü
                int maxFileSizeMB = plugin.getConfig().getInt("replay.max-file-size", 5);
                if (maxFileSizeMB > 0 && sizeMB > maxFileSizeMB) {
                    plugin.getLogger().warning("[RECORDING] ⚠ Replay boyutu çok büyük! " +
                            String.format("%.2f MB > %d MB limit", sizeMB, maxFileSizeMB) +
                            " - Kayıt iptal edildi (Report #" + reportId + ")");
                    return false;
                }

                // Replay nesnesini oluştur
                Replay replay = new Replay(
                        reportId,
                        session.getRecordedPlayerName(),
                        session.getWorldName(),
                        session.getStartTime(),
                        session.getEndTime(),
                        actionsSnapshot.size(),
                        data,
                        true);

                // Veritabanına kaydet
                replayDAO.saveReplay(replay);

                plugin.getLogger()
                        .info("[RECORDING] ✓ Replay başarıyla veritabanına kaydedildi (Report #" + reportId + ")");

                // Web replay auto-generate
                if (plugin instanceof com.reportsystem.spigot.ReportSystemSpigot) {
                    com.reportsystem.spigot.ReportSystemSpigot spigotPlugin = (com.reportsystem.spigot.ReportSystemSpigot) plugin;
                    if (spigotPlugin.getConfigManager().isWebReplayEnabled()
                            && spigotPlugin.getConfigManager().isWebReplayAutoGenerate()) {
                        spigotPlugin.getReplayManager().generateWebReplay(reportId)
                                .thenAccept(url -> {
                                    if (url != null) {
                                        plugin.getLogger().info("[WebReplay] Otomatik export tamamlandi: " + url);
                                    }
                                });
                    }
                }

                return true;

            } catch (IOException e) {
                plugin.getLogger().severe("[RECORDING] Replay serialize edilirken hata: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                plugin.getLogger().severe("[RECORDING] Replay kaydedilirken hata: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
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

        plugin.getLogger().info("[RECORDING] Tüm aktif kayıtlar durduruldu.");
    }

    /**
     * Tüm aktif kayıtları durdurur - async wrapper
     */
    public void stopAllRecordings() {
        stopAll();
    }

    /**
     * Tüm aktif kayıtları kaydeder (auto-save için)
     */
    public void saveAllRecordings() {
        // Bu metot şu anda bir şey yapmıyor çünkü kayıtlar
        // durdurulduğunda otomatik olarak kaydediliyor
        // İleride incremental save eklenebilir
        ReportSystemSpigot.getInstance()
                .debug("[RECORDING] Auto-save check completed. Active recordings: " + activeRecordings.size());
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
        out.writeUTF(targetName);
        out.writeUTF(reporterName);
        out.writeUTF(reason);
        out.writeUTF(plugin.getServerName());

        // Herhangi bir online oyuncuya mesajı gönder
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player != null) {
            player.sendPluginMessage(plugin, "reportsystem:notification", out.toByteArray());
            plugin.getLogger().info("[ReportNotification] Sent notification to BungeeCord for report #" + reportId);
        } else {
            plugin.getLogger().warning("[ReportNotification] Cannot send to BungeeCord - no online players!");
        }
    }

    /**
     * Recording bilgilerini tutan inner class
     */
    public static class RecordingInfo {
        private int reportId;
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