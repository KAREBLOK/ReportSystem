package com.reportsystem.common.replay.actions;

import java.util.UUID;

/**
 * Entity boyama (koyun, kedi tasması, vb.)
 */
public class EntityDyeAction extends ReplayAction {

    private final UUID entityUuid;
    private final String entityType;
    private final String dyeColor;
    private final double x, y, z;

    public EntityDyeAction(UUID entityUuid, String entityType, String dyeColor,
                          double x, double y, double z) {
        super();
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.dyeColor = dyeColor;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public String getDyeColor() { return dyeColor; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
