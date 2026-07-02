package com.reportsystem.spigot.webreplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Replay'deki entity'ler icin UUID -> entityId eslestirmesi yapar.
 * Ana oyuncu entityId=1, civar oyuncular 100'den baslar.
 */
public class WebReplayEntityManager {

    private final int mainPlayerEntityId = 1;
    private final Map<UUID, Integer> nearbyPlayerIds = new HashMap<>();
    private int nextEntityId = 100;

    public int getMainPlayerEntityId() {
        return mainPlayerEntityId;
    }

    /**
     * Civar oyuncu icin entityId dondurur. Yoksa yeni atar.
     */
    public int getOrAssignEntityId(UUID playerUuid) {
        return nearbyPlayerIds.computeIfAbsent(playerUuid, k -> nextEntityId++);
    }

    /**
     * Civar oyuncu icin entityId dondurur. Yoksa -1.
     */
    public int getEntityId(UUID playerUuid) {
        return nearbyPlayerIds.getOrDefault(playerUuid, -1);
    }

    /**
     * Civar oyuncuyu kaldir.
     */
    public void removeEntity(UUID playerUuid) {
        nearbyPlayerIds.remove(playerUuid);
    }

    /**
     * Bir action'in hangi entityId'ye ait oldugunu dondurur.
     */
    public int resolveEntityId(UUID ownerUuid) {
        if (ownerUuid == null) {
            return mainPlayerEntityId;
        }
        return getEntityId(ownerUuid);
    }
}
