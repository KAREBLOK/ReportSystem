package com.reportsystem.common.replay.actions;

public class LocationAction extends ReplayAction {
    private static final long serialVersionUID = 1L;
    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean onGround;

    public LocationAction(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    // Getter metodları
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isOnGround() {
        return onGround;
    }
}