package com.reportsystem.common.replay.actions;

import java.util.UUID;

public class InteractEntityAction extends ReplayAction {
    private static final long serialVersionUID = 1L;
    public enum InteractionType {
        RIGHT_CLICK,    // Sağ tık (ticaret, evcilleştirme vs)
        LEFT_CLICK,     // Sol tık (saldırı)
        FEED,           // Besleme
        TAME,           // Evcilleştirme
        BREED,          // Çiftleştirme
        SHEAR,          // Kırpma (koyun)
        MILK,           // Süt sağma
        TRADE,           // Ticaret (villager)
        LEASH,
        UNLEASH
    }

    private final InteractionType type;
    private final UUID targetEntityUuid;
    private final String targetEntityType;
    private final double targetX, targetY, targetZ;
    private final String itemUsed; // Material name of item used (for feeding, taming, breeding)

    public InteractEntityAction(InteractionType type, UUID targetUuid,
                                String entityType, double x, double y, double z) {
        this(type, targetUuid, entityType, x, y, z, null);
    }

    public InteractEntityAction(InteractionType type, UUID targetUuid,
                                String entityType, double x, double y, double z, String itemUsed) {
        super();
        this.type = type;
        this.targetEntityUuid = targetUuid;
        this.targetEntityType = entityType;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.itemUsed = itemUsed;
    }

    public InteractionType getType() {
        return type;
    }

    public UUID getTargetEntityUuid() {
        return targetEntityUuid;
    }

    public String getTargetEntityType() {
        return targetEntityType;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public String getItemUsed() {
        return itemUsed;
    }
}