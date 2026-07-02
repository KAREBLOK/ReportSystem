package com.reportsystem.common.replay.actions;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.UUID;

/**
 * Tekrar oynatma sırasında gerçekleşen tek bir aksiyonu temsil eden temel sınıf.
 * Serializable arayüzü, bu nesnelerin kolayca saklanabilir hale gelmesini sağlar.
 */
public abstract class ReplayAction implements Serializable {
    // serialVersionUID = 1L olarak kalmalı! Eski replay verilerini kırmamak için asla değiştirme.
    // ownerUUID field'ı sonradan eklendi, eski verilerde yoktur - readObject() ile handle ediliyor.
    private static final long serialVersionUID = 1L;

    // Her aksiyonun ne zaman gerçekleştiğini bilmek için zaman damgası
    private final long timestamp;

    // Action'ın sahibi: null = ana oyuncu (recorded player), UUID = yakındaki oyuncu
    private UUID ownerUUID;

    public ReplayAction() {
        this.timestamp = System.currentTimeMillis();
        this.ownerUUID = null; // Varsayılan: ana oyuncu
    }

    /**
     * Eski replay verileri ownerUUID field'ı olmadan serialize edilmiş olabilir.
     * Bu durumda ownerUUID null olarak kalır (ana oyuncu varsayılır).
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Eski verilerde ownerUUID field'ı yoksa, defaultReadObject() null bırakır - sorun yok.
        // Bu zaten istediğimiz davranış: ownerUUID == null -> ana oyuncu
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