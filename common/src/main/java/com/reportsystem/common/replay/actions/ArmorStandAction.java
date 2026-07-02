package com.reportsystem.common.replay.actions;

import java.util.Map;

/**
 * Armor Stand yerleştirme ve manipülasyon
 */
public class ArmorStandAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum ActionType {
        PLACE,
        BREAK,
        MANIPULATE // Pose değiştirme, ekipman ekleme
    }

    private final ActionType actionType;
    private final double x, y, z;
    private final float yaw, pitch;
    private final Map<String, String> properties; // arms, basePlate, visible, small, vb.

    public ArmorStandAction(ActionType actionType, double x, double y, double z,
                           float yaw, float pitch, Map<String, String> properties) {
        super();
        this.actionType = actionType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.properties = properties;
    }

    public ActionType getActionType() { return actionType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public Map<String, String> getProperties() { return properties; }
}
