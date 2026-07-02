package com.reportsystem.common.replay.actions;

public class ProjectileAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum ProjectileType {
        ARROW,          // Ok
        SNOWBALL,       // Kar topu
        EGG,            // Yumurta
        ENDER_PEARL,    // Ender pearl
        EXPERIENCE_BOTTLE, // XP şişesi
        SPLASH_POTION,  // Sıçrayan iksir
        LINGERING_POTION, // Kalıcı iksir
        TRIDENT,        // Trident
        FISHING_HOOK,   // Olta iğnesi
        FIREWORK_ROCKET // Havai fişek
    }

    private final ProjectileType type;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;
    private final float yaw;
    private final float pitch;
    private final String potionData; // Serialized potion item for splash/lingering potions
    private final String arrowData; // Serialized arrow item for tipped arrows with effects

    public ProjectileAction(ProjectileType type, double velocityX, double velocityY,
                            double velocityZ, float yaw, float pitch) {
        this(type, velocityX, velocityY, velocityZ, yaw, pitch, null, null);
    }

    public ProjectileAction(ProjectileType type, double velocityX, double velocityY,
                            double velocityZ, float yaw, float pitch, String potionData) {
        this(type, velocityX, velocityY, velocityZ, yaw, pitch, potionData, null);
    }

    public ProjectileAction(ProjectileType type, double velocityX, double velocityY,
                            double velocityZ, float yaw, float pitch, String potionData, String arrowData) {
        super();
        this.type = type;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.potionData = potionData;
        this.arrowData = arrowData;
    }

    public ProjectileType getType() { return type; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getPotionData() { return potionData; }
    public String getArrowData() { return arrowData; }
}