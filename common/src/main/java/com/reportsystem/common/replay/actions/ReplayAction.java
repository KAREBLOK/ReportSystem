package com.reportsystem.common.replay.actions;

import java.io.Serializable;
import java.util.UUID;

/**
 * Tekrar oynatma sırasında gerçekleşen tek bir aksiyonu temsil eden temel sınıf.
 * Serializable arayüzü, bu nesnelerin kolayca saklanabilir hale gelmesini sağlar.
 */
public abstract class ReplayAction implements Serializable {
    private static final long serialVersionUID = 2L; // Değişti - yeni field eklendi

    // Her aksiyonun ne zaman gerçekleştiğini bilmek için zaman damgası
    private final long timestamp;

    // Action'ın sahibi: null = ana oyuncu (recorded player), UUID = yakındaki oyuncu
    private UUID ownerUUID;

    public ReplayAction() {
        this.timestamp = System.currentTimeMillis();
        this.ownerUUID = null; // Varsayılan: ana oyuncu
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    /**
     * Bu action ana oyuncuya mı ait?
     */
    public boolean isMainPlayer() {
        return ownerUUID == null;
    }

    /**
     * Bu action belirli bir nearby player'a mı ait?
     */
    public boolean isOwnedBy(UUID uuid) {
        return ownerUUID != null && ownerUUID.equals(uuid);
    }
}