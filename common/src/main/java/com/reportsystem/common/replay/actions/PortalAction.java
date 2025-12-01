package com.reportsystem.common.replay.actions;

/**
 * Portal kullanımı (nether, end)
 */
public class PortalAction extends ReplayAction {

    public enum PortalType {
        NETHER,         // Nether portal
        END,            // End portal
        END_GATEWAY     // End gateway
    }

    private final PortalType portalType;
    private final double fromX, fromY, fromZ;
    private final String fromWorld;
    private final double toX, toY, toZ;
    private final String toWorld;

    public PortalAction(PortalType portalType,
                       double fromX, double fromY, double fromZ, String fromWorld,
                       double toX, double toY, double toZ, String toWorld) {
        super();
        this.portalType = portalType;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.fromWorld = fromWorld;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.toWorld = toWorld;
    }

    public PortalType getPortalType() { return portalType; }
    public double getFromX() { return fromX; }
    public double getFromY() { return fromY; }
    public double getFromZ() { return fromZ; }
    public String getFromWorld() { return fromWorld; }
    public double getToX() { return toX; }
    public double getToY() { return toY; }
    public double getToZ() { return toZ; }
    public String getToWorld() { return toWorld; }
}
