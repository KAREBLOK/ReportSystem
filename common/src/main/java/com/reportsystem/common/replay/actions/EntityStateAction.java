package com.reportsystem.common.replay.actions;

import java.util.UUID;

public class EntityStateAction extends ReplayAction {

    public enum StateType {
        SNEAKING,    // Eğilme
        SPRINTING,   // Koşma
        SWIMMING,    // Yüzme
        GLIDING,     // Elytra ile süzülme
        BURNING,     // Yanma
        INVISIBLE,   // Görünmezlik
        GLOWING,     // Parlama efekti
        FLYING,      // Uçma (creative/spectator)
        BLOCKING,    // Kalkan ile bloklama
        CRAWLING,    // Sürünme (1.14+)
        FREEZING     // Donma (Powder Snow)
    }

    private final StateType stateType;
    private final boolean enabled;
    private final UUID entityUUID; // Nearby player için UUID (null = recorded player)

    // Eski constructor (geriye uyumluluk için - recorded player)
    public EntityStateAction(StateType stateType, boolean enabled) {
        this(stateType, enabled, null);
    }

    // Yeni constructor - nearby player desteği ile
    public EntityStateAction(StateType stateType, boolean enabled, UUID entityUUID) {
        super();
        this.stateType = stateType;
        this.enabled = enabled;
        this.entityUUID = entityUUID;
    }

    public StateType getStateType() { return stateType; }
    public boolean isEnabled() { return enabled; }
    public UUID getEntityUUID() { return entityUUID; }
    public boolean isForNearbyPlayer() { return entityUUID != null; }
}