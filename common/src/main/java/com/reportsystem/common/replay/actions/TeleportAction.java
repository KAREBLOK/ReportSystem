package com.reportsystem.common.replay.actions;

public class TeleportAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum TeleportCause {
        ENDER_PEARL,
        CHORUS_FRUIT,
        COMMAND,
        PLUGIN,
        UNKNOWN
    }

    private final double fromX, fromY, fromZ;
    private final double toX, toY, toZ;
    private final String fromWorld;
    private final String toWorld;
    private final TeleportCause cause;

    // Backward compatibility constructor (without world info)
    @Deprecated
    public TeleportAction(double fromX, double fromY, double fromZ,
                          double toX, double toY, double toZ, TeleportCause cause) {
        this(fromX, fromY, fromZ, null, toX, toY, toZ, null, cause);
    }

    // New constructor with world info
    public TeleportAction(double fromX, double fromY, double fromZ, String fromWorld,
                          double toX, double toY, double toZ, String toWorld,
                          TeleportCause cause) {
        super();
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.fromWorld = fromWorld;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.toWorld = toWorld;
        this.cause = cause;
    }

    public double getFromX() {
        return fromX;
    }

    public double getFromY() {
        return fromY;
    }

    public double getFromZ() {
        return fromZ;
    }

    public double getToX() {
        return toX;
    }

    public double getToY() {
        return toY;
    }

    public double getToZ() {
        return toZ;
    }

    public TeleportCause getCause() {
        return cause;
    }

    public String getFromWorld() {
        return fromWorld;
    }

    public String getToWorld() {
        return toWorld;
    }
}