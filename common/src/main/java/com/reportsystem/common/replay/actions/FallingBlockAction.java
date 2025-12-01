package com.reportsystem.common.replay.actions;

/**
 * Falling block (kum, çakıl, anvil düşmesi)
 */
public class FallingBlockAction extends ReplayAction {

    private final String blockType;
    private final double x, y, z;

    public FallingBlockAction(String blockType, double x, double y, double z) {
        super();
        this.blockType = blockType;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getBlockType() { return blockType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
