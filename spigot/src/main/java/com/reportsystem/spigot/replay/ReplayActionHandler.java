package com.reportsystem.spigot.replay;

import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.LocationAction;
import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.common.replay.actions.ReplayAction;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ReplayActionHandler {

    private final JavaPlugin plugin;
    private final ReplayPlayer replayPlayer;
    private final List<ReplayAction> actions;

    private BukkitTask playbackTask;
    private int currentActionIndex = 0;
    private long startTime;

    // Seek sırasında processNextAction'ın çalışmasını engelleyen flag
    private volatile boolean seeking = false;

    // Pause sırasında geçen süreyi doğru hesaplamak için
    private long pausedAtElapsed = -1;

    // Seek generation - eski deferred operasyonları geçersiz kılmak için
    // Her seek'te artar, eski runTask callback'leri bu değeri kontrol eder
    private volatile int seekGeneration = 0;

    public ReplayActionHandler(JavaPlugin plugin, ReplayPlayer replayPlayer, List<ReplayAction> actions) {
        this.plugin = plugin;
        this.replayPlayer = replayPlayer;
        this.actions = actions;
    }

    /**
     * Playback'i başlatır
     */
    public void startPlayback() {
        startTime = System.currentTimeMillis();
        currentActionIndex = 0;
        pausedAtElapsed = -1;

        // Playback task'ı başlat - HER TICK ÇALIŞTIR
        playbackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processNextAction, 0L, 1L);
    }

    /**
     * Playback'i durdurur
     */
    public void stopPlayback() {
        if (playbackTask != null) {
            playbackTask.cancel();
            playbackTask = null;
        }
    }

    /**
     * Bir sonraki action'ları işler.
     * Aynı zamanda gerçekleşen tüm action'ları tek tick'te oynatır.
     */
    private void processNextAction() {
        // Seek işlemi devam ediyorsa bu tick'i atla
        if (seeking) return;

        if (replayPlayer.isPaused() || replayPlayer.getState() != ReplayPlayer.ReplayState.PLAYING) {
            return;
        }

        long elapsedTime = getElapsedTime();

        // Kayıt süresi doldu mu kontrol et (action bitse bile süre dolana kadar bekle)
        long recordingDurationMs = replayPlayer.getReplay().getDuration() * 1000L;
        if (recordingDurationMs > 0 && elapsedTime >= recordingDurationMs) {
            replayPlayer.finish();
            return;
        }

        if (currentActionIndex >= actions.size()) {
            // Action'lar bitti ama süre dolmadı - NPC son konumda bekliyor
            return;
        }

        long firstTimestamp = actions.get(0).getTimestamp();

        // Zamanı gelmiş TÜM action'ları bu tick'te oynat
        while (currentActionIndex < actions.size()) {
            ReplayAction currentAction = actions.get(currentActionIndex);
            long actionTime = currentAction.getTimestamp() - firstTimestamp;

            if (elapsedTime >= actionTime) {
                replayPlayer.getActionPlayer().playAction(currentAction);
                currentActionIndex++;
            } else {
                break;
            }
        }
    }

    /**
     * Progress bilgisi döndürür (0.0 - 1.0 arası)
     * Zaman bazlı hesaplama - action sayısına değil, geçen süreye göre
     */
    public double getProgress() {
        if (actions.isEmpty()) return 0.0;

        long totalTime = actions.get(actions.size() - 1).getTimestamp() - actions.get(0).getTimestamp();
        if (totalTime <= 0) return 1.0;

        long elapsedTime = getElapsedTime();
        return Math.max(0.0, Math.min(1.0, (double) elapsedTime / totalTime));
    }

    /**
     * Mevcut geçen süreyi hesaplar (pause durumunu da dikkate alır)
     */
    private long getElapsedTime() {
        if (pausedAtElapsed >= 0) {
            return pausedAtElapsed;
        }
        return (long) ((System.currentTimeMillis() - startTime) * replayPlayer.getPlaybackSpeed());
    }

    /**
     * Belirli bir yüzdeye atlar
     */
    public void seekToPercent(double percent) {
        if (percent < 0.0) percent = 0.0;
        if (percent > 1.0) percent = 1.0;

        long totalDuration = actions.get(actions.size() - 1).getTimestamp() - actions.get(0).getTimestamp();
        seekToTime((long) (totalDuration * percent));
    }

    /**
     * Belirli bir zamana atlar (milisaniye cinsinden, replay başlangıcından itibaren).
     * Binary search ile doğru index'i bulur ve tüm NPC'leri konumlandırır.
     */
    private void seekToTime(long targetMs) {
        if (actions.isEmpty()) return;

        // Seek başladı - processNextAction'ı engelle
        seeking = true;
        // Seek generation'ı artır - eski deferred operasyonlar geçersiz olsun
        seekGeneration++;

        try {
            long firstTimestamp = actions.get(0).getTimestamp();
            long totalDuration = actions.get(actions.size() - 1).getTimestamp() - firstTimestamp;

            // Sınırları kontrol et
            targetMs = Math.max(0, Math.min(targetMs, totalDuration));

            // Binary search ile hedef zamandaki action index'ini bul
            int targetIndex = 0;
            int low = 0, high = actions.size() - 1;
            while (low <= high) {
                int mid = (low + high) / 2;
                long midTime = actions.get(mid).getTimestamp() - firstTimestamp;
                if (midTime <= targetMs) {
                    targetIndex = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            // targetIndex'ten devam et - applyAllEntityPositions sadece konum uygular,
            // diğer action tipleri (PLAYER_APPEAR, blok, animasyon vb.) hala oynatılmalı
            currentActionIndex = targetIndex;

            startTime = System.currentTimeMillis() - (long) (targetMs / replayPlayer.getPlaybackSpeed());

            // Eğer replay duraklatılmışsa, pausedAtElapsed'ı targetMs olarak ayarla
            // Böylece getElapsedTime() doğru değeri döndürür ve
            // onResume() çağrıldığında startTime doğru hesaplanır
            if (replayPlayer.isPaused()) {
                pausedAtElapsed = targetMs;
            } else {
                pausedAtElapsed = -1;
            }

            // Tüm NPC'leri doğru konuma taşı (ana oyuncu + yakındaki oyuncular)
            applyAllEntityPositions(targetIndex);

            // Tüm entity'lerin metadata durumlarını sıfırla (DYING pose vb.)
            // Bu işlem entity'leri destroy+respawn yapar, equipment kaybolur
            replayPlayer.getNpcManager().resetAllEntityStates();

            // Respawn sonrası equipment'ı tekrar uygula
            applyAllEquipment(targetIndex);

        } finally {
            // Seek bitti - processNextAction tekrar çalışabilir
            seeking = false;
        }
    }

    /**
     * Hedef index'teki veya öncesindeki en yakın konumları bularak
     * tüm NPC'leri (ana oyuncu + yakındaki oyuncular) doğru konuma taşır.
     * Eksik nearby player'ları da spawn eder.
     * Absolute teleport kullanır - relative move seek işlemlerinde güvenilir değildir.
     *
     * İki aşamalı tarama:
     * 1. Geriye doğru tara: her nearby player için son konum + durumunu bul
     * 2. Spawn edilmemiş ama görünür olması gereken nearby player'ları spawn et ve konumlandır
     */
    private void applyAllEntityPositions(int targetIndex) {
        ReplayNPCManager npcManager = replayPlayer.getNpcManager();
        World world = getReplayWorld();

        if (world == null) {
            plugin.getLogger().warning("[REPLAY] Cannot apply positions - world is null!");
            return;
        }

        // === Ana oyuncunun konumunu bul ===
        for (int i = targetIndex; i >= 0; i--) {
            ReplayAction action = actions.get(i);
            if (action instanceof LocationAction && action.isMainPlayer()) {
                LocationAction locAction = (LocationAction) action;
                Location newLocation = new Location(
                        world,
                        locAction.getX(), locAction.getY(), locAction.getZ(),
                        locAction.getYaw(), locAction.getPitch()
                );
                npcManager.absoluteTeleportNPC(newLocation);
                replayPlayer.setLastLocation(newLocation);
                break;
            }
        }

        // === Nearby player'ların durumlarını tara ===
        // Her UUID için: son konum, görünür mü, spawn action'ı
        Set<UUID> processedUuids = new HashSet<>();
        Map<UUID, Location> nearbyPositions = new LinkedHashMap<>();
        Map<UUID, NearbyPlayerAction> nearbySpawnActions = new LinkedHashMap<>();
        Set<UUID> nearbyDisappeared = new HashSet<>();

        for (int i = targetIndex; i >= 0; i--) {
            ReplayAction action = actions.get(i);
            if (!(action instanceof NearbyPlayerAction)) continue;

            NearbyPlayerAction nearbyAction = (NearbyPlayerAction) action;
            UUID uuid = nearbyAction.getPlayerUuid();

            // Bu UUID zaten tamamen işlendi (hem konum hem spawn bulundu veya disappear)
            if (nearbyDisappeared.contains(uuid)) continue;

            switch (nearbyAction.getActionType()) {
                case PLAYER_DISAPPEAR:
                    // Bu zamanda oyuncu görünmüyor - atla
                    nearbyDisappeared.add(uuid);
                    break;

                case PLAYER_MOVE:
                    // En son konum (sadece ilk bulunan - en güncel)
                    if (!nearbyPositions.containsKey(uuid)) {
                        nearbyPositions.put(uuid, new Location(
                                world,
                                nearbyAction.getX(), nearbyAction.getY(), nearbyAction.getZ(),
                                nearbyAction.getYaw(), nearbyAction.getPitch()
                        ));
                    }
                    break;

                case PLAYER_APPEAR:
                    // Konum yoksa konumu da kaydet
                    if (!nearbyPositions.containsKey(uuid)) {
                        nearbyPositions.put(uuid, new Location(
                                world,
                                nearbyAction.getX(), nearbyAction.getY(), nearbyAction.getZ(),
                                nearbyAction.getYaw(), nearbyAction.getPitch()
                        ));
                    }
                    // Spawn action'ını kaydet (isim, skin, equipment bilgisi burada)
                    if (!nearbySpawnActions.containsKey(uuid)) {
                        nearbySpawnActions.put(uuid, nearbyAction);
                    }
                    break;
            }
        }

        // === Spawn edilmemiş nearby player'ları spawn et ve hepsini konumlandır ===
        for (Map.Entry<UUID, Location> entry : nearbyPositions.entrySet()) {
            UUID uuid = entry.getKey();
            Location position = entry.getValue();

            Integer entityId = npcManager.getNearbyPlayerEntityId(uuid);

            // Entity yoksa → spawn et
            if (entityId == null) {
                NearbyPlayerAction spawnAction = nearbySpawnActions.get(uuid);
                if (spawnAction != null) {
                    npcManager.playNearbyPlayerAction(spawnAction);
                    // Spawn sonrası entity ID'yi al (2 tick gecikme olabilir, ama entity map'e ekleniyor)
                    entityId = npcManager.getNearbyPlayerEntityId(uuid);
                }
            }

            // Entity varsa konumlandır
            if (entityId != null) {
                npcManager.updateNearbyPlayerLocation(uuid, position);
                absoluteTeleportEntity(entityId, position);
            }
        }
    }

    /**
     * Respawn sonrası tüm entity'lerin equipment'ını tekrar uygular.
     * Geriye doğru tarayarak her slot için en son EquipmentAction'ı bulur ve oynatır.
     */
    private void applyAllEquipment(int targetIndex) {
        // Ana oyuncunun tüm slot'ları için en son equipment'ı bul
        Set<EquipmentAction.EquipmentSlot> mainFoundSlots = new HashSet<>();
        // Nearby player'ların tüm slot'ları için en son equipment'ı bul
        // UUID -> bulunmuş slotlar
        Map<UUID, Set<EquipmentAction.EquipmentSlot>> nearbyFoundSlots = new HashMap<>();

        for (int i = targetIndex; i >= 0; i--) {
            ReplayAction action = actions.get(i);
            if (!(action instanceof EquipmentAction)) continue;

            EquipmentAction eqAction = (EquipmentAction) action;

            if (eqAction.isMainPlayer()) {
                // Ana oyuncu - bu slot zaten bulunduysa atla
                if (mainFoundSlots.contains(eqAction.getSlot())) continue;
                mainFoundSlots.add(eqAction.getSlot());

                // Equipment'ı tekrar oynat
                replayPlayer.getActionPlayer().playAction(eqAction);
            } else if (eqAction.getOwnerUUID() != null) {
                // Nearby player
                UUID uuid = eqAction.getOwnerUUID();
                Set<EquipmentAction.EquipmentSlot> found = nearbyFoundSlots
                        .computeIfAbsent(uuid, k -> new HashSet<>());
                if (found.contains(eqAction.getSlot())) continue;
                found.add(eqAction.getSlot());

                // Equipment'ı tekrar oynat
                replayPlayer.getActionPlayer().playAction(eqAction);
            }

            // 6 slot (main hand, off hand, helmet, chest, legs, boots) * (main + nearby player sayısı)
            // Tüm slotlar bulunduysa erken çık
            if (mainFoundSlots.size() >= 6) {
                boolean allNearbyComplete = true;
                for (Set<EquipmentAction.EquipmentSlot> slots : nearbyFoundSlots.values()) {
                    if (slots.size() < 6) { allNearbyComplete = false; break; }
                }
                if (allNearbyComplete) break;
            }
        }
    }

    /**
     * Replay'in çalıştığı World referansını güvenli şekilde alır
     */
    private World getReplayWorld() {
        Location lastLoc = replayPlayer.getLastLocation();
        if (lastLoc != null && lastLoc.getWorld() != null) {
            return lastLoc.getWorld();
        }
        // Fallback: viewer'ın dünyasını kullan
        for (Player viewer : replayPlayer.getViewers()) {
            if (viewer.isOnline()) {
                return viewer.getWorld();
            }
        }
        return null;
    }

    /**
     * Bir entity'yi absolute teleport ile taşır (seek işlemleri için)
     */
    private void absoluteTeleportEntity(int entityId, Location location) {
        WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                entityId,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getYaw(),
                location.getPitch(),
                false
        );

        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                entityId,
                location.getYaw()
        );

        for (Player viewer : replayPlayer.getViewers()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teleportPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, headLookPacket);
        }
    }

    /**
     * İleri sar (saniye cinsinden)
     */
    public void forward(int seconds) {
        long currentElapsed = getElapsedTime();
        seekToTime(currentElapsed + seconds * 1000L);
    }

    /**
     * Geri sar (saniye cinsinden)
     */
    public void rewind(int seconds) {
        long currentElapsed = getElapsedTime();
        seekToTime(currentElapsed - seconds * 1000L);
    }

    /**
     * Start time'ı yeniden hesaplar
     */
    public void recalculateStartTime() {
        if (currentActionIndex < actions.size()) {
            long targetTime = actions.get(currentActionIndex).getTimestamp() - actions.get(0).getTimestamp();
            startTime = System.currentTimeMillis() - (long)(targetTime / replayPlayer.getPlaybackSpeed());
            pausedAtElapsed = -1;
        }
    }

    /**
     * Pause sırasında geçen süreyi kaydeder (resume'da kullanılır)
     */
    public void onPause() {
        pausedAtElapsed = (long) ((System.currentTimeMillis() - startTime) * replayPlayer.getPlaybackSpeed());
    }

    /**
     * Hız değişmeden ÖNCE çağrılır.
     * Mevcut elapsed time'ı eski hızla hesaplar ve startTime'ı yeni hıza göre ayarlar.
     * Böylece hız değişince süre sıfırlanmaz, kaldığı yerden devam eder.
     *
     * Örnek: 2x hızda 10. saniyedeysen, gerçek süre 5s geçmiş.
     * 1x'e çekince: startTime = now - (10000 / 1.0) = now - 10s → elapsed = 10s. Doğru!
     * Bu metod olmadan: elapsed = (5s) * 1.0 = 5s → NPC 10. saniyeye kadar donuyor.
     */
    public void onSpeedChange() {
        // Mevcut elapsed time'ı eski hızla hesapla
        long currentElapsed = getElapsedTime();

        if (replayPlayer.isPaused()) {
            // Pause'daysa sadece pausedAtElapsed'ı güncelle
            pausedAtElapsed = currentElapsed;
        } else {
            // Oynatılıyorsa startTime'ı yeniden hesapla
            // setPlaybackSpeed henüz çağrılmadı, ama bu metod ÖNCE çağrılıyor
            // Bu yüzden mevcut elapsed doğru. startTime'ı geçici olarak kaydet,
            // setPlaybackSpeed çağrıldıktan sonra yeni hızla startTime hesaplanacak.
            pausedAtElapsed = currentElapsed;
        }
    }

    /**
     * Hız değiştikten SONRA çağrılır (setPlaybackSpeed sonrası).
     * pausedAtElapsed'tan startTime'ı yeni hıza göre hesaplar.
     */
    public void afterSpeedChange() {
        if (!replayPlayer.isPaused() && pausedAtElapsed >= 0) {
            startTime = System.currentTimeMillis() - (long) (pausedAtElapsed / replayPlayer.getPlaybackSpeed());
            pausedAtElapsed = -1;
        }
    }

    /**
     * Resume sırasında startTime'ı yeniden hesaplar
     */
    public void onResume() {
        if (pausedAtElapsed >= 0) {
            startTime = System.currentTimeMillis() - (long) (pausedAtElapsed / replayPlayer.getPlaybackSpeed());
            pausedAtElapsed = -1;
        }
    }

    /**
     * Mevcut seek generation'ı döndürür.
     * Eski deferred operasyonlar bu değeri karşılaştırarak geçerliliğini kontrol eder.
     */
    public int getSeekGeneration() {
        return seekGeneration;
    }

    // Getter'lar
    public int getCurrentActionIndex() { return currentActionIndex; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
}
