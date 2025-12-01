package com.reportsystem.common.replay.actions;

public class VelocityAction extends ReplayAction {

    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;

    public VelocityAction(double velocityX, double velocityY, double velocityZ) {
        super();
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }

    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
}