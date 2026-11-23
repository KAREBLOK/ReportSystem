package com.reportsystem.spigot.recording;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.reportsystem.common.replay.actions.EquipmentAction;
import com.reportsystem.common.replay.actions.InventorySnapshotAction;
import com.reportsystem.common.replay.actions.NearbyPlayerAction;
import com.reportsystem.common.replay.actions.PlayerInfoAction;
import com.reportsystem.common.replay.actions.ReplayAction;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RecordingSession {
    private final String recordedPlayerName;
    // PERFORMANS: 45 saniyelik kayıtta ~1000-1500 action oluşur
    // Pre-allocate ile ArrayList resize overhead'ini önle
    private final List<ReplayAction> actions = Collections.synchronizedList(new ArrayList<>(1500));
    private PacketListenerAbstract packetListener;
    private String worldName;
    private long startTime;
    private long endTime;

    // Spawned entity tracking için
    private final Map<UUID, Entity> spawnedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastEntityLocations = new ConcurrentHashMap<>();

    // H-07: paket spam'i ile RAM şişirme / kanıt imhasına karşı sınırlar.
    // <= 0 → sınırsız. addAction hem Netty hem ana thread'den çağrılır → rateLock.
    private final int maxActions;
    private final int maxPerTypePerSecond;
    private final Object rateLock = new Object();
    private long rateWindowSecond = Long.MIN_VALUE;
    // Anahtar (sahip, sınıf): bir oyuncunun spam'i başka oyuncunun aynı tip aksiyonlarını bastıramaz
    private final Map<RateKey, Integer> rateCounts = new HashMap<>();
    private long droppedCount;
    private boolean dropWarned;

    /** Oran sınırı anahtarı. owner == null → kaydedilen oyuncu (oturum sahibi). */
    private record RateKey(UUID owner, Class<?> type) {
    }

    public RecordingSession(String recordedPlayerName, int maxActions, int maxPerTypePerSecond) {
        this.recordedPlayerName = recordedPlayerName;
        this.maxActions = maxActions;
        this.maxPerTypePerSecond = maxPerTypePerSecond;
    }

    /**
     * Yaşam döngüsü / kritik aksiyonlar tip-saniye sınırından muaftır (toplam tavan yine
     * uygulanır): bunlar düşerse replay'de oyuncu hiç görünmez/kaybolmaz ya da envanteri yanlış kalır.
     */
    private static boolean isRateExempt(ReplayAction action) {
        if (action instanceof NearbyPlayerAction nearby) {
            NearbyPlayerAction.ActionType type = nearby.getActionType();
            return type == NearbyPlayerAction.ActionType.PLAYER_APPEAR
                    || type == NearbyPlayerAction.ActionType.PLAYER_DISAPPEAR;
        }
        return action instanceof PlayerInfoAction
                || action instanceof InventorySnapshotAction
                || action instanceof EquipmentAction;
    }

    /**
     * Oran sınırı için aksiyonun sahibi: ownerUUID → NearbyPlayerAction oyuncusu →
     * null (kaydedilen oyuncu).
     */
    private static UUID rateOwner(ReplayAction action) {
        UUID owner = action.getOwnerUUID();
        if (owner == null && action instanceof NearbyPlayerAction nearby) {
            owner = nearby.getPlayerUuid();
        }
        return owner;
    }

    /**
     * Aksiyonu kayda ekler. Toplam tavan (maxActions) veya (sahip, tip) başına saniyelik
     * sınır (maxPerTypePerSecond) aşılırsa aksiyon düşürülür — kayıt ATILMAZ.
     *
     * @return aksiyon kayda eklendiyse true, sınır nedeniyle düşürüldüyse (veya null ise) false
     */
    public boolean addAction(ReplayAction action) {
        if (action == null) {
            return false;
        }
        String warnReason = null;
        synchronized (rateLock) {
            boolean drop = false;
            String reason = null;
            if (maxActions > 0 && actions.size() >= maxActions) {
                drop = true;
                reason = "toplam aksiyon tavanı (" + maxActions + ")";
            } else if (maxPerTypePerSecond > 0 && !isRateExempt(action)) {
                long nowSecond = System.currentTimeMillis() / 1000L;
                if (nowSecond != rateWindowSecond) {
                    rateWindowSecond = nowSecond;
                    rateCounts.clear();
                }
                int count = rateCounts.merge(new RateKey(rateOwner(action), action.getClass()), 1, Integer::sum);
                if (count > maxPerTypePerSecond) {
                    drop = true;
                    reason = "sahip+tip başına saniyelik sınır (" + action.getClass().getSimpleName() + " > "
                            + maxPerTypePerSecond + "/sn)";
                }
            }
            if (!drop) {
                actions.add(action);
                return true;
            }
            droppedCount++;
            if (!dropWarned) {
                dropWarned = true;
                warnReason = reason;
            }
        }
        if (warnReason != null) {
            com.reportsystem.spigot.ReportSystemSpigot instance = com.reportsystem.spigot.ReportSystemSpigot.getInstance();
            if (instance != null) {
                instance.getLogger().warning("[RECORDING] " + recordedPlayerName + " kaydında aksiyonlar düşürülüyor: "
                        + warnReason + ". Kayıt devam ediyor; bu uyarı oturum başına bir kez yazılır.");
            }
        }
        return false;
    }

    /**
     * Toplam aksiyon tavanı doldu mu? (Pahalı aksiyon üretmeden önce ucuz ön kontrol.)
     */
    public boolean isActionCapReached() {
        return maxActions > 0 && actions.size() >= maxActions;
    }

    /**
     * Sınırlar yüzünden düşürülen aksiyon sayısı.
     */
    public long getDroppedCount() {
        synchronized (rateLock) {
            return droppedCount;
        }
    }

    public List<ReplayAction> getActions() {
        return actions;
    }

    public String getRecordedPlayerName() {
        return recordedPlayerName;
    }

    public PacketListenerAbstract getPacketListener() {
        return packetListener;
    }

    public void setPacketListener(PacketListenerAbstract packetListener) {
        this.packetListener = packetListener;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    // Spawned entity tracking metodları
    public void trackSpawnedEntity(Entity entity) {
        spawnedEntities.put(entity.getUniqueId(), entity);
        lastEntityLocations.put(entity.getUniqueId(), entity.getLocation().clone());
    }

    public Map<UUID, Entity> getSpawnedEntities() {
        return spawnedEntities;
    }

    public Map<UUID, Location> getLastEntityLocations() {
        return lastEntityLocations;
    }

    public void updateEntityLocation(UUID uuid, Location location) {
        lastEntityLocations.put(uuid, location.clone());
    }

    public void removeSpawnedEntity(UUID uuid) {
        spawnedEntities.remove(uuid);
        lastEntityLocations.remove(uuid);
    }
}