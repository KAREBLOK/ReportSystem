package com.reportsystem.common.models.overwatch;

public class OverwatchNPC {
    private final String id;
    private final String serverName;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long createdAt;
    private final String createdBy;
    private final String displayName;
    private final String skinTexture;
    private final String skinSignature;

    public OverwatchNPC(String id, String serverName, String world, double x, double y, double z,
                        float yaw, float pitch, long createdAt, String createdBy, String displayName) {
        this(id, serverName, world, x, y, z, yaw, pitch, createdAt, createdBy, displayName, null, null);
    }

    public OverwatchNPC(String id, String serverName, String world, double x, double y, double z,
                        float yaw, float pitch, long createdAt, String createdBy, String displayName,
                        String skinTexture, String skinSignature) {
        this.id = id;
        this.serverName = serverName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.displayName = displayName;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
    }

    public String getId() { return id; }
    public String getServerName() { return serverName; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public long getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public String getDisplayName() { return displayName; }
    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
}
