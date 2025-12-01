package com.reportsystem.common.replay.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spawn egg ile entity spawn edildiğinde özellikleri kaydetmek için
 */
public class EntitySpawnAction extends ReplayAction {

    private final UUID entityUuid;
    private final String entityType;
    private final double x, y, z;
    private final float yaw, pitch;

    // Entity özellikleri (flexible - her entity için farklı)
    private final Map<String, String> properties;

    public EntitySpawnAction(UUID entityUuid, String entityType,
                           double x, double y, double z,
                           float yaw, float pitch,
                           Map<String, String> properties) {
        super();
        this.entityUuid = entityUuid;
        this.entityType = entityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.properties = properties != null ? properties : new HashMap<>();
    }

    public UUID getEntityUuid() { return entityUuid; }
    public String getEntityType() { return entityType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public Map<String, String> getProperties() { return properties; }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
}
