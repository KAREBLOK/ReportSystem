package com.reportsystem.common.replay.actions;

import java.util.UUID;

/**
 * Spawned entity'lerin pozisyon güncellemelerini kaydetmek için kullanılır
 */
public class EntityUpdateAction extends ReplayAction {

    private final UUID entityUuid;
    private final String entityType;
    private final double x, y, z;
    private final float yaw, pitch;

    public EntityUpdateAction(UUID entityUuid, String entityType,
                            double x, double y, double z,
                            float yaw, float pitch) {
        super();
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public UUID getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
