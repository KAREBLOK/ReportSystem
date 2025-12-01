package com.reportsystem.common.replay.actions;

/**
 * Entity breeding - baby entity spawn
 */
public class BreedAction extends ReplayAction {

    private final String babyEntityType;
    private final double x, y, z;

    public BreedAction(String babyEntityType, double x, double y, double z) {
        super();
        this.babyEntityType = babyEntityType;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getBabyEntityType() { return babyEntityType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
